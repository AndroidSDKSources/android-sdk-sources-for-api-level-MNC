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
package android.support.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * Denotes that the annotated element requires (or may require) one or more permissions.
 * <p/>
 * Example of requiring a single permission:
 * <pre>{@code
 *   &#64;RequiresPermission(Manifest.permission.SET_WALLPAPER)
 *   public abstract void setWallpaper(Bitmap bitmap) throws IOException;
 *
 *   &#64;RequiresPermission(ACCESS_COARSE_LOCATION)
 *   public abstract Location getLastKnownLocation(String provider);
 * }</pre>
 * Example of requiring at least one permission from a set:
 * <pre>{@code
 *   &#64;RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
 *   public abstract Location getLastKnownLocation(String provider);
 * }</pre>
 * Example of requiring multiple permissions:
 * <pre>{@code
 *   &#64;RequiresPermission(allOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
 *   public abstract Location getLastKnownLocation(String provider);
 * }</pre>
 * Example of requiring separate read and write permissions for a content provider:
 * <pre>{@code
 *   &#64;RequiresPermission.Read(&#64;RequiresPermission(READ_HISTORY_BOOKMARKS))
 *   &#64;RequiresPermission.Write(&#64;RequiresPermission(WRITE_HISTORY_BOOKMARKS))
 *   public static final Uri BOOKMARKS_URI = Uri.parse("content://browser/bookmarks");
 * }</pre>
 *
 * @hide
 */
@Retention(CLASS)
@Target({ANNOTATION_TYPE,METHOD,CONSTRUCTOR,FIELD})
public @interface RequiresPermission {
    /**
     * The name of the permission that is required, if precisely one permission
     * is required. If more than one permission is required, specify either
     * {@link #allOf()} or {@link #anyOf()} instead.
     * <p>
     * If specified, {@link #anyOf()} and {@link #allOf()} must both be null.
     */
    String value() default "";

    /**
     * Specifies a list of permission names that are all required.
     * <p>
     * If specified, {@link #anyOf()} and {@link #value()} must both be null.
     */
    String[] allOf() default {};

    /**
     * Specifies a list of permission names where at least one is required
     * <p>
     * If specified, {@link #allOf()} and {@link #value()} must both be null.
     */
    String[] anyOf() default {};

    /**
     * If true, the permission may not be required in all cases (e.g. it may only be
     * enforced on certain platforms, or for certain call parameters, etc.
     */
    boolean conditional() default false;

    /**
     * Specifies that the given permission is required for read operations
     */
    @Target(FIELD)
    @interface Read {
        RequiresPermission value();
    }

    /**
     * Specifies that the given permission is required for write operations
     */
    @Target(FIELD)
    @interface Write {
        RequiresPermission value();
    }
}
