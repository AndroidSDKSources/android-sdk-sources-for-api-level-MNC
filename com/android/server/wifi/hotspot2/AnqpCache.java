package com.android.server.wifi.hotspot2;

import android.util.Log;

import com.android.server.wifi.anqp.ANQPElement;
import com.android.server.wifi.anqp.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnqpCache implements AlarmHandler {
    private static final long CACHE_RECHECK = 60000L;
    private static final boolean STANDARD_ESS = true;  // Regular AP keying; see CacheKey below.

    private final HashMap<CacheKey, ANQPData> mANQPCache;
    private final Chronograph mChronograph;

    public AnqpCache(Chronograph chronograph) {
        mANQPCache = new HashMap<>();
        mChronograph = chronograph;
        mChronograph.addAlarm(CACHE_RECHECK, this, null);
    }

    private static class CacheKey {
        private final String mSSID;
        private final long mBSSID;
        private final long mHESSID;

        private CacheKey(String ssid, long bssid, long hessid) {
            mSSID = ssid;
            mBSSID = bssid;
            mHESSID = hessid;
        }

        /**
         * Build an ANQP cache key suitable for the granularity of the key space as follows:
         *
         * HESSID   domainID    standardESS     Key content Rationale
         * -------- ----------- --------------- ----------- --------------------
         * n/a      zero        n/a             SSID/BSSID  Domain ID indicates unique AP info
         * not set  set         false           SSID/BSSID  Strict per AP keying override
         * not set  set         true            SSID        Standard definition of an ESS
         * set      set         n/a             HESSID      The ESS is defined by the HESSID
         *
         * @param network The network to build the key for.
         * @param standardESS If this parameter is set the "standard" paradigm for an ESS is used
         *                    for the cache, i.e. all APs with identical SSID is considered an ESS,
         *                    otherwise caching is performed per AP.
         * @return A CacheKey.
         */
        private static CacheKey buildKey(NetworkDetail network, boolean standardESS) {
            String ssid;
            long bssid;
            long hessid;
            if (network.getAnqpDomainID() == 0L || (network.getHESSID() == 0L && !standardESS)) {
                ssid = network.getSSID();
                bssid = network.getBSSID();
                hessid = 0L;
            }
            else if (network.getHESSID() != 0L && network.getAnqpDomainID() > 0) {
                ssid = null;
                bssid = 0L;
                hessid = network.getHESSID();
            }
            else {
                ssid = network.getSSID();
                bssid = 0L;
                hessid = 0L;
            }

            return new CacheKey(ssid, bssid, hessid);
        }

        @Override
        public int hashCode() {
            if (mHESSID != 0) {
                return (int)((mHESSID >>> 32) * 31 + mHESSID);
            }
            else if (mBSSID != 0) {
                return (int)((mSSID.hashCode() * 31 + (mBSSID >>> 32)) * 31 + mBSSID);
            }
            else {
                return mSSID.hashCode();
            }
        }

        @Override
        public boolean equals(Object thatObject) {
            if (thatObject == this) {
                return true;
            }
            else if (thatObject == null || thatObject.getClass() != CacheKey.class) {
                return false;
            }
            CacheKey that = (CacheKey) thatObject;
            return Utils.compare(that.mSSID, mSSID) == 0 &&
                    that.mBSSID == mBSSID &&
                    that.mHESSID == mHESSID;
        }

        @Override
        public String toString() {
            if (mHESSID != 0L) {
                return "HESSID:" + NetworkDetail.toMACString(mHESSID);
            }
            else if (mBSSID != 0L) {
                return NetworkDetail.toMACString(mBSSID) +
                        ":<" + Utils.toUnicodeEscapedString(mSSID) + ">";
            }
            else {
                return '<' + Utils.toUnicodeEscapedString(mSSID) + '>';
            }
        }
    }

    public boolean initiate(NetworkDetail network) {

        long now = System.currentTimeMillis();
        CacheKey key = CacheKey.buildKey(network, STANDARD_ESS);

        synchronized (mANQPCache) {
            ANQPData data = mANQPCache.get(key);
            if (data == null ||
                    (data.isResolved() && data.getDomainID() != network.getAnqpDomainID()) ||
                    data.expendable(now)) {
                mANQPCache.put(key, new ANQPData(network, null));
                return true;
            }
            else {
                return false;
            }
        }
    }

    public void update(NetworkDetail network,
                       Map<Constants.ANQPElementType, ANQPElement> anqpElements) {

        long now = System.currentTimeMillis();
        CacheKey key = CacheKey.buildKey(network, STANDARD_ESS);

        // Networks with a 0 ANQP Domain ID are still cached, but with a very short expiry, just
        // long enough to prevent excessive re-querying.
        synchronized (mANQPCache) {
            ANQPData data = mANQPCache.get(key);
            if (data == null ||
                    !data.isResolved() ||
                    data.getDomainID() != network.getAnqpDomainID() ||
                    data.recacheable(now)) {
                Log.d(Utils.hs2LogTag(getClass()), "Updating " + key + " -> " + data);
                data = new ANQPData(network, anqpElements);
                mANQPCache.put(key, data);
            }
            else {
                Log.d(Utils.hs2LogTag(getClass()), "Not recaching " + key + " -> " + data);
            }
        }
    }

    public ANQPData getEntry(NetworkDetail network) {
        ANQPData data;

        CacheKey key = CacheKey.buildKey(network, STANDARD_ESS);
        synchronized (mANQPCache) {
            data = mANQPCache.get(key);
        }

        long now = System.currentTimeMillis();

        if (data == null ||
                !data.isResolved() ||
                data.getDomainID() != network.getAnqpDomainID() ||
                data.expired(now)) {
            return null;
        }
        return data;
    }

    public void clear() {
        synchronized (mANQPCache) {
            mANQPCache.clear();
        }
    }

    @Override
    public void wake(Object token) {
        long now = System.currentTimeMillis();
        synchronized (mANQPCache) {
            List<CacheKey> regulars = new ArrayList<>();
            for (Map.Entry<CacheKey, ANQPData> entry : mANQPCache.entrySet()) {
                if (entry.getValue().expired(now)) {
                    regulars.add(entry.getKey());
                }
            }
            for (CacheKey key : regulars) {
                mANQPCache.remove(key);
                Log.d(Utils.hs2LogTag(getClass()), "Retired " + key);
            }
        }
        mChronograph.addAlarm(CACHE_RECHECK, this, null);
    }
}
