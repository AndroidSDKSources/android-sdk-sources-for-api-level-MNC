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

package android.graphics.drawable;

import android.annotation.DrawableRes;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * An umbrella container for several serializable graphics representations, including Bitmaps,
 * compressed bitmap images (e.g. JPG or PNG), and drawable resources (including vectors).
 *
 * <a href="https://developer.android.com/training/displaying-bitmaps/index.html">Much ink</a>
 * has been spilled on the best way to load images, and many clients may have different needs when
 * it comes to threading and fetching. This class is therefore focused on encapsulation rather than
 * behavior.
 */

public final class Icon implements Parcelable {
    private static final String TAG = "Icon";

    /** @hide */
    public static final int TYPE_BITMAP   = 1;
    /** @hide */
    public static final int TYPE_RESOURCE = 2;
    /** @hide */
    public static final int TYPE_DATA     = 3;
    /** @hide */
    public static final int TYPE_URI      = 4;

    private static final int VERSION_STREAM_SERIALIZER = 1;

    private final int mType;

    // To avoid adding unnecessary overhead, we have a few basic objects that get repurposed
    // based on the value of mType.

    // TYPE_BITMAP: Bitmap
    // TYPE_RESOURCE: Resources
    // TYPE_DATA: DataBytes
    private Object          mObj1;

    // TYPE_RESOURCE: package name
    // TYPE_URI: uri string
    private String          mString1;

    // TYPE_RESOURCE: resId
    // TYPE_DATA: data length
    private int             mInt1;

    // TYPE_DATA: data offset
    private int             mInt2;

    /**
     * @return The type of image data held in this Icon. One of
     * {@link #TYPE_BITMAP},
     * {@link #TYPE_RESOURCE},
     * {@link #TYPE_DATA}, or
     * {@link #TYPE_URI}.
     * @hide
     */
    public int getType() {
        return mType;
    }

    /**
     * @return The {@link android.graphics.Bitmap} held by this {@link #TYPE_BITMAP} Icon.
     * @hide
     */
    public Bitmap getBitmap() {
        if (mType != TYPE_BITMAP) {
            throw new IllegalStateException("called getBitmap() on " + this);
        }
        return (Bitmap) mObj1;
    }

    /**
     * @return The length of the compressed bitmap byte array held by this {@link #TYPE_DATA} Icon.
     * @hide
     */
    public int getDataLength() {
        if (mType != TYPE_DATA) {
            throw new IllegalStateException("called getDataLength() on " + this);
        }
        synchronized (this) {
            return mInt1;
        }
    }

    /**
     * @return The offset into the byte array held by this {@link #TYPE_DATA} Icon at which
     * valid compressed bitmap data is found.
     * @hide
     */
    public int getDataOffset() {
        if (mType != TYPE_DATA) {
            throw new IllegalStateException("called getDataOffset() on " + this);
        }
        synchronized (this) {
            return mInt2;
        }
    }

    /**
     * @return The byte array held by this {@link #TYPE_DATA} Icon ctonaining compressed
     * bitmap data.
     * @hide
     */
    public byte[] getDataBytes() {
        if (mType != TYPE_DATA) {
            throw new IllegalStateException("called getDataBytes() on " + this);
        }
        synchronized (this) {
            return (byte[]) mObj1;
        }
    }

    /**
     * @return The {@link android.content.res.Resources} for this {@link #TYPE_RESOURCE} Icon.
     * @hide
     */
    public Resources getResources() {
        if (mType != TYPE_RESOURCE) {
            throw new IllegalStateException("called getResources() on " + this);
        }
        return (Resources) mObj1;
    }

    /**
     * @return The package containing resources for this {@link #TYPE_RESOURCE} Icon.
     * @hide
     */
    public String getResPackage() {
        if (mType != TYPE_RESOURCE) {
            throw new IllegalStateException("called getResPackage() on " + this);
        }
        return mString1;
    }

    /**
     * @return The resource ID for this {@link #TYPE_RESOURCE} Icon.
     * @hide
     */
    public int getResId() {
        if (mType != TYPE_RESOURCE) {
            throw new IllegalStateException("called getResId() on " + this);
        }
        return mInt1;
    }

    /**
     * @return The URI (as a String) for this {@link #TYPE_URI} Icon.
     * @hide
     */
    public String getUriString() {
        if (mType != TYPE_URI) {
            throw new IllegalStateException("called getUriString() on " + this);
        }
        return mString1;
    }

    /**
     * @return The {@link android.net.Uri} for this {@link #TYPE_URI} Icon.
     * @hide
     */
    public Uri getUri() {
        return Uri.parse(getUriString());
    }

    private static final String typeToString(int x) {
        switch (x) {
            case TYPE_BITMAP: return "BITMAP";
            case TYPE_DATA: return "DATA";
            case TYPE_RESOURCE: return "RESOURCE";
            case TYPE_URI: return "URI";
            default: return "UNKNOWN";
        }
    }

    /**
     * Invokes {@link #loadDrawable(Context)} on the given {@link android.os.Handler Handler}
     * and then sends <code>andThen</code> to the same Handler when finished.
     *
     * @param context {@link android.content.Context Context} in which to load the drawable; see
     *                {@link #loadDrawable(Context)}
     * @param andThen {@link android.os.Message} to send to its target once the drawable
     *                is available. The {@link android.os.Message#obj obj}
     *                property is populated with the Drawable.
     */
    public void loadDrawableAsync(Context context, Message andThen) {
        if (andThen.getTarget() == null) {
            throw new IllegalArgumentException("callback message must have a target handler");
        }
        new LoadDrawableTask(context, andThen).runAsync();
    }

    /**
     * Invokes {@link #loadDrawable(Context)} on a background thread
     * and then runs <code>andThen</code> on the UI thread when finished.
     *
     * @param context {@link Context Context} in which to load the drawable; see
     *                {@link #loadDrawable(Context)}
     * @param listener a callback to run on the provided
     * @param handler {@link Handler} on which to run <code>andThen</code>.
     */
    public void loadDrawableAsync(Context context, final OnDrawableLoadedListener listener,
            Handler handler) {
        new LoadDrawableTask(context, handler, listener).runAsync();
    }

    /**
     * Returns a Drawable that can be used to draw the image inside this Icon, constructing it
     * if necessary. Depending on the type of image, this may not be something you want to do on
     * the UI thread, so consider using
     * {@link #loadDrawableAsync(Context, Message) loadDrawableAsync} instead.
     *
     * @param context {@link android.content.Context Context} in which to load the drawable; used
     *                to access {@link android.content.res.Resources Resources}, for example.
     * @return A fresh instance of a drawable for this image, yours to keep.
     */
    public Drawable loadDrawable(Context context) {
        switch (mType) {
            case TYPE_BITMAP:
                return new BitmapDrawable(context.getResources(), getBitmap());
            case TYPE_RESOURCE:
                if (getResources() == null) {
                    if (getResPackage() == null || "android".equals(getResPackage())) {
                        mObj1 = Resources.getSystem();
                    } else {
                        final PackageManager pm = context.getPackageManager();
                        try {
                            mObj1 = pm.getResourcesForApplication(getResPackage());
                        } catch (PackageManager.NameNotFoundException e) {
                            Log.e(TAG, String.format("Unable to find pkg=%s",
                                            getResPackage()),
                                    e);
                            break;
                        }
                    }
                }
                try {
                    return getResources().getDrawable(getResId(), context.getTheme());
                } catch (RuntimeException e) {
                    Log.e(TAG, String.format("Unable to load resource 0x%08x from pkg=%s",
                                    getResId(),
                                    getResPackage()),
                            e);
                }
                break;
            case TYPE_DATA:
                return new BitmapDrawable(context.getResources(),
                    BitmapFactory.decodeByteArray(getDataBytes(), getDataOffset(), getDataLength())
                );
            case TYPE_URI:
                final Uri uri = getUri();
                final String scheme = uri.getScheme();
                InputStream is = null;
                if (ContentResolver.SCHEME_CONTENT.equals(scheme)
                        || ContentResolver.SCHEME_FILE.equals(scheme)) {
                    try {
                        is = context.getContentResolver().openInputStream(uri);
                    } catch (Exception e) {
                        Log.w(TAG, "Unable to load image from URI: " + uri, e);
                    }
                } else {
                    try {
                        is = new FileInputStream(new File(mString1));
                    } catch (FileNotFoundException e) {
                        Log.w(TAG, "Unable to load image from path: " + uri, e);
                    }
                }
                if (is != null) {
                    return new BitmapDrawable(context.getResources(),
                            BitmapFactory.decodeStream(is));
                }
                break;
        }
        return null;
    }

    /**
     * Load the requested resources under the given userId, if the system allows it,
     * before actually loading the drawable.
     *
     * @hide
     */
    public Drawable loadDrawableAsUser(Context context, int userId) {
        if (mType == TYPE_RESOURCE) {
            if (getResources() == null
                    && getResPackage() != null
                    && !(getResPackage().equals("android"))) {
                final PackageManager pm = context.getPackageManager();
                try {
                    mObj1 = pm.getResourcesForApplicationAsUser(getResPackage(), userId);
                } catch (PackageManager.NameNotFoundException e) {
                    Log.e(TAG, String.format("Unable to find pkg=%s user=%d",
                                    getResPackage(),
                                    userId),
                            e);
                }
            }
        }
        return loadDrawable(context);
    }

    /**
     * Writes a serialized version of an Icon to the specified stream.
     *
     * @param stream The stream on which to serialize the Icon.
     * @hide
     */
    public void writeToStream(OutputStream stream) throws IOException {
        DataOutputStream dataStream = new DataOutputStream(stream);

        dataStream.writeInt(VERSION_STREAM_SERIALIZER);
        dataStream.writeByte(mType);

        switch (mType) {
            case TYPE_BITMAP:
                getBitmap().compress(Bitmap.CompressFormat.PNG, 100, dataStream);
                break;
            case TYPE_DATA:
                dataStream.writeInt(getDataLength());
                dataStream.write(getDataBytes(), getDataOffset(), getDataLength());
                break;
            case TYPE_RESOURCE:
                dataStream.writeUTF(getResPackage());
                dataStream.writeInt(getResId());
                break;
            case TYPE_URI:
                dataStream.writeUTF(getUriString());
                break;
        }
    }

    private Icon(int mType) {
        this.mType = mType;
    }

    /**
     * Create an Icon from the specified stream.
     *
     * @param stream The input stream from which to reconstruct the Icon.
     * @hide
     */
    public static Icon createFromStream(InputStream stream) throws IOException {
        DataInputStream inputStream = new DataInputStream(stream);

        final int version = inputStream.readInt();
        if (version >= VERSION_STREAM_SERIALIZER) {
            final int type = inputStream.readByte();
            switch (type) {
                case TYPE_BITMAP:
                    return createWithBitmap(BitmapFactory.decodeStream(inputStream));
                case TYPE_DATA:
                    final int length = inputStream.readInt();
                    final byte[] data = new byte[length];
                    inputStream.read(data, 0 /* offset */, length);
                    return createWithData(data, 0 /* offset */, length);
                case TYPE_RESOURCE:
                    final String packageName = inputStream.readUTF();
                    final int resId = inputStream.readInt();
                    return createWithResource(packageName, resId);
                case TYPE_URI:
                    final String uriOrPath = inputStream.readUTF();
                    return createWithContentUri(uriOrPath);
            }
        }
        return null;
    }

    /**
     * Create an Icon pointing to a drawable resource.
     * @param context The context for the application whose resources should be used to resolve the
     *                given resource ID.
     * @param resId ID of the drawable resource
     */
    public static Icon createWithResource(Context context, @DrawableRes int resId) {
        final Icon rep = new Icon(TYPE_RESOURCE);
        rep.mInt1 = resId;
        rep.mString1 = context.getPackageName();
        return rep;
    }

    /**
     * Version of createWithResource that takes Resources. Do not use.
     * @hide
     */
    public static Icon createWithResource(Resources res, @DrawableRes int resId) {
        if (res == null) {
            throw new IllegalArgumentException("Resource must not be null.");
        }
        final Icon rep = new Icon(TYPE_RESOURCE);
        rep.mInt1 = resId;
        rep.mString1 = res.getResourcePackageName(resId);
        return rep;
    }

    /**
     * Create an Icon pointing to a drawable resource.
     * @param resPackage Name of the package containing the resource in question
     * @param resId ID of the drawable resource
     */
    public static Icon createWithResource(String resPackage, @DrawableRes int resId) {
        if (resPackage == null) {
            throw new IllegalArgumentException("Resource package name must not be null.");
        }
        final Icon rep = new Icon(TYPE_RESOURCE);
        rep.mInt1 = resId;
        rep.mString1 = resPackage;
        return rep;
    }

    /**
     * Create an Icon pointing to a bitmap in memory.
     * @param bits A valid {@link android.graphics.Bitmap} object
     */
    public static Icon createWithBitmap(Bitmap bits) {
        if (bits == null) {
            throw new IllegalArgumentException("Bitmap must not be null.");
        }
        final Icon rep = new Icon(TYPE_BITMAP);
        rep.mObj1 = bits;
        return rep;
    }

    /**
     * Create an Icon pointing to a compressed bitmap stored in a byte array.
     * @param data Byte array storing compressed bitmap data of a type that
     *             {@link android.graphics.BitmapFactory}
     *             can decode (see {@link android.graphics.Bitmap.CompressFormat}).
     * @param offset Offset into <code>data</code> at which the bitmap data starts
     * @param length Length of the bitmap data
     */
    public static Icon createWithData(byte[] data, int offset, int length) {
        if (data == null) {
            throw new IllegalArgumentException("Data must not be null.");
        }
        final Icon rep = new Icon(TYPE_DATA);
        rep.mObj1 = data;
        rep.mInt1 = length;
        rep.mInt2 = offset;
        return rep;
    }

    /**
     * Create an Icon pointing to an image file specified by URI.
     *
     * @param uri A uri referring to local content:// or file:// image data.
     */
    public static Icon createWithContentUri(String uri) {
        if (uri == null) {
            throw new IllegalArgumentException("Uri must not be null.");
        }
        final Icon rep = new Icon(TYPE_URI);
        rep.mString1 = uri;
        return rep;
    }

    /**
     * Create an Icon pointing to an image file specified by URI.
     *
     * @param uri A uri referring to local content:// or file:// image data.
     */
    public static Icon createWithContentUri(Uri uri) {
        if (uri == null) {
            throw new IllegalArgumentException("Uri must not be null.");
        }
        final Icon rep = new Icon(TYPE_URI);
        rep.mString1 = uri.toString();
        return rep;
    }

    /**
     * Create an Icon pointing to an image file specified by path.
     *
     * @param path A path to a file that contains compressed bitmap data of
     *           a type that {@link android.graphics.BitmapFactory} can decode.
     */
    public static Icon createWithFilePath(String path) {
        if (path == null) {
            throw new IllegalArgumentException("Path must not be null.");
        }
        final Icon rep = new Icon(TYPE_URI);
        rep.mString1 = path;
        return rep;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Icon(typ=").append(typeToString(mType));
        switch (mType) {
            case TYPE_BITMAP:
                sb.append(" size=")
                        .append(getBitmap().getWidth())
                        .append("x")
                        .append(getBitmap().getHeight());
                break;
            case TYPE_RESOURCE:
                sb.append(" pkg=")
                        .append(getResPackage())
                        .append(" id=")
                        .append(String.format("0x%08x", getResId()));
                break;
            case TYPE_DATA:
                sb.append(" len=").append(getDataLength());
                if (getDataOffset() != 0) {
                    sb.append(" off=").append(getDataOffset());
                }
                break;
            case TYPE_URI:
                sb.append(" uri=").append(getUriString());
                break;
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Parcelable interface
     */
    public int describeContents() {
        return (mType == TYPE_BITMAP || mType == TYPE_DATA)
                ? Parcelable.CONTENTS_FILE_DESCRIPTOR : 0;
    }

    // ===== Parcelable interface ======

    private Icon(Parcel in) {
        this(in.readInt());
        switch (mType) {
            case TYPE_BITMAP:
                final Bitmap bits = Bitmap.CREATOR.createFromParcel(in);
                mObj1 = bits;
                break;
            case TYPE_RESOURCE:
                final String pkg = in.readString();
                final int resId = in.readInt();
                mString1 = pkg;
                mInt1 = resId;
                break;
            case TYPE_DATA:
                final int len = in.readInt();
                final byte[] a = in.readBlob();
                if (len != a.length) {
                    throw new RuntimeException("internal unparceling error: blob length ("
                            + a.length + ") != expected length (" + len + ")");
                }
                mInt1 = len;
                mObj1 = a;
                break;
            case TYPE_URI:
                final String uri = in.readString();
                mString1 = uri;
                break;
            default:
                throw new RuntimeException("invalid "
                        + this.getClass().getSimpleName() + " type in parcel: " + mType);
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        switch (mType) {
            case TYPE_BITMAP:
                final Bitmap bits = getBitmap();
                dest.writeInt(TYPE_BITMAP);
                getBitmap().writeToParcel(dest, flags);
                break;
            case TYPE_RESOURCE:
                dest.writeInt(TYPE_RESOURCE);
                dest.writeString(getResPackage());
                dest.writeInt(getResId());
                break;
            case TYPE_DATA:
                dest.writeInt(TYPE_DATA);
                dest.writeInt(getDataLength());
                dest.writeBlob(getDataBytes(), getDataOffset(), getDataLength());
                break;
            case TYPE_URI:
                dest.writeInt(TYPE_URI);
                dest.writeString(getUriString());
                break;
        }
    }

    public static final Parcelable.Creator<Icon> CREATOR
            = new Parcelable.Creator<Icon>() {
        public Icon createFromParcel(Parcel in) {
            return new Icon(in);
        }

        public Icon[] newArray(int size) {
            return new Icon[size];
        }
    };

    /**
     * Implement this interface to receive a callback when
     * {@link #loadDrawableAsync(Context, OnDrawableLoadedListener, Handler) loadDrawableAsync}
     * is finished and your Drawable is ready.
     */
    public interface OnDrawableLoadedListener {
        void onDrawableLoaded(Drawable d);
    }

    /**
     * Wrapper around loadDrawable that does its work on a pooled thread and then
     * fires back the given (targeted) Message.
     */
    private class LoadDrawableTask implements Runnable {
        final Context mContext;
        final Message mMessage;

        public LoadDrawableTask(Context context, final Handler handler,
                final OnDrawableLoadedListener listener) {
            mContext = context;
            mMessage = Message.obtain(handler, new Runnable() {
                    @Override
                    public void run() {
                        listener.onDrawableLoaded((Drawable) mMessage.obj);
                    }
                });
        }

        public LoadDrawableTask(Context context, Message message) {
            mContext = context;
            mMessage = message;
        }

        @Override
        public void run() {
            mMessage.obj = loadDrawable(mContext);
            mMessage.sendToTarget();
        }

        public void runAsync() {
            AsyncTask.THREAD_POOL_EXECUTOR.execute(this);
        }
    }
}
