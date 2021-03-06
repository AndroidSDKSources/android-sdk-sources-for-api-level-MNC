/**
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package android.app.usage;

import android.app.usage.NetworkUsageStats.Bucket;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkIdentity;
import android.net.NetworkTemplate;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;

/**
 * Provides access to network usage history and statistics. Usage data is collected in
 * discrete bins of time called 'Buckets'. See {@link NetworkUsageStats.Bucket} for details.
 * <p />
 * Queries can define a time interval in the form of start and end timestamps (Long.MIN_VALUE and
 * Long.MAX_VALUE can be used to simulate open ended intervals). All queries (except
 * {@link #querySummaryForDevice}) collect only network usage of apps belonging to the same user
 * as the client. In addition tethering usage, usage by removed users and apps, and usage by system
 * is also included in the results.
 * <h3>
 * Summary queries
 * </h3>
 * These queries aggregate network usage across the whole interval. Therefore there will be only one
 * bucket for a particular key and state combination. In case of the user-wide and device-wide
 * summaries a single bucket containing the totalised network usage is returned.
 * <h3>
 * History queries
 * </h3>
 * These queries do not aggregate over time but do aggregate over state. Therefore there can be
 * multiple buckets for a particular key but all Bucket's state is going to be
 * {@link NetworkUsageStats.Bucket#STATE_ALL}.
 * <p />
 * <b>NOTE:</b> This API requires the permission
 * {@link android.Manifest.permission#PACKAGE_USAGE_STATS}, which is a system-level permission and
 * will not be granted to third-party apps. However, declaring the permission implies intention to
 * use the API and the user of the device can grant permission through the Settings application.
 * Profile owner apps are automatically granted permission to query data on the profile they manage
 * (that is, for any query except {@link #querySummaryForDevice}). Device owner apps likewise get
 * access to usage data of the primary user.
 */
public class NetworkStatsManager {
    private final static String TAG = "NetworkStatsManager";

    private final Context mContext;

    /**
     * {@hide}
     */
    public NetworkStatsManager(Context context) {
        mContext = context;
    }
    /**
     * Query network usage statistics summaries. Result is summarised data usage for the whole
     * device. Result is a single Bucket aggregated over time, state and uid.
     *
     * @param networkType As defined in {@link ConnectivityManager}, e.g.
     *            {@link ConnectivityManager#TYPE_MOBILE}, {@link ConnectivityManager#TYPE_WIFI}
     *            etc.
     * @param subscriberId If applicable, the subscriber id of the network interface.
     * @param startTime Start of period. Defined in terms of "Unix time", see
     *            {@link java.lang.System#currentTimeMillis}.
     * @param endTime End of period. Defined in terms of "Unix time", see
     *            {@link java.lang.System#currentTimeMillis}.
     * @return Bucket object or null if permissions are insufficient or error happened during
     *         statistics collection.
     */
    public Bucket querySummaryForDevice(int networkType, String subscriberId,
            long startTime, long endTime) throws SecurityException, RemoteException {
        NetworkTemplate template = createTemplate(networkType, subscriberId);
        if (template == null) {
            return null;
        }

        Bucket bucket = null;
        NetworkUsageStats stats = new NetworkUsageStats(mContext, template, startTime, endTime);
        bucket = stats.getDeviceSummaryForNetwork(startTime, endTime);

        stats.close();
        return bucket;
    }

    /**
     * Query network usage statistics summaries. Result is summarised data usage for all uids
     * belonging to calling user. Result is a single Bucket aggregated over time, state and uid.
     *
     * @param networkType As defined in {@link ConnectivityManager}, e.g.
     *            {@link ConnectivityManager#TYPE_MOBILE}, {@link ConnectivityManager#TYPE_WIFI}
     *            etc.
     * @param subscriberId If applicable, the subscriber id of the network interface.
     * @param startTime Start of period. Defined in terms of "Unix time", see
     *            {@link java.lang.System#currentTimeMillis}.
     * @param endTime End of period. Defined in terms of "Unix time", see
     *            {@link java.lang.System#currentTimeMillis}.
     * @return Bucket object or null if permissions are insufficient or error happened during
     *         statistics collection.
     */
    public Bucket querySummaryForUser(int networkType, String subscriberId, long startTime,
            long endTime) throws SecurityException, RemoteException {
        NetworkTemplate template = createTemplate(networkType, subscriberId);
        if (template == null) {
            return null;
        }

        NetworkUsageStats stats;
        stats = new NetworkUsageStats(mContext, template, startTime, endTime);
        stats.startSummaryEnumeration(startTime, endTime);

        stats.close();
        return stats.getSummaryAggregate();
    }

    /**
     * Query network usage statistics summaries. Result filtered to include only uids belonging to
     * calling user. Result is aggregated over time, hence all buckets will have the same start and
     * end timestamps. Not aggregated over state or uid.
     *
     * @param networkType As defined in {@link ConnectivityManager}, e.g.
     *            {@link ConnectivityManager#TYPE_MOBILE}, {@link ConnectivityManager#TYPE_WIFI}
     *            etc.
     * @param subscriberId If applicable, the subscriber id of the network interface.
     * @param startTime Start of period. Defined in terms of "Unix time", see
     *            {@link java.lang.System#currentTimeMillis}.
     * @param endTime End of period. Defined in terms of "Unix time", see
     *            {@link java.lang.System#currentTimeMillis}.
     * @return Statistics object or null if permissions are insufficient or error happened during
     *         statistics collection.
     */
    public NetworkUsageStats querySummary(int networkType, String subscriberId, long startTime,
            long endTime) throws SecurityException, RemoteException {
        NetworkTemplate template = createTemplate(networkType, subscriberId);
        if (template == null) {
            return null;
        }

        NetworkUsageStats result;
        result = new NetworkUsageStats(mContext, template, startTime, endTime);
        result.startSummaryEnumeration(startTime, endTime);

        return result;
    }

    /**
     * Query network usage statistics details. Only usable for uids belonging to calling user.
     * Result is aggregated over state but not aggregated over time.
     *
     * @param networkType As defined in {@link ConnectivityManager}, e.g.
     *            {@link ConnectivityManager#TYPE_MOBILE}, {@link ConnectivityManager#TYPE_WIFI}
     *            etc.
     * @param subscriberId If applicable, the subscriber id of the network interface.
     * @param startTime Start of period. Defined in terms of "Unix time", see
     *            {@link java.lang.System#currentTimeMillis}.
     * @param endTime End of period. Defined in terms of "Unix time", see
     *            {@link java.lang.System#currentTimeMillis}.
     * @param uid UID of app
     * @return Statistics object or null if permissions are insufficient or error happened during
     *         statistics collection.
     */
    public NetworkUsageStats queryDetailsForUid(int networkType, String subscriberId,
            long startTime, long endTime, int uid) throws SecurityException, RemoteException {
        NetworkTemplate template = createTemplate(networkType, subscriberId);
        if (template == null) {
            return null;
        }

        NetworkUsageStats result;
        result = new NetworkUsageStats(mContext, template, startTime, endTime);
        result.startHistoryEnumeration(uid);

        return result;
    }

    /**
     * Query network usage statistics details. Result filtered to include only uids belonging to
     * calling user. Result is aggregated over state but not aggregated over time or uid.
     *
     * @param networkType As defined in {@link ConnectivityManager}, e.g.
     *            {@link ConnectivityManager#TYPE_MOBILE}, {@link ConnectivityManager#TYPE_WIFI}
     *            etc.
     * @param subscriberId If applicable, the subscriber id of the network interface.
     * @param startTime Start of period. Defined in terms of "Unix time", see
     *            {@link java.lang.System#currentTimeMillis}.
     * @param endTime End of period. Defined in terms of "Unix time", see
     *            {@link java.lang.System#currentTimeMillis}.
     * @return Statistics object or null if permissions are insufficient or error happened during
     *         statistics collection.
     */
    public NetworkUsageStats queryDetails(int networkType, String subscriberId, long startTime,
            long endTime) throws SecurityException, RemoteException {
        NetworkTemplate template = createTemplate(networkType, subscriberId);
        if (template == null) {
            return null;
        }
        NetworkUsageStats result;
        result = new NetworkUsageStats(mContext, template, startTime, endTime);
        result.startUserUidEnumeration();
        return result;
    }

    private static NetworkTemplate createTemplate(int networkType, String subscriberId) {
        NetworkTemplate template = null;
        switch (networkType) {
            case ConnectivityManager.TYPE_MOBILE: {
                template = NetworkTemplate.buildTemplateMobileAll(subscriberId);
                } break;
            case ConnectivityManager.TYPE_WIFI: {
                template = NetworkTemplate.buildTemplateWifiWildcard();
                } break;
            default: {
                Log.w(TAG, "Cannot create template for network type " + networkType
                        + ", subscriberId '" + NetworkIdentity.scrubSubscriberId(subscriberId) +
                        "'.");
            }
        }
        return template;
    }
}
