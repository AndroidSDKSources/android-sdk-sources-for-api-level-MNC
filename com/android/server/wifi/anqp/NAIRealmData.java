package com.android.server.wifi.anqp;

import com.android.server.wifi.anqp.eap.EAPMethod;
import com.android.server.wifi.hotspot2.AuthMatch;

import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The NAI Realm Data ANQP sub-element, IEEE802.11-2012 section 8.4.4.10 figure 8-418
 */
public class NAIRealmData {
    private final List<String> mRealms;
    private final List<EAPMethod> mEAPMethods;

    public NAIRealmData(ByteBuffer payload) throws ProtocolException {
        if (payload.remaining() < 5) {
            throw new ProtocolException("Runt payload: " + payload.remaining());
        }

        int length = payload.getShort() & Constants.SHORT_MASK;
        if (length > payload.remaining()) {
            throw new ProtocolException("Invalid data length: " + length);
        }
        boolean utf8 = (payload.get() & 1) == Constants.UTF8_INDICATOR;

        String realm = Constants.getPrefixedString(payload, 1, utf8 ?
                StandardCharsets.UTF_8 :
                StandardCharsets.US_ASCII);
        String[] realms = realm.split(";");
        mRealms = new ArrayList<String>();
        for (String realmElement : realms) {
            if (realmElement.length() > 0) {
                mRealms.add(realmElement);
            }
        }

        int methodCount = payload.get() & Constants.BYTE_MASK;
        mEAPMethods = new ArrayList<EAPMethod>(methodCount);
        while (methodCount > 0) {
            mEAPMethods.add(new EAPMethod(payload));
            methodCount--;
        }
    }

    public List<String> getRealms() {
        return Collections.unmodifiableList(mRealms);
    }

    public List<EAPMethod> getEAPMethods() {
        return Collections.unmodifiableList(mEAPMethods);
    }

    public AuthMatch matchEAPMethods(EAPMethod refMethod) {
        if (mEAPMethods.isEmpty()) {
            return AuthMatch.RealmOnly;
        }

        AuthMatch best = AuthMatch.None;
        AuthMatch exitCondition = AuthMatch.values()[AuthMatch.values().length-1];

        for (EAPMethod method : mEAPMethods) {
            AuthMatch match = method.matchAuthParams(refMethod);
            if (match.compareTo(best) > 0) {
                best = match;
                if (match == exitCondition) {
                    return match;
                }
            }
        }
        return best;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("  NAI Realm(s)");
        for (String realm : mRealms) {
            sb.append(' ').append(realm);
        }
        sb.append('\n');

        for (EAPMethod eapMethod : mEAPMethods) {
            sb.append( "    " ).append(eapMethod.toString());
        }
        return sb.toString();
    }
}
