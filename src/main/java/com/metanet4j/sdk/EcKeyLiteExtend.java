package com.metanet4j.sdk;

import com.metanet4j.base.bsv.BsvHashBytes;
import com.metanet4j.bsv.compat.BSM;
import com.metanet4j.bsv.primitives.ECDSA;
import com.metanet4j.bsv.primitives.PrivateKey;
import com.metanet4j.bsv.primitives.PublicKey;
import com.metanet4j.bsv.primitives.Signature;
import com.metanet4j.bsv.primitives.Utils;
import java.math.BigInteger;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * only for BSM sign message
 *
 * <p>原实现继承旧库 {@code ECKeyLite} 以绕开其 signMessage 在 Sigma 消息哈希上的签名不一致。
 * 现改为基于新 SDK（com.metanet4j.bsv）的独立实现：签名走 {@code ECDSA.sign}，
 * compact 编码走 {@code Signature.toCompact(recovery, compressed, "base64")}，
 * 与旧实现产出的「1 字节头 + r + s」Base64 格式一致（V05 固定样本与奇偶校验核对）。
 */
public class EcKeyLiteExtend {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final boolean compressed;

    /** 由私钥与公钥构造；仅验签场景可只传公钥。 */
    public EcKeyLiteExtend(@Nullable BigInteger priv, @Nullable PublicKey pub) {
        this(priv == null ? null : fromScalar(priv), pub, true);
    }

    private EcKeyLiteExtend(@Nullable PrivateKey priv, @Nullable PublicKey pub, boolean compressed) {
        if (priv == null && pub == null) {
            throw new IllegalArgumentException("私钥与公钥不能同时为空");
        }
        this.compressed = compressed;
        this.privateKey = priv;
        this.publicKey = pub != null ? pub : priv.toPublicKey();
    }

    private static PrivateKey fromScalar(BigInteger scalar) {
        return PrivateKey.fromString(scalar.toString(16), 16);
    }

    /** 由私钥标量构造。 */
    public static EcKeyLiteExtend fromPrivate(BigInteger privKey) {
        return new EcKeyLiteExtend(fromScalar(privKey), null, true);
    }

    /** 由新 SDK 私钥构造。 */
    public static EcKeyLiteExtend fromPrivateKey(PrivateKey key) {
        return new EcKeyLiteExtend(key, key.toPublicKey(), true);
    }

    /** 由公钥构造（仅验签）。 */
    public static EcKeyLiteExtend fromPublicOnly(PublicKey key) {
        return new EcKeyLiteExtend(null, key, true);
    }

    /** 对 32 字节摘要签名，返回 Base64 compact（65 字节）签名。 */
    public String signHash(BsvHashBytes hash) {
        return signHash(hash.getBytes());
    }

    /** 对摘要字节签名，返回 Base64 compact 签名。 */
    public String signHash(byte[] digest) {
        requirePrivate();
        com.metanet4j.bsv.primitives.BigNumber messageHash =
                new com.metanet4j.bsv.primitives.BigNumber(Utils.toArray(digest));
        Signature signature = ECDSA.sign(messageHash, privateKey, true);
        int recovery = signature.CalculateRecoveryFactor(publicKey, messageHash);
        return (String) signature.toCompact(recovery, compressed, "base64");
    }

    /** BSM 消息签名（前缀 + CompactSize 长度 + 消息体，双 SHA-256）。 */
    public String signMessage(byte[] messageBytes) {
        return signMessage(messageBytes, null);
    }

    /**
     * BSM 消息签名。
     *
     * <p>{@code aesKey} 在新 SDK 中无对应能力（私钥不再有加密态），保留参数仅为兼容调用方。
     */
    public String signMessage(byte[] messageBytes, @Nullable Object aesKey) {
        requirePrivate();
        return BSM.sign(toList(messageBytes), privateKey);
    }

    /** 底层新 SDK 私钥；仅验签对象时为 {@code null}。 */
    @Nullable
    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    /** 公钥。 */
    public PublicKey getPublicKey() {
        return publicKey;
    }

    /** 压缩公钥字节。 */
    public byte[] getPubKey() {
        return toBytes(publicKey.toDER());
    }

    /** 公钥哈希（压缩编码的 hash160）。 */
    public byte[] getPubKeyHash() {
        return toBytes(publicKey.toHash());
    }

    /** 压缩公钥 hex。 */
    public String getPublicKeyAsHex() {
        return Utils.toHex(publicKey.toDER());
    }

    /** 是否压缩公钥。 */
    public boolean isCompressed() {
        return compressed;
    }

    private void requirePrivate() {
        if (privateKey == null) {
            throw new IllegalStateException("该实例只有公钥，无法签名");
        }
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
