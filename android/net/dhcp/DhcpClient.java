/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.net.dhcp;

import com.android.internal.util.HexDump;
import com.android.internal.util.Protocol;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.DhcpResults;
import android.net.BaseDhcpStateMachine;
import android.net.DhcpStateMachine;
import android.net.InterfaceConfiguration;
import android.net.LinkAddress;
import android.net.NetworkUtils;
import android.os.IBinder;
import android.os.INetworkManagementService;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.system.PacketSocketAddress;
import android.util.Log;
import android.util.TimeUtils;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.Thread;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

import libcore.io.IoUtils;

import static android.system.OsConstants.*;
import static android.net.dhcp.DhcpPacket.*;

/**
 * A DHCPv4 client.
 *
 * Written to behave similarly to the DhcpStateMachine + dhcpcd 5.5.6 combination used in Android
 * 5.1 and below, as configured on Nexus 6. The interface is the same as DhcpStateMachine.
 *
 * TODO:
 *
 * - Exponential backoff when receiving NAKs (not specified by the RFC, but current behaviour).
 * - Support persisting lease state and support INIT-REBOOT. Android 5.1 does this, but it does not
 *   do so correctly: instead of requesting the lease last obtained on a particular network (e.g., a
 *   given SSID), it requests the last-leased IP address on the same interface, causing a delay if
 *   the server NAKs or a timeout if it doesn't.
 *
 * Known differences from current behaviour:
 *
 * - Does not request the "static routes" option.
 * - Does not support BOOTP servers. DHCP has been around since 1993, should be everywhere now.
 * - Requests the "broadcast" option, but does nothing with it.
 * - Rejects invalid subnet masks such as 255.255.255.1 (current code treats that as 255.255.255.0).
 *
 * @hide
 */
public class DhcpClient extends BaseDhcpStateMachine {

    private static final String TAG = "DhcpClient";
    private static final boolean DBG = true;
    private static final boolean STATE_DBG = false;
    private static final boolean MSG_DBG = false;
    private static final boolean PACKET_DBG = true;

    // Timers and timeouts.
    private static final int SECONDS = 1000;
    private static final int FIRST_TIMEOUT_MS   =   2 * SECONDS;
    private static final int MAX_TIMEOUT_MS     = 128 * SECONDS;

    // This is not strictly needed, since the client is asynchronous and implements exponential
    // backoff. It's maintained for backwards compatibility with the previous DHCP code, which was
    // a blocking operation with a 30-second timeout. We pick 36 seconds so we can send packets at
    // t=0, t=2, t=6, t=14, t=30, allowing for 10% jitter.
    private static final int DHCP_TIMEOUT_MS    =  36 * SECONDS;

    // Messages.
    private static final int BASE                 = Protocol.BASE_DHCP + 100;
    private static final int CMD_KICK             = BASE + 1;
    private static final int CMD_RECEIVED_PACKET  = BASE + 2;
    private static final int CMD_TIMEOUT          = BASE + 3;

    // DHCP parameters that we request.
    private static final byte[] REQUESTED_PARAMS = new byte[] {
        DHCP_SUBNET_MASK,
        DHCP_ROUTER,
        DHCP_DNS_SERVER,
        DHCP_DOMAIN_NAME,
        DHCP_MTU,
        DHCP_BROADCAST_ADDRESS,  // TODO: currently ignored.
        DHCP_LEASE_TIME,
        DHCP_RENEWAL_TIME,
        DHCP_REBINDING_TIME,
    };

    // DHCP flag that means "yes, we support unicast."
    private static final boolean DO_UNICAST   = false;

    // System services / libraries we use.
    private final Context mContext;
    private final AlarmManager mAlarmManager;
    private final Random mRandom;
    private final INetworkManagementService mNMService;

    // Sockets.
    // - We use a packet socket to receive, because servers send us packets bound for IP addresses
    //   which we have not yet configured, and the kernel protocol stack drops these.
    // - We use a UDP socket to send, so the kernel handles ARP and routing for us (DHCP servers can
    //   be off-link as well as on-link).
    private FileDescriptor mPacketSock;
    private FileDescriptor mUdpSock;
    private ReceiveThread mReceiveThread;

    // State variables.
    private final StateMachine mController;
    private final PendingIntent mKickIntent;
    private final PendingIntent mTimeoutIntent;
    private final PendingIntent mRenewIntent;
    private final String mIfaceName;

    private boolean mRegisteredForPreDhcpNotification;
    private NetworkInterface mIface;
    private byte[] mHwAddr;
    private PacketSocketAddress mInterfaceBroadcastAddr;
    private int mTransactionId;
    private long mTransactionStartMillis;
    private DhcpResults mDhcpLease;
    private long mDhcpLeaseExpiry;
    private DhcpResults mOffer;

    // States.
    private State mStoppedState = new StoppedState();
    private State mDhcpState = new DhcpState();
    private State mDhcpInitState = new DhcpInitState();
    private State mDhcpSelectingState = new DhcpSelectingState();
    private State mDhcpRequestingState = new DhcpRequestingState();
    private State mDhcpBoundState = new DhcpBoundState();
    private State mDhcpRenewingState = new DhcpRenewingState();
    private State mDhcpRebindingState = new DhcpRebindingState();
    private State mDhcpInitRebootState = new DhcpInitRebootState();
    private State mDhcpRebootingState = new DhcpRebootingState();
    private State mWaitBeforeStartState = new WaitBeforeStartState(mDhcpInitState);
    private State mWaitBeforeRenewalState = new WaitBeforeRenewalState(mDhcpRenewingState);

    private DhcpClient(Context context, StateMachine controller, String iface) {
        super(TAG);

        mContext = context;
        mController = controller;
        mIfaceName = iface;

        addState(mStoppedState);
        addState(mDhcpState);
            addState(mDhcpInitState, mDhcpState);
            addState(mWaitBeforeStartState, mDhcpState);
            addState(mDhcpSelectingState, mDhcpState);
            addState(mDhcpRequestingState, mDhcpState);
            addState(mDhcpBoundState, mDhcpState);
            addState(mWaitBeforeRenewalState, mDhcpState);
            addState(mDhcpRenewingState, mDhcpState);
            addState(mDhcpRebindingState, mDhcpState);
            addState(mDhcpInitRebootState, mDhcpState);
            addState(mDhcpRebootingState, mDhcpState);

        setInitialState(mStoppedState);

        mAlarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
        mNMService = INetworkManagementService.Stub.asInterface(b);

        mRandom = new Random();

        mKickIntent = createStateMachineCommandIntent("KICK", CMD_KICK);
        mTimeoutIntent = createStateMachineCommandIntent("TIMEOUT", CMD_TIMEOUT);
        mRenewIntent = createStateMachineCommandIntent("RENEW", DhcpStateMachine.CMD_RENEW_DHCP);
    }

    @Override
    public void registerForPreDhcpNotification() {
        mRegisteredForPreDhcpNotification = true;
    }

    public static BaseDhcpStateMachine makeDhcpStateMachine(
            Context context, StateMachine controller, String intf) {
        DhcpClient client = new DhcpClient(context, controller, intf);
        client.start();
        return client;
    }

    /**
     * Constructs a PendingIntent that sends the specified command to the state machine. This is
     * implemented by creating an Intent with the specified parameters, and creating and registering
     * a BroadcastReceiver for it. The broadcast must be sent by a process that holds the
     * {@code CONNECTIVITY_INTERNAL} permission.
     *
     * @param cmdName the name of the command. The intent's action will be
     *         {@code android.net.dhcp.DhcpClient.<cmdName>}
     * @param cmd the command to send to the state machine when the PendingIntent is triggered.
     * @return the PendingIntent
     */
    private PendingIntent createStateMachineCommandIntent(final String cmdName, final int cmd) {
        String action = DhcpClient.class.getName() + "." + cmdName;

        // TODO: figure out what values to pass to intent.setPackage() and intent.setClass() that
        // result in the Intent being received by this class and nowhere else, and use them.
        Intent intent = new Intent(action, null)
                .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        PendingIntent pendingIntent =  PendingIntent.getBroadcast(mContext, cmd, intent, 0);

        mContext.registerReceiver(
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    sendMessage(cmd);
                }
            },
            new IntentFilter(action),
            android.Manifest.permission.CONNECTIVITY_INTERNAL,
            null);

        return pendingIntent;
    }

    private boolean initInterface() {
        try {
            mIface = NetworkInterface.getByName(mIfaceName);
            mHwAddr = mIface.getHardwareAddress();
            mInterfaceBroadcastAddr = new PacketSocketAddress(mIface.getIndex(),
                    DhcpPacket.ETHER_BROADCAST);
            return true;
        } catch(SocketException e) {
            Log.wtf(TAG, "Can't determine ifindex or MAC address for " + mIfaceName);
            return false;
        }
    }

    private void startNewTransaction() {
        mTransactionId = mRandom.nextInt();
        mTransactionStartMillis = SystemClock.elapsedRealtime();
    }

    private boolean initSockets() {
        try {
            mPacketSock = Os.socket(AF_PACKET, SOCK_RAW, ETH_P_IP);
            PacketSocketAddress addr = new PacketSocketAddress((short) ETH_P_IP, mIface.getIndex());
            Os.bind(mPacketSock, addr);
            NetworkUtils.attachDhcpFilter(mPacketSock);
        } catch(SocketException|ErrnoException e) {
            Log.e(TAG, "Error creating packet socket", e);
            return false;
        }
        try {
            mUdpSock = Os.socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
            Os.setsockoptInt(mUdpSock, SOL_SOCKET, SO_REUSEADDR, 1);
            Os.setsockoptIfreq(mUdpSock, SOL_SOCKET, SO_BINDTODEVICE, mIfaceName);
            Os.setsockoptInt(mUdpSock, SOL_SOCKET, SO_BROADCAST, 1);
            Os.bind(mUdpSock, Inet4Address.ANY, DhcpPacket.DHCP_CLIENT);
            NetworkUtils.protectFromVpn(mUdpSock);
        } catch(SocketException|ErrnoException e) {
            Log.e(TAG, "Error creating UDP socket", e);
            return false;
        }
        return true;
    }

    private void closeSockets() {
        IoUtils.closeQuietly(mUdpSock);
        IoUtils.closeQuietly(mPacketSock);
    }

    private boolean setIpAddress(LinkAddress address) {
        InterfaceConfiguration ifcg = new InterfaceConfiguration();
        ifcg.setLinkAddress(address);
        try {
            mNMService.setInterfaceConfig(mIfaceName, ifcg);
        } catch (RemoteException|IllegalStateException e) {
            Log.e(TAG, "Error configuring IP address : " + e);
            return false;
        }
        return true;
    }

    class ReceiveThread extends Thread {

        private final byte[] mPacket = new byte[DhcpPacket.MAX_LENGTH];
        private boolean stopped = false;

        public void halt() {
            stopped = true;
            closeSockets();  // Interrupts the read() call the thread is blocked in.
        }

        @Override
        public void run() {
            maybeLog("Starting receive thread");
            while (!stopped) {
                try {
                    int length = Os.read(mPacketSock, mPacket, 0, mPacket.length);
                    DhcpPacket packet = null;
                    packet = DhcpPacket.decodeFullPacket(mPacket, length, DhcpPacket.ENCAP_L2);
                    if (packet != null) {
                        maybeLog("Received packet: " + packet);
                        sendMessage(CMD_RECEIVED_PACKET, packet);
                    } else if (PACKET_DBG) {
                        Log.d(TAG,
                                "Can't parse packet" + HexDump.dumpHexString(mPacket, 0, length));
                    }
                } catch (IOException|ErrnoException e) {
                    if (!stopped) {
                        Log.e(TAG, "Read error", e);
                    }
                }
            }
            maybeLog("Stopping receive thread");
        }
    }

    private short getSecs() {
        return (short) ((SystemClock.elapsedRealtime() - mTransactionStartMillis) / 1000);
    }

    private boolean transmitPacket(ByteBuffer buf, String description, Inet4Address to) {
        try {
            if (to.equals(INADDR_BROADCAST)) {
                maybeLog("Broadcasting " + description);
                Os.sendto(mPacketSock, buf.array(), 0, buf.limit(), 0, mInterfaceBroadcastAddr);
            } else {
                maybeLog("Unicasting " + description + " to " + to.getHostAddress());
                Os.sendto(mUdpSock, buf, 0, to, DhcpPacket.DHCP_SERVER);
            }
        } catch(ErrnoException|IOException e) {
            Log.e(TAG, "Can't send packet: ", e);
            return false;
        }
        return true;
    }

    private boolean sendDiscoverPacket() {
        ByteBuffer packet = DhcpPacket.buildDiscoverPacket(
                DhcpPacket.ENCAP_L2, mTransactionId, getSecs(), mHwAddr,
                DO_UNICAST, REQUESTED_PARAMS);
        return transmitPacket(packet, "DHCPDISCOVER", INADDR_BROADCAST);
    }

    private boolean sendRequestPacket(
            Inet4Address clientAddress, Inet4Address requestedAddress,
            Inet4Address serverAddress, Inet4Address to) {
        // TODO: should we use the transaction ID from the server?
        int encap = to.equals(INADDR_BROADCAST) ? DhcpPacket.ENCAP_L2 : DhcpPacket.ENCAP_BOOTP;

        ByteBuffer packet = DhcpPacket.buildRequestPacket(
                encap, mTransactionId, getSecs(), clientAddress,
                DO_UNICAST, mHwAddr, requestedAddress,
                serverAddress, REQUESTED_PARAMS, null);
        String description = "DHCPREQUEST ciaddr=" + clientAddress.getHostAddress() +
                             " request=" + requestedAddress.getHostAddress() +
                             " to=" + serverAddress.getHostAddress();
        return transmitPacket(packet, description, to);
    }

    private void scheduleRenew() {
        long now = SystemClock.elapsedRealtime();
        long alarmTime = (now + mDhcpLeaseExpiry) / 2;
        mAlarmManager.cancel(mRenewIntent);
        mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, alarmTime, mRenewIntent);
        Log.d(TAG, "Scheduling renewal in " + ((alarmTime - now) / 1000) + "s");
    }

    private void notifyLease() {
        mController.sendMessage(DhcpStateMachine.CMD_POST_DHCP_ACTION,
                DhcpStateMachine.DHCP_SUCCESS, 0, mDhcpLease);
    }

    private void notifyFailure() {
        mController.sendMessage(DhcpStateMachine.CMD_POST_DHCP_ACTION,
                DhcpStateMachine.DHCP_FAILURE, 0, null);
    }

    private void clearDhcpState() {
        mDhcpLease = null;
        mDhcpLeaseExpiry = 0;
        mOffer = null;
    }

    /**
     * Quit the DhcpStateMachine.
     *
     * @hide
     */
    @Override
    public void doQuit() {
        Log.d(TAG, "doQuit");
        quit();
    }

    protected void onQuitting() {
        Log.d(TAG, "onQuitting");
        mController.sendMessage(DhcpStateMachine.CMD_ON_QUIT);
    }

    private void maybeLog(String msg) {
        if (DBG) Log.d(TAG, msg);
    }

    abstract class LoggingState extends State {
        public void enter() {
            if (STATE_DBG) Log.d(TAG, "Entering state " + getName());
        }

        private String messageName(int what) {
            switch (what) {
                case DhcpStateMachine.CMD_START_DHCP:
                    return "CMD_START_DHCP";
                case DhcpStateMachine.CMD_STOP_DHCP:
                    return "CMD_STOP_DHCP";
                case DhcpStateMachine.CMD_RENEW_DHCP:
                    return "CMD_RENEW_DHCP";
                case DhcpStateMachine.CMD_PRE_DHCP_ACTION:
                    return "CMD_PRE_DHCP_ACTION";
                case DhcpStateMachine.CMD_PRE_DHCP_ACTION_COMPLETE:
                    return "CMD_PRE_DHCP_ACTION_COMPLETE";
                case DhcpStateMachine.CMD_POST_DHCP_ACTION:
                    return "CMD_POST_DHCP_ACTION";
                case CMD_KICK:
                    return "CMD_KICK";
                case CMD_RECEIVED_PACKET:
                    return "CMD_RECEIVED_PACKET";
                default:
                    return Integer.toString(what);
            }
        }

        private String messageToString(Message message) {
            long now = SystemClock.uptimeMillis();
            StringBuilder b = new StringBuilder(" ");
            TimeUtils.formatDuration(message.getWhen() - now, b);
            b.append(" ").append(messageName(message.what))
                    .append(" ").append(message.arg1)
                    .append(" ").append(message.arg2)
                    .append(" ").append(message.obj);
            return b.toString();
        }

        @Override
        public boolean processMessage(Message message) {
            if (MSG_DBG) {
                Log.d(TAG, getName() + messageToString(message));
            }
            return NOT_HANDLED;
        }
    }

    // Sends CMD_PRE_DHCP_ACTION to the controller, waits for the controller to respond with
    // CMD_PRE_DHCP_ACTION_COMPLETE, and then transitions to mOtherState.
    abstract class WaitBeforeOtherState extends LoggingState {
        protected State mOtherState;

        @Override
        public void enter() {
            super.enter();
            mController.sendMessage(DhcpStateMachine.CMD_PRE_DHCP_ACTION);
        }

        @Override
        public boolean processMessage(Message message) {
            super.processMessage(message);
            switch (message.what) {
                case DhcpStateMachine.CMD_PRE_DHCP_ACTION_COMPLETE:
                    transitionTo(mOtherState);
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class StoppedState extends LoggingState {
        @Override
        public boolean processMessage(Message message) {
            super.processMessage(message);
            switch (message.what) {
                case DhcpStateMachine.CMD_START_DHCP:
                    if (mRegisteredForPreDhcpNotification) {
                        transitionTo(mWaitBeforeStartState);
                    } else {
                        transitionTo(mDhcpInitState);
                    }
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class WaitBeforeStartState extends WaitBeforeOtherState {
        public WaitBeforeStartState(State otherState) {
            super();
            mOtherState = otherState;
        }
    }

    class WaitBeforeRenewalState extends WaitBeforeOtherState {
        public WaitBeforeRenewalState(State otherState) {
            super();
            mOtherState = otherState;
        }
    }

    class DhcpState extends LoggingState {
        @Override
        public void enter() {
            super.enter();
            clearDhcpState();
            if (initInterface() && initSockets()) {
                mReceiveThread = new ReceiveThread();
                mReceiveThread.start();
            } else {
                notifyFailure();
                transitionTo(mStoppedState);
            }
        }

        @Override
        public void exit() {
            mReceiveThread.halt();  // Also closes sockets.
            clearDhcpState();
        }

        @Override
        public boolean processMessage(Message message) {
            super.processMessage(message);
            switch (message.what) {
                case DhcpStateMachine.CMD_STOP_DHCP:
                    transitionTo(mStoppedState);
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    public boolean isValidPacket(DhcpPacket packet) {
        // TODO: check checksum.
        int xid = packet.getTransactionId();
        if (xid != mTransactionId) {
            Log.d(TAG, "Unexpected transaction ID " + xid + ", expected " + mTransactionId);
            return false;
        }
        if (!Arrays.equals(packet.getClientMac(), mHwAddr)) {
            Log.d(TAG, "MAC addr mismatch: got " +
                    HexDump.toHexString(packet.getClientMac()) + ", expected " +
                    HexDump.toHexString(packet.getClientMac()));
            return false;
        }
        return true;
    }

    /**
     * Retransmits packets using jittered exponential backoff with an optional timeout. Packet
     * transmission is triggered by CMD_KICK, which is sent by an AlarmManager alarm.
     *
     * Concrete subclasses must implement sendPacket, which is called when the alarm fires and a
     * packet needs to be transmitted, and receivePacket, which is triggered by CMD_RECEIVED_PACKET
     * sent by the receive thread. They may implement timeout, which is called when the timeout
     * fires.
     */
    abstract class PacketRetransmittingState extends LoggingState {

        private int mTimer;
        protected int mTimeout = 0;

        @Override
        public void enter() {
            super.enter();
            initTimer();
            maybeInitTimeout();
            sendMessage(CMD_KICK);
        }

        @Override
        public boolean processMessage(Message message) {
            super.processMessage(message);
            switch (message.what) {
                case CMD_KICK:
                    sendPacket();
                    scheduleKick();
                    return HANDLED;
                case CMD_RECEIVED_PACKET:
                    receivePacket((DhcpPacket) message.obj);
                    return HANDLED;
                case CMD_TIMEOUT:
                    timeout();
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }

        public void exit() {
            mAlarmManager.cancel(mKickIntent);
            mAlarmManager.cancel(mTimeoutIntent);
        }

        abstract protected boolean sendPacket();
        abstract protected void receivePacket(DhcpPacket packet);
        protected void timeout() {}

        protected void initTimer() {
            mTimer = FIRST_TIMEOUT_MS;
        }

        protected int jitterTimer(int baseTimer) {
            int maxJitter = baseTimer / 10;
            int jitter = mRandom.nextInt(2 * maxJitter) - maxJitter;
            return baseTimer + jitter;
        }

        protected void scheduleKick() {
            long now = SystemClock.elapsedRealtime();
            long timeout = jitterTimer(mTimer);
            long alarmTime = now + timeout;
            mAlarmManager.cancel(mKickIntent);
            mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, alarmTime, mKickIntent);
            mTimer *= 2;
            if (mTimer > MAX_TIMEOUT_MS) {
                mTimer = MAX_TIMEOUT_MS;
            }
        }

        protected void maybeInitTimeout() {
            if (mTimeout > 0) {
                long alarmTime = SystemClock.elapsedRealtime() + mTimeout;
                mAlarmManager.setExact(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP, alarmTime, mTimeoutIntent);
            }
        }
    }

    class DhcpInitState extends PacketRetransmittingState {
        public DhcpInitState() {
            super();
            mTimeout = DHCP_TIMEOUT_MS;
        }

        @Override
        public void enter() {
            super.enter();
            startNewTransaction();
        }

        protected boolean sendPacket() {
            return sendDiscoverPacket();
        }

        protected void timeout() {
            maybeLog("Timeout");
            notifyFailure();
        }

        protected void receivePacket(DhcpPacket packet) {
            if (!isValidPacket(packet)) return;
            if (!(packet instanceof DhcpOfferPacket)) return;
            mOffer = packet.toDhcpResults();
            if (mOffer != null) {
                Log.d(TAG, "Got pending lease: " + mOffer);
                transitionTo(mDhcpRequestingState);
            }
        }
    }

    // Not implemented. We request the first offer we receive.
    class DhcpSelectingState extends LoggingState {
    }

    class DhcpRequestingState extends PacketRetransmittingState {
        public DhcpRequestingState() {
            super();
            mTimeout = DHCP_TIMEOUT_MS / 2;
        }

        protected boolean sendPacket() {
            return sendRequestPacket(
                    INADDR_ANY,                                    // ciaddr
                    (Inet4Address) mOffer.ipAddress.getAddress(),  // DHCP_REQUESTED_IP
                    (Inet4Address) mOffer.serverAddress,           // DHCP_SERVER_IDENTIFIER
                    INADDR_BROADCAST);                             // packet destination address
        }

        protected void receivePacket(DhcpPacket packet) {
            if (!isValidPacket(packet)) return;
            if ((packet instanceof DhcpAckPacket)) {
                DhcpResults results = packet.toDhcpResults();
                if (results != null) {
                    mDhcpLease = results;
                    Log.d(TAG, "Confirmed lease: " + mDhcpLease);
                    mDhcpLeaseExpiry = SystemClock.elapsedRealtime() +
                            mDhcpLease.leaseDuration * 1000;
                    mOffer = null;
                    transitionTo(mDhcpBoundState);
                }
            } else if (packet instanceof DhcpNakPacket) {
                Log.d(TAG, "Received NAK, returning to INIT");
                mOffer = null;
                transitionTo(mDhcpInitState);
            }
        }

        protected void timeout() {
            notifyFailure();
            transitionTo(mDhcpInitState);
        }
    }

    class DhcpBoundState extends LoggingState {
        @Override
        public void enter() {
            super.enter();
            if (!setIpAddress(mDhcpLease.ipAddress)) {
                notifyFailure();
                transitionTo(mStoppedState);
            }
            notifyLease();
            // TODO: DhcpStateMachine only supports renewing at 50% of the lease time, and does not
            // support rebinding. Fix this.
            scheduleRenew();
        }

        @Override
        public boolean processMessage(Message message) {
            super.processMessage(message);
            switch (message.what) {
                case DhcpStateMachine.CMD_RENEW_DHCP:
                    if (mRegisteredForPreDhcpNotification) {
                        transitionTo(mWaitBeforeRenewalState);
                    } else {
                        transitionTo(mDhcpRenewingState);
                    }
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    // TODO: timeout.
    class DhcpRenewingState extends PacketRetransmittingState {
        public DhcpRenewingState() {
            super();
            mTimeout = DHCP_TIMEOUT_MS;
        }

        @Override
        public void enter() {
            super.enter();
            startNewTransaction();
        }

        protected boolean sendPacket() {
            return sendRequestPacket(
                    (Inet4Address) mDhcpLease.ipAddress.getAddress(),  // ciaddr
                    INADDR_ANY,                                        // DHCP_REQUESTED_IP
                    INADDR_ANY,                                        // DHCP_SERVER_IDENTIFIER
                    (Inet4Address) mDhcpLease.serverAddress);          // packet destination address
        }

        protected void receivePacket(DhcpPacket packet) {
            if (!isValidPacket(packet)) return;
            if ((packet instanceof DhcpAckPacket)) {
                DhcpResults results = packet.toDhcpResults();
                mDhcpLease.leaseDuration = results.leaseDuration;
                mDhcpLeaseExpiry = SystemClock.elapsedRealtime() +
                        mDhcpLease.leaseDuration * 1000;
                transitionTo(mDhcpBoundState);
            } else if (packet instanceof DhcpNakPacket) {
                transitionTo(mDhcpInitState);
            }
        }
    }

    // Not implemented. DhcpStateMachine does not implement it either.
    class DhcpRebindingState extends LoggingState {
    }

    class DhcpInitRebootState extends LoggingState {
    }

    class DhcpRebootingState extends LoggingState {
    }
}
