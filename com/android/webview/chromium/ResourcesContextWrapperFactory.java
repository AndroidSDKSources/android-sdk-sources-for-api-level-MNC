/*
 * Copyright 2014 The Android Open Source Project
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
package com.android.webview.chromium;

import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.view.LayoutInflater;

import java.util.WeakHashMap;

/**
 * This class allows us to wrap the application context so that the WebView implementation can
 * correctly reference both org.chromium.* and application classes which is necessary to properly
 * inflate UI.  We keep a weak map from contexts to wrapped contexts to avoid constantly re-wrapping
 * or doubly wrapping contexts.
 */
public class ResourcesContextWrapperFactory {
    private static WeakHashMap<Context,ContextWrapper> sCtxToWrapper
            = new WeakHashMap<Context,ContextWrapper>();
    private static final Object sLock = new Object();

    private ResourcesContextWrapperFactory() {
    }

    public static Context get(Context ctx) {
        ContextWrapper wrappedCtx;
        synchronized (sLock) {
            wrappedCtx = sCtxToWrapper.get(ctx);
            if (wrappedCtx == null) {
                wrappedCtx = createWrapper(ctx);
                sCtxToWrapper.put(ctx, wrappedCtx);
            }
        }
        return wrappedCtx;
    }

    private static ContextWrapper createWrapper(final Context ctx) {
        return new ContextWrapper(ctx) {
            private Context applicationContext;

            @Override
            public ClassLoader getClassLoader() {
                final ClassLoader appCl = getBaseContext().getClassLoader();
                final ClassLoader webViewCl = this.getClass().getClassLoader();
                return new ClassLoader() {
                    @Override
                    protected Class<?> findClass(String name) throws ClassNotFoundException {
                        // First look in the WebViewProvider class loader.
                        try {
                            return webViewCl.loadClass(name);
                        } catch (ClassNotFoundException e) {
                            // Look in the app class loader; allowing it to throw ClassNotFoundException.
                            return appCl.loadClass(name);
                        }
                    }
                };
            }

            @Override
            public Object getSystemService(String name) {
                if (Context.LAYOUT_INFLATER_SERVICE.equals(name)) {
                    LayoutInflater i = (LayoutInflater) getBaseContext().getSystemService(name);
                    return i.cloneInContext(this);
                } else {
                    return getBaseContext().getSystemService(name);
                }
            }

            @Override
            public Context getApplicationContext() {
                if (applicationContext == null)
                    applicationContext = get(ctx.getApplicationContext());
                return applicationContext;
            }

            @Override
            public void registerComponentCallbacks(ComponentCallbacks callback) {
                // We have to override registerComponentCallbacks and unregisterComponentCallbacks
                // since they call getApplicationContext().[un]registerComponentCallbacks()
                // which causes us to go into a loop.
                ctx.registerComponentCallbacks(callback);
            }

            @Override
            public void unregisterComponentCallbacks(ComponentCallbacks callback) {
                ctx.unregisterComponentCallbacks(callback);
            }
        };
    }
}
