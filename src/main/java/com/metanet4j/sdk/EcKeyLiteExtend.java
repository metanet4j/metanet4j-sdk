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

    /**
     * 由私钥标量构造**非压缩**公钥实例（旧 {@code ECKeyLite.fromPrivate(priv, false)} 的等价入口）。
     *
     * <p>局限：新 SDK 的 {@code PublicKey} 未公开非压缩编码入口（{@code toDER()} 恒为压缩），
     * 因此本实例的 {@code compressed=false} 只作用于 **compact 签名头**与恢复校验；
     * {@code getPubKey()}／{@code getPublicKeyAsHex()} 仍返回压缩编码。该局限已在
     * evidence/bsv-replace/r3-keys-20261010/migration-rules.md §21 登记。
     */
    public static EcKeyLiteExtend fromPrivateUncompressed(BigInteger privKey) {
        return new EcKeyLiteExtend(fromScalar(privKey), null, false);
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

    /**
     * 校验 65 字节 compact 签名是否由本实例公钥对给定摘要签署。
     *
     * <p>取代旧 {@code ECKeyLite.verify(hash, ECDSASignature)}：新 SDK 用
     * {@code PublicKey.fromMsgHashAndCompactSignature} 从 compact 签名恢复公钥再比对。
     */
    public boolean verify(BsvHashBytes hash, byte[] compactSignature) {
        if (compactSignature == null || compactSignature.length != 65) {
            return false;
        }
        try {
            com.metanet4j.bsv.primitives.BigNumber messageHash =
                    new com.metanet4j.bsv.primitives.BigNumber(Utils.toArray(hash.getBytes()));
            PublicKey recovered = PublicKey.fromMsgHashAndCompactSignature(messageHash, compactSignature);
            return java.util.Arrays.equals(toBytes(recovered.toDER()), toBytes(publicKey.toDER()));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 校验 Base64 compact 签名。 */
    public boolean verify(BsvHashBytes hash, String base64CompactSignature) {
        return base64CompactSignature != null
                && verify(hash, java.util.Base64.getDecoder().decode(base64CompactSignature));
    }

    /** 对摘要字节签名，返回 Base64 compact 签名。 */
    public String signHash(byte[] digest) {
        requirePrivate();
        requireConsistentKeys();
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
        requireConsistentKeys();
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

    /**
     * 私钥与显式传入的公钥必须一致，否则签名不可恢复。
     *
     * <p>保留旧实现的「recoverable key」表述，使既有契约断言继续可观测。
     */
    private void requireConsistentKeys() {
        PublicKey derived = privateKey.toPublicKey();
        if (!java.util.Arrays.equals(toBytes(publicKey.toDER()), toBytes(derived.toDER()))) {
            throw new IllegalStateException("private key does not match the recoverable key");
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
