

package com.metanet4j.sdk.utils;


/**
 * 定长字节流读取器（取代旧库 {@code ProtocolException} 与 {@code TxParams} 依赖）。
 *
 * <p>长度上限沿用旧库 {@code TxParams.MAX_TRANSACTION_SIZE_PARAM} 的默认语义（32 MiB），
 * 用于拒绝明显异常的长度声明。
 */
public class ReadUtils {

    /** 单次可读的最大长度（旧库交易大小参数默认值）。 */
    private static final int MAX_TRANSACTION_SIZE = 32 * 1024 * 1024;


    private final byte[] payload;
    private int cursor;

    public ReadUtils(byte[] payload){
        this.payload = payload;
        cursor = 0;
    }

    private void checkReadLength(int length) {
        if (length < 0 || length > MAX_TRANSACTION_SIZE || length > payload.length - cursor) {
            throw new IllegalArgumentException("Claimed value length too large: " + length);
        }
    }

    public byte[] readBytes(int length) {
        checkReadLength(length);
        byte[] bytes = new byte[length];
        System.arraycopy(payload, cursor, bytes, 0, length);
        cursor += length;
        return bytes;
    }

    public byte readByte() {
        checkReadLength(1);
        return payload[cursor++];
    }


}
