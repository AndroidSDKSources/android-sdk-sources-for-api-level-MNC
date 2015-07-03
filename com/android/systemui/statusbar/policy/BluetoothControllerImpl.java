/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.systemui.statusbar.policy;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.os.Looper;
import android.util.Log;

import com.android.settingslib.bluetooth.BluetoothCallback;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;

public class BluetoothControllerImpl implements BluetoothController, BluetoothCallback,
        CachedBluetoothDevice.Callback {
    private static final String TAG = "BluetoothController";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final ArrayList<Callback> mCallbacks = new ArrayList<Callback>();
    private final LocalBluetoothManager mLocalBluetoothManager;

    private boolean mEnabled;
    private boolean mConnecting;
    private CachedBluetoothDevice mLastDevice;

    public BluetoothControllerImpl(Context context, Looper bgLooper) {
        mLocalBluetoothManager = LocalBluetoothManager.getInstance(context, null);
        if (mLocalBluetoothManager != null) {
            mLocalBluetoothManager.getEventManager().registerCallback(this);
            onBluetoothStateChanged(
                    mLocalBluetoothManager.getBluetoothAdapter().getBluetoothState());
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("BluetoothController state:");
        pw.print("  mLocalBluetoothManager="); pw.println(mLocalBluetoothManager);
        if (mLocalBluetoothManager == null) {
            return;
        }
        pw.print("  mEnabled="); pw.println(mEnabled);
        pw.print("  mConnecting="); pw.println(mConnecting);
        pw.print("  mLastDevice="); pw.println(mLastDevice);
        pw.print("  mCallbacks.size="); pw.println(mCallbacks.size());
        pw.println("  Bluetooth Devices:");
        for (CachedBluetoothDevice device :
                mLocalBluetoothManager.getCachedDeviceManager().getCachedDevicesCopy()) {
            pw.println("    " + getDeviceString(device));
        }
    }

    private String getDeviceString(CachedBluetoothDevice device) {
        return device.getName() + " " + device.getBondState() + " " + device.isConnected();
    }

    public void addStateChangedCallback(Callback cb) {
        mCallbacks.add(cb);
        fireStateChange(cb);
    }

    @Override
    public void removeStateChangedCallback(Callback cb) {
        mCallbacks.remove(cb);
    }

    @Override
    public boolean isBluetoothEnabled() {
        return mEnabled;
    }

    @Override
    public boolean isBluetoothConnected() {
        return mLocalBluetoothManager != null
                && mLocalBluetoothManager.getBluetoothAdapter().getConnectionState()
                == BluetoothAdapter.STATE_CONNECTED;
    }

    @Override
    public boolean isBluetoothConnecting() {
        return mConnecting;
    }

    @Override
    public void setBluetoothEnabled(boolean enabled) {
        if (mLocalBluetoothManager != null) {
            mLocalBluetoothManager.getBluetoothAdapter().setBluetoothEnabled(enabled);
        }
    }

    @Override
    public boolean isBluetoothSupported() {
        return mLocalBluetoothManager != null;
    }

    @Override
    public void connect(final CachedBluetoothDevice device) {
        if (mLocalBluetoothManager == null || device == null) return;
        device.connect(true);
    }

    @Override
    public void disconnect(CachedBluetoothDevice device) {
        if (mLocalBluetoothManager == null || device == null) return;
        device.disconnect();
    }

    @Override
    public String getLastDeviceName() {
        return mLastDevice != null ? mLastDevice.getName() : null;
    }

    @Override
    public Collection<CachedBluetoothDevice> getDevices() {
        return mLocalBluetoothManager != null
                ? mLocalBluetoothManager.getCachedDeviceManager().getCachedDevicesCopy()
                : null;
    }

    private void firePairedDevicesChanged() {
        for (Callback cb : mCallbacks) {
            cb.onBluetoothDevicesChanged();
        }
    }

    private void fireStateChange() {
        for (Callback cb : mCallbacks) {
            fireStateChange(cb);
        }
    }

    private void fireStateChange(Callback cb) {
        cb.onBluetoothStateChange(mEnabled, mConnecting);
    }

    private void updateConnected() {
        if (mLastDevice != null && mLastDevice.isConnected()) {
            // Our current device is still valid.
            return;
        }
        for (CachedBluetoothDevice device : getDevices()) {
            if (device.isConnected()) {
                mLastDevice = device;
            }
        }
    }

    @Override
    public void onBluetoothStateChanged(int bluetoothState) {
        mEnabled = bluetoothState == BluetoothAdapter.STATE_ON;
        fireStateChange();
    }

    @Override
    public void onScanningStateChanged(boolean started) {
        // Don't care.
    }

    @Override
    public void onDeviceAdded(CachedBluetoothDevice cachedDevice) {
        cachedDevice.registerCallback(this);
        updateConnected();
        firePairedDevicesChanged();
    }

    @Override
    public void onDeviceDeleted(CachedBluetoothDevice cachedDevice) {
        updateConnected();
        firePairedDevicesChanged();
    }

    @Override
    public void onDeviceBondStateChanged(CachedBluetoothDevice cachedDevice, int bondState) {
        updateConnected();
        firePairedDevicesChanged();
    }

    @Override
    public void onDeviceAttributesChanged() {
        updateConnected();
        firePairedDevicesChanged();
    }

    @Override
    public void onConnectionStateChanged(CachedBluetoothDevice cachedDevice, int state) {
        mConnecting = state == BluetoothAdapter.STATE_CONNECTING;
        mLastDevice = cachedDevice;
        updateConnected();
        fireStateChange();
    }
}
