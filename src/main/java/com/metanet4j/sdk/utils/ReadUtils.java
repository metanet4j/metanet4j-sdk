

package com.metanet4j.sdk.utils;


import io.bitcoinsv.bitcoinjsv.core.ProtocolException;
import io.bitcoinsv.bitcoinjsv.params.TxParams;

public class ReadUtils {


    private byte[] payload;
    private int cursor;

    public ReadUtils(byte[] payload){
        this.payload = payload;
        cursor = 0;
    }

    private void checkReadLength(int length) throws ProtocolException {
        if ((length > TxParams.MAX_TRANSACTION_SIZE_PARAM) || (cursor + length > payload.length)) {
            throw new ProtocolException("Claimed value length too large: " + length);
        }
    }

    public byte[] readBytes(int length) throws ProtocolException {
        checkReadLength(length);
        try {
            byte[] b = new byte[length];
            System.arraycopy(payload, cursor, b, 0, length);
            cursor += length;
            return b;
        } catch (IndexOutOfBoundsException e) {
            throw new ProtocolException(e);
        }
    }

    public byte readByte() throws ProtocolException {
        checkReadLength(1);
        return payload[cursor++];
    }


}
