package com.android.server.wifi.anqp;

import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.android.server.wifi.anqp.Constants.BYTES_IN_SHORT;
import static com.android.server.wifi.anqp.Constants.SHORT_MASK;

/**
 * The NAI Realm ANQP Element, IEEE802.11-2012 section 8.4.4.10
 */
public class NAIRealmElement extends ANQPElement {
    private final List<NAIRealmData> mRealmData;

    public NAIRealmElement(Constants.ANQPElementType infoID, ByteBuffer payload)
            throws ProtocolException {
        super(infoID);

        if (!payload.hasRemaining()) {
            mRealmData = Collections.emptyList();
            return;
        }

        if (payload.remaining() < BYTES_IN_SHORT) {
            throw new ProtocolException("Runt NAI Realm: " + payload.remaining());
        }

        int count = payload.getShort() & SHORT_MASK;
        mRealmData = new ArrayList<NAIRealmData>(count);
        while (count > 0) {
            mRealmData.add(new NAIRealmData(payload));
            count--;
        }
    }

    public List<NAIRealmData> getRealmData() {
        return Collections.unmodifiableList(mRealmData);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("NAI Realm:\n");
        for (NAIRealmData data : mRealmData) {
            sb.append(data);
        }
        return sb.toString();
    }
}
