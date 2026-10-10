package com.metanet4j.sdk;

import com.metanet4j.bsv.primitives.Utils;
import java.util.List;

/**
 * 业务公钥包装：以新 SDK 的 {@code PublicKey} 为底层，保留原有字节／hex 访问入口。
 *
 * <p>原实现包装旧库 {@code ECKeyLite}；现统一为新 SDK（com.metanet4j.bsv）。
 * 公钥哈希为压缩编码的 hash160，与旧实现一致（V02／V05 样本核对）。
 */
public class PublicKey {

    private final com.metanet4j.bsv.primitives.PublicKey key;

    private PublicKey(com.metanet4j.bsv.primitives.PublicKey key) {
        this.key = key;
    }

    /** 由公钥 hex（压缩或非压缩）构造。 */
    public static PublicKey fromHex(String encoded) {
        return new PublicKey(com.metanet4j.bsv.primitives.PublicKey.fromString(encoded));
    }

    /** 由公钥字节构造。 */
    public static PublicKey fromBytes(byte[] pubkeyBytes) {
        return new PublicKey(com.metanet4j.bsv.primitives.PublicKey.fromDER(toList(pubkeyBytes)));
    }

    /** 包装新 SDK 公钥。 */
    public static PublicKey of(com.metanet4j.bsv.primitives.PublicKey publicKey) {
        return new PublicKey(publicKey);
    }

    /** 公钥哈希（压缩编码的 hash160）。 */
    public byte[] getPubKeyHash() {
        return toBytes(key.toHash());
    }

    /** 压缩公钥字节。 */
    public byte[] getPubKeyBytes() {
        return toBytes(key.toDER());
    }

    /** 压缩公钥 hex。 */
    public String getPubKeyHex() {
        return Utils.toHex(key.toDER());
    }

    /** 底层新 SDK 公钥。 */
    public com.metanet4j.bsv.primitives.PublicKey getPublicKey() {
        return key;
    }

    private static List<Double> toList(byte[] data) {
        Double[] values = new Double[data.length];
        for (int i = 0; i < data.length; i++) {
            values[i] = (double) (data[i] & 0xFF);
        }
        return List.of(values);
    }

    private static byte[] toBytes(List<?> values) {
        byte[] bytes = new byte[values.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ((Number) values.get(i)).intValue();
        }
        return bytes;
    }
}
