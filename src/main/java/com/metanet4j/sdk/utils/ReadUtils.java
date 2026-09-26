

package com.metanet4j.sdk.utils;


import io.bitcoinsv.bitcoinjsv.core.ProtocolException;
import io.bitcoinsv.bitcoinjsv.params.TxParams;

public class ReadUtils {


    private final byte[] payload;
    private int cursor;

    public ReadUtils(byte[] payload){
        this.payload = payload;
        cursor = 0;
    }

    private void checkReadLength(int length) throws ProtocolException {
        if (length < 0 || length > TxParams.MAX_TRANSACTION_SIZE_PARAM || length > payload.length - cursor) {
            throw new ProtocolException("Claimed value length too large: " + length);
        }
    }

    public byte[] readBytes(int length) throws ProtocolException {
        checkReadLength(length);
        byte[] bytes = new byte[length];
        System.arraycopy(payload, cursor, bytes, 0, length);
        cursor += length;
        return bytes;
    }

    public byte readByte() throws ProtocolException {
        checkReadLength(1);
        return payload[cursor++];
    }


}
