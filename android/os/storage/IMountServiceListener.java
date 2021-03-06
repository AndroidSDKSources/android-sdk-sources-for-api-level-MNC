/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.os.storage;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/**
 * Callback class for receiving events from MountService.
 * 
 * @hide - Applications should use IStorageEventListener for storage event
 *       callbacks.
 */
public interface IMountServiceListener extends IInterface {
    /** Local-side IPC implementation stub class. */
    public static abstract class Stub extends Binder implements IMountServiceListener {
        private static final String DESCRIPTOR = "IMountServiceListener";

        /** Construct the stub at attach it to the interface. */
        public Stub() {
            this.attachInterface(this, DESCRIPTOR);
        }

        /**
         * Cast an IBinder object into an IMountServiceListener interface,
         * generating a proxy if needed.
         */
        public static IMountServiceListener asInterface(IBinder obj) {
            if ((obj == null)) {
                return null;
            }
            IInterface iin = (IInterface) obj.queryLocalInterface(DESCRIPTOR);
            if (((iin != null) && (iin instanceof IMountServiceListener))) {
                return ((IMountServiceListener) iin);
            }
            return new IMountServiceListener.Stub.Proxy(obj);
        }

        public IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            switch (code) {
                case INTERFACE_TRANSACTION: {
                    reply.writeString(DESCRIPTOR);
                    return true;
                }
                case TRANSACTION_onUsbMassStorageConnectionChanged: {
                    data.enforceInterface(DESCRIPTOR);
                    boolean connected;
                    connected = (0 != data.readInt());
                    this.onUsbMassStorageConnectionChanged(connected);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_onStorageStateChanged: {
                    data.enforceInterface(DESCRIPTOR);
                    final String path = data.readString();
                    final String oldState = data.readString();
                    final String newState = data.readString();
                    this.onStorageStateChanged(path, oldState, newState);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_onVolumeStateChanged: {
                    data.enforceInterface(DESCRIPTOR);
                    final VolumeInfo vol = (VolumeInfo) data.readParcelable(null);
                    final int oldState = data.readInt();
                    final int newState = data.readInt();
                    onVolumeStateChanged(vol, oldState, newState);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_onVolumeRecordChanged: {
                    data.enforceInterface(DESCRIPTOR);
                    final VolumeRecord rec = (VolumeRecord) data.readParcelable(null);
                    onVolumeRecordChanged(rec);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_onVolumeForgotten: {
                    data.enforceInterface(DESCRIPTOR);
                    final String fsUuid = data.readString();
                    onVolumeForgotten(fsUuid);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_onDiskScanned: {
                    data.enforceInterface(DESCRIPTOR);
                    final DiskInfo disk = (DiskInfo) data.readParcelable(null);
                    final int volumeCount = data.readInt();
                    onDiskScanned(disk, volumeCount);
                    reply.writeNoException();
                    return true;
                }
            }
            return super.onTransact(code, data, reply, flags);
        }

        private static class Proxy implements IMountServiceListener {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                mRemote = remote;
            }

            public IBinder asBinder() {
                return mRemote;
            }

            public String getInterfaceDescriptor() {
                return DESCRIPTOR;
            }

            /**
             * Detection state of USB Mass Storage has changed
             * 
             * @param available true if a UMS host is connected.
             */
            public void onUsbMassStorageConnectionChanged(boolean connected) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeInt(((connected) ? (1) : (0)));
                    mRemote.transact(Stub.TRANSACTION_onUsbMassStorageConnectionChanged, _data,
                            _reply, android.os.IBinder.FLAG_ONEWAY);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            /**
             * Storage state has changed.
             * 
             * @param path The volume mount path.
             * @param oldState The old state of the volume.
             * @param newState The new state of the volume. Note: State is one
             *            of the values returned by
             *            Environment.getExternalStorageState()
             */
            public void onStorageStateChanged(String path, String oldState, String newState)
                    throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeString(path);
                    _data.writeString(oldState);
                    _data.writeString(newState);
                    mRemote.transact(Stub.TRANSACTION_onStorageStateChanged, _data, _reply,
                            android.os.IBinder.FLAG_ONEWAY);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public void onVolumeStateChanged(VolumeInfo vol, int oldState, int newState)
                    throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeParcelable(vol, 0);
                    _data.writeInt(oldState);
                    _data.writeInt(newState);
                    mRemote.transact(Stub.TRANSACTION_onVolumeStateChanged, _data, _reply,
                            android.os.IBinder.FLAG_ONEWAY);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public void onVolumeRecordChanged(VolumeRecord rec) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeParcelable(rec, 0);
                    mRemote.transact(Stub.TRANSACTION_onVolumeRecordChanged, _data, _reply,
                            android.os.IBinder.FLAG_ONEWAY);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public void onVolumeForgotten(String fsUuid) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeString(fsUuid);
                    mRemote.transact(Stub.TRANSACTION_onVolumeForgotten, _data, _reply,
                            android.os.IBinder.FLAG_ONEWAY);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public void onDiskScanned(DiskInfo disk, int volumeCount) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeParcelable(disk, 0);
                    _data.writeInt(volumeCount);
                    mRemote.transact(Stub.TRANSACTION_onDiskScanned, _data, _reply,
                            android.os.IBinder.FLAG_ONEWAY);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }

        static final int TRANSACTION_onUsbMassStorageConnectionChanged = (IBinder.FIRST_CALL_TRANSACTION + 0);
        static final int TRANSACTION_onStorageStateChanged = (IBinder.FIRST_CALL_TRANSACTION + 1);
        static final int TRANSACTION_onVolumeStateChanged = (IBinder.FIRST_CALL_TRANSACTION + 2);
        static final int TRANSACTION_onVolumeRecordChanged = (IBinder.FIRST_CALL_TRANSACTION + 3);
        static final int TRANSACTION_onVolumeForgotten = (IBinder.FIRST_CALL_TRANSACTION + 4);
        static final int TRANSACTION_onDiskScanned = (IBinder.FIRST_CALL_TRANSACTION + 5);
    }

    /**
     * Detection state of USB Mass Storage has changed
     * 
     * @param available true if a UMS host is connected.
     */
    public void onUsbMassStorageConnectionChanged(boolean connected) throws RemoteException;

    /**
     * Storage state has changed.
     * 
     * @param path The volume mount path.
     * @param oldState The old state of the volume.
     * @param newState The new state of the volume. Note: State is one of the
     *            values returned by Environment.getExternalStorageState()
     */
    public void onStorageStateChanged(String path, String oldState, String newState)
            throws RemoteException;

    public void onVolumeStateChanged(VolumeInfo vol, int oldState, int newState)
            throws RemoteException;
    public void onVolumeRecordChanged(VolumeRecord rec) throws RemoteException;
    public void onVolumeForgotten(String fsUuid) throws RemoteException;

    public void onDiskScanned(DiskInfo disk, int volumeCount) throws RemoteException;
}
