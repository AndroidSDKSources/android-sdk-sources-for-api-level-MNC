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
package com.android.settingslib.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.Toast;

import com.android.internal.annotations.VisibleForTesting;
import com.android.settingslib.R;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks saved or available wifi networks and their state.
 */
public class WifiTracker {
    private static final String TAG = "WifiTracker";

    /** verbose logging flag. this flag is set thru developer debugging options
     * and used so as to assist with in-the-field WiFi connectivity debugging  */
    public static int sVerboseLogging = 0;

    // TODO: Allow control of this?
    // Combo scans can take 5-6s to complete - set to 10s.
    private static final int WIFI_RESCAN_INTERVAL_MS = 10 * 1000;

    private final Context mContext;
    private final WifiManager mWifiManager;
    private final IntentFilter mFilter;

    private final AtomicBoolean mConnected = new AtomicBoolean(false);
    private final WifiListener mListener;
    private final boolean mIncludeSaved;
    private final boolean mIncludeScans;
    private final boolean mIncludePasspoints;

    private final MainHandler mMainHandler;
    private final WorkHandler mWorkHandler;

    private boolean mSavedNetworksExist;
    private boolean mRegistered;
    private ArrayList<AccessPoint> mAccessPoints = new ArrayList<>();

    private NetworkInfo mLastNetworkInfo;
    private WifiInfo mLastInfo;

    @VisibleForTesting
    Scanner mScanner;

    public WifiTracker(Context context, WifiListener wifiListener,
            boolean includeSaved, boolean includeScans) {
        this(context, wifiListener, null, includeSaved, includeScans);
    }

    public WifiTracker(Context context, WifiListener wifiListener, Looper workerLooper,
            boolean includeSaved, boolean includeScans) {
        this(context, wifiListener, workerLooper, includeSaved, includeScans, false);
    }

    public WifiTracker(Context context, WifiListener wifiListener,
            boolean includeSaved, boolean includeScans, boolean includePasspoints) {
        this(context, wifiListener, null, includeSaved, includeScans, includePasspoints);
    }

    public WifiTracker(Context context, WifiListener wifiListener, Looper workerLooper,
            boolean includeSaved, boolean includeScans, boolean includePasspoints) {
        this(context, wifiListener, workerLooper, includeSaved, includeScans, includePasspoints,
                (WifiManager) context.getSystemService(Context.WIFI_SERVICE), Looper.myLooper());
    }

    @VisibleForTesting
    WifiTracker(Context context, WifiListener wifiListener, Looper workerLooper,
            boolean includeSaved, boolean includeScans, boolean includePasspoints,
            WifiManager wifiManager, Looper currentLooper) {
        if (!includeSaved && !includeScans) {
            throw new IllegalArgumentException("Must include either saved or scans");
        }
        mContext = context;
        mMainHandler = new MainHandler(currentLooper);
        mWorkHandler = new WorkHandler(
                workerLooper != null ? workerLooper : currentLooper);
        mWifiManager = wifiManager;
        mIncludeSaved = includeSaved;
        mIncludeScans = includeScans;
        mIncludePasspoints = includePasspoints;
        mListener = wifiListener;

        // check if verbose logging has been turned on or off
        sVerboseLogging = mWifiManager.getVerboseLoggingLevel();

        mFilter = new IntentFilter();
        mFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        mFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        mFilter.addAction(WifiManager.NETWORK_IDS_CHANGED_ACTION);
        mFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        mFilter.addAction(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION);
        mFilter.addAction(WifiManager.LINK_CONFIGURATION_CHANGED_ACTION);
        mFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
    }

    /**
     * Forces an update of the wifi networks when not scanning.
     */
    public void forceUpdate() {
        updateAccessPoints();
    }

    /**
     * Force a scan for wifi networks to happen now.
     */
    public void forceScan() {
        if (mWifiManager.isWifiEnabled() && mScanner != null) {
            mScanner.forceScan();
        }
    }

    /**
     * Temporarily stop scanning for wifi networks.
     */
    public void pauseScanning() {
        if (mScanner != null) {
            mScanner.pause();
            mScanner = null;
        }
    }

    /**
     * Resume scanning for wifi networks after it has been paused.
     */
    public void resumeScanning() {
        if (mScanner == null) {
            mScanner = new Scanner();
        }
        if (mWifiManager.isWifiEnabled()) {
            mScanner.resume();
        }
        mWorkHandler.sendEmptyMessage(WorkHandler.MSG_UPDATE_ACCESS_POINTS);
    }

    /**
     * Start tracking wifi networks.
     * Registers listeners and starts scanning for wifi networks. If this is not called
     * then forceUpdate() must be called to populate getAccessPoints().
     */
    public void startTracking() {
        resumeScanning();
        if (!mRegistered) {
            mContext.registerReceiver(mReceiver, mFilter);
            mRegistered = true;
        }
    }

    /**
     * Stop tracking wifi networks.
     * Unregisters all listeners and stops scanning for wifi networks. This should always
     * be called when done with a WifiTracker (if startTracking was called) to ensure
     * proper cleanup.
     */
    public void stopTracking() {
        if (mRegistered) {
            mWorkHandler.removeMessages(WorkHandler.MSG_UPDATE_ACCESS_POINTS);
            mWorkHandler.removeMessages(WorkHandler.MSG_UPDATE_NETWORK_INFO);
            mContext.unregisterReceiver(mReceiver);
            mRegistered = false;
        }
        pauseScanning();
    }

    /**
     * Gets the current list of access points.
     */
    public List<AccessPoint> getAccessPoints() {
        return mAccessPoints;
    }

    public WifiManager getManager() {
        return mWifiManager;
    }

    public boolean isWifiEnabled() {
        return mWifiManager.isWifiEnabled();
    }

    /**
     * @return true when there are saved networks on the device, regardless
     * of whether the WifiTracker is tracking saved networks.
     */
    public boolean doSavedNetworksExist() {
        return mSavedNetworksExist;
    }

    public boolean isConnected() {
        return mConnected.get();
    }

    public void dump(PrintWriter pw) {
        pw.println("  - wifi tracker ------");
        for (AccessPoint accessPoint : mAccessPoints) {
            pw.println("  " + accessPoint);
        }
    }

    private void updateAccessPoints() {
        // Swap the current access points into a cached list.
        ArrayList<AccessPoint> cachedAccessPoints = new ArrayList<>(mAccessPoints);
        ArrayList<AccessPoint> accessPoints = new ArrayList<>();

        // Clear out the configs so we don't think something is saved when it isn't.
        for (AccessPoint accessPoint : cachedAccessPoints) {
            accessPoint.clearConfig();
        }

        /** Lookup table to more quickly update AccessPoints by only considering objects with the
         * correct SSID.  Maps SSID -> List of AccessPoints with the given SSID.  */
        Multimap<String, AccessPoint> apMap = new Multimap<String, AccessPoint>();
        WifiConfiguration connectionConfig = null;

        final List<WifiConfiguration> configs = mWifiManager.getConfiguredNetworks();
        if (configs != null) {
            mSavedNetworksExist = configs.size() != 0;
            for (WifiConfiguration config : configs) {
                if (mLastInfo != null && mLastInfo.getNetworkId() == config.networkId) {
                    connectionConfig = config;
                }
                if (config.selfAdded && config.numAssociation == 0) {
                    continue;
                }
                AccessPoint accessPoint = getCachedOrCreate(config, cachedAccessPoints);
                if (mLastInfo != null && mLastNetworkInfo != null) {
                    if (config.isPasspoint() == false) {
                        accessPoint.update(mLastInfo, mLastNetworkInfo);
                    }
                }
                if (mIncludeSaved) {
                    if (!config.isPasspoint() || mIncludePasspoints)
                        accessPoints.add(accessPoint);

                    if (config.isPasspoint() == false) {
                        apMap.put(accessPoint.getSsid(), accessPoint);
                    }
                } else {
                    // If we aren't using saved networks, drop them into the cache so that
                    // we have access to their saved info.
                    cachedAccessPoints.add(accessPoint);
                }
            }
        }

        final List<ScanResult> results = mWifiManager.getScanResults();
        if (results != null) {
            for (ScanResult result : results) {
                // Ignore hidden and ad-hoc networks.
                if (result.SSID == null || result.SSID.length() == 0 ||
                        result.capabilities.contains("[IBSS]")) {
                    continue;
                }

                boolean found = false;
                for (AccessPoint accessPoint : apMap.getAll(result.SSID)) {
                    if (accessPoint.update(result)) {
                        found = true;
                        break;
                    }
                }
                if (!found && mIncludeScans) {
                    AccessPoint accessPoint = getCachedOrCreate(result, cachedAccessPoints);
                    if (mLastInfo != null && mLastNetworkInfo != null) {
                        accessPoint.update(mLastInfo, mLastNetworkInfo);
                    }

                    if (result.passpointNetwork) {
                        WifiConfiguration config = mWifiManager.getMatchingWifiConfig(result);
                        if (config != null) {
                            accessPoint.update(config);
                        }
                    }

                    if (mLastInfo != null && mLastInfo.getBSSID() != null
                            && mLastInfo.getBSSID().equals(result.BSSID)
                            && connectionConfig != null && connectionConfig.isPasspoint()) {
                        /* This network is connected via this passpoint config */
                        /* SSID match is not going to work for it; so update explicitly */
                        accessPoint.update(connectionConfig);
                    }

                    accessPoints.add(accessPoint);
                    apMap.put(accessPoint.getSsid(), accessPoint);
                }
            }
        }

        // Pre-sort accessPoints to speed preference insertion
        Collections.sort(accessPoints);
        mAccessPoints = accessPoints;
        mMainHandler.sendEmptyMessage(MainHandler.MSG_ACCESS_POINT_CHANGED);
    }

    private AccessPoint getCachedOrCreate(ScanResult result, ArrayList<AccessPoint> cache) {
        final int N = cache.size();
        for (int i = 0; i < N; i++) {
            if (cache.get(i).matches(result)) {
                AccessPoint ret = cache.remove(i);
                ret.update(result);
                return ret;
            }
        }
        return new AccessPoint(mContext, result);
    }

    private AccessPoint getCachedOrCreate(WifiConfiguration config, ArrayList<AccessPoint> cache) {
        final int N = cache.size();
        for (int i = 0; i < N; i++) {
            if (cache.get(i).matches(config)) {
                AccessPoint ret = cache.remove(i);
                ret.loadConfig(config);
                return ret;
            }
        }
        return new AccessPoint(mContext, config);
    }

    private void updateNetworkInfo(NetworkInfo networkInfo) {
        /* sticky broadcasts can call this when wifi is disabled */
        if (!mWifiManager.isWifiEnabled()) {
            mScanner.pause();
            return;
        }

        if (networkInfo != null &&
                networkInfo.getDetailedState() == DetailedState.OBTAINING_IPADDR) {
            mScanner.pause();
        } else {
            mScanner.resume();
        }

        mLastInfo = mWifiManager.getConnectionInfo();
        if (networkInfo != null) {
            mLastNetworkInfo = networkInfo;
        }

        boolean reorder = false;
        for (int i = mAccessPoints.size() - 1; i >= 0; --i) {
            if (mAccessPoints.get(i).update(mLastInfo, mLastNetworkInfo)) {
                reorder = true;
            }
        }
        if (reorder) {
            Collections.sort(mAccessPoints);
            mMainHandler.sendEmptyMessage(MainHandler.MSG_ACCESS_POINT_CHANGED);
        }
    }

    private void updateWifiState(int state) {
        if (state == WifiManager.WIFI_STATE_ENABLED) {
            if (mScanner != null) {
                // We only need to resume if mScanner isn't null because
                // that means we want to be scanning.
                mScanner.resume();
            }
        } else {
            mLastInfo = null;
            mLastNetworkInfo = null;
            if (mScanner != null) {
                mScanner.pause();
            }
        }
        mMainHandler.obtainMessage(MainHandler.MSG_WIFI_STATE_CHANGED, state, 0).sendToTarget();
    }

    public static List<AccessPoint> getCurrentAccessPoints(Context context, boolean includeSaved,
            boolean includeScans, boolean includePasspoints) {
        WifiTracker tracker = new WifiTracker(context,
                null, null, includeSaved, includeScans, includePasspoints);
        tracker.forceUpdate();
        return tracker.getAccessPoints();
    }

    @VisibleForTesting
    final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
                updateWifiState(intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                        WifiManager.WIFI_STATE_UNKNOWN));
            } else if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action) ||
                    WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION.equals(action) ||
                    WifiManager.LINK_CONFIGURATION_CHANGED_ACTION.equals(action)) {
                mWorkHandler.sendEmptyMessage(WorkHandler.MSG_UPDATE_ACCESS_POINTS);
            } else if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
                NetworkInfo info = (NetworkInfo) intent.getParcelableExtra(
                        WifiManager.EXTRA_NETWORK_INFO);
                mConnected.set(info.isConnected());

                mMainHandler.sendEmptyMessage(MainHandler.MSG_CONNECTED_CHANGED);

                mWorkHandler.sendEmptyMessage(WorkHandler.MSG_UPDATE_ACCESS_POINTS);
                mWorkHandler.obtainMessage(WorkHandler.MSG_UPDATE_NETWORK_INFO, info)
                        .sendToTarget();
            } else if (WifiManager.RSSI_CHANGED_ACTION.equals(action)) {
                mWorkHandler.sendEmptyMessage(WorkHandler.MSG_UPDATE_NETWORK_INFO);
            }
        }
    };

    private final class MainHandler extends Handler {
        private static final int MSG_CONNECTED_CHANGED = 0;
        private static final int MSG_WIFI_STATE_CHANGED = 1;
        private static final int MSG_ACCESS_POINT_CHANGED = 2;

        public MainHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (mListener == null) {
                return;
            }
            switch (msg.what) {
                case MSG_CONNECTED_CHANGED:
                    mListener.onConnectedChanged();
                    break;
                case MSG_WIFI_STATE_CHANGED:
                    mListener.onWifiStateChanged(msg.arg1);
                    break;
                case MSG_ACCESS_POINT_CHANGED:
                    mListener.onAccessPointsChanged();
                    break;
            }
        }
    }

    private final class WorkHandler extends Handler {
        private static final int MSG_UPDATE_ACCESS_POINTS = 0;
        private static final int MSG_UPDATE_NETWORK_INFO = 1;

        public WorkHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_ACCESS_POINTS:
                    updateAccessPoints();
                    break;
                case MSG_UPDATE_NETWORK_INFO:
                    updateNetworkInfo((NetworkInfo) msg.obj);
                    break;
            }
        }
    }

    @VisibleForTesting
    class Scanner extends Handler {
        static final int MSG_SCAN = 0;

        private int mRetry = 0;

        void resume() {
            if (!hasMessages(MSG_SCAN)) {
                sendEmptyMessage(MSG_SCAN);
            }
        }

        void forceScan() {
            removeMessages(MSG_SCAN);
            sendEmptyMessage(MSG_SCAN);
        }

        void pause() {
            mRetry = 0;
            removeMessages(MSG_SCAN);
        }

        @VisibleForTesting
        boolean isScanning() {
            return hasMessages(MSG_SCAN);
        }

        @Override
        public void handleMessage(Message message) {
            if (message.what != MSG_SCAN) return;
            if (mWifiManager.startScan()) {
                mRetry = 0;
            } else if (++mRetry >= 3) {
                mRetry = 0;
                if (mContext != null) {
                    Toast.makeText(mContext, R.string.wifi_fail_to_scan, Toast.LENGTH_LONG).show();
                }
                return;
            }
            sendEmptyMessageDelayed(0, WIFI_RESCAN_INTERVAL_MS);
        }
    }

    /** A restricted multimap for use in constructAccessPoints */
    private static class Multimap<K,V> {
        private final HashMap<K,List<V>> store = new HashMap<K,List<V>>();
        /** retrieve a non-null list of values with key K */
        List<V> getAll(K key) {
            List<V> values = store.get(key);
            return values != null ? values : Collections.<V>emptyList();
        }

        void put(K key, V val) {
            List<V> curVals = store.get(key);
            if (curVals == null) {
                curVals = new ArrayList<V>(3);
                store.put(key, curVals);
            }
            curVals.add(val);
        }
    }

    public interface WifiListener {
        /**
         * Called when the state of Wifi has changed, the state will be one of
         * the following.
         *
         * <li>{@link WifiManager#WIFI_STATE_DISABLED}</li>
         * <li>{@link WifiManager#WIFI_STATE_ENABLED}</li>
         * <li>{@link WifiManager#WIFI_STATE_DISABLING}</li>
         * <li>{@link WifiManager#WIFI_STATE_ENABLING}</li>
         * <li>{@link WifiManager#WIFI_STATE_UNKNOWN}</li>
         * <p>
         *
         * @param state The new state of wifi.
         */
        void onWifiStateChanged(int state);

        /**
         * Called when the connection state of wifi has changed and isConnected
         * should be called to get the updated state.
         */
        void onConnectedChanged();

        /**
         * Called to indicate the list of AccessPoints has been updated and
         * getAccessPoints should be called to get the latest information.
         */
        void onAccessPointsChanged();
    }
}
