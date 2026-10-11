package com.metanet4j.sdk;

import com.metanet4j.base.bsv.BsvHashBytes;
import com.metanet4j.base.bsv.BsvNetwork;
import com.metanet4j.bsv.primitives.Utils;
import com.metanet4j.sdk.exception.InvalidKeyException;
import java.math.BigInteger;
import java.util.List;

/**
 * 业务私钥包装：以新 SDK 的 {@code PrivateKey} 为底层，保留 WIF 解析、网络判定与签名入口。
 *
 * <p>原实现包装旧库 {@code ECKeyLite} 并以 {@code NetworkParameters} 表达网络；
 * 现统一为新 SDK 私钥与业务网络值类型 {@link BsvNetwork}。
 * 加密私钥态（KeyCrypter／AES）在新 SDK 中不存在，相关入口随之移除。
 */
public class PrivateKey {

    private final com.metanet4j.bsv.primitives.PrivateKey key;
    private final boolean compressed;
    private final BsvNetwork networkType;

    /** 以主网、压缩公钥构造。 */
    public PrivateKey(com.metanet4j.bsv.primitives.PrivateKey key) {
        this(key, true, BsvNetwork.MAINNET);
    }

    public PrivateKey(com.metanet4j.bsv.primitives.PrivateKey key, boolean isCompressed,
                      BsvNetwork networkType) {
        if (key == null) {
            throw new IllegalArgumentException("私钥不能为空");
        }
        this.key = key;
        this.compressed = isCompressed;
        this.networkType = networkType == null ? BsvNetwork.MAINNET : networkType;
    }

    /**
     * 解析 WIF：长度 51（非压缩）或 52（压缩），由首字符判定网络，解压后校验压缩标记。
     *
     * @throws InvalidKeyException 长度、首字符、压缩标记非法
     */
    public static PrivateKey fromWIF(String wif) throws InvalidKeyException {
        if (wif == null) {
            throw new InvalidKeyException("WIF 不能为空");
        }
        if (wif.length() != 51 && wif.length() != 52) {
            throw new InvalidKeyException("Valid keys are either 51 or 52 bytes in length");
        }

        BsvNetwork networkType = decodeNetworkType(wif);
        boolean isCompressed = wif.length() == 52;

        List<Double> payload;
        try {
            payload = Utils.fromBase58(wif);
            // 校验校验和：Base58Check 的末 4 字节必须匹配双 SHA-256 前缀
            List<Double> body = payload.subList(0, payload.size() - 4);
            List<Double> checksum = payload.subList(payload.size() - 4, payload.size());
            List<Double> expected = com.metanet4j.bsv.primitives.Hash.hash256(body, null)
                    .subList(0, 4);
            if (!expected.equals(checksum)) {
                throw new InvalidKeyException("WIF 校验和不匹配：" + wif);
            }
            // 后续的版本字节与压缩标记检查都必须针对**去掉校验和**的载荷，
            // 否则会把校验和末字节误当成压缩标记（曾导致压缩 WIF 被误判为非法）。
            payload = body;
        } catch (RuntimeException e) {
            throw new InvalidKeyException("WIF 解码失败：" + wif, e);
        }
        // 版本字节 + 32 字节私钥（+ 压缩标记），长度由首字符规则保证
        if (payload.size() < 33) {
            throw new InvalidKeyException("WIF 载荷长度非法：" + payload.size());
        }
        if (payload.get(0).intValue() != networkType.getDumpedPrivateKeyHeader()) {
            throw new InvalidKeyException("WIF 版本字节与网络不符：0x"
                    + Integer.toHexString(payload.get(0).intValue()));
        }
        if (isCompressed && payload.get(payload.size() - 1).intValue() != 0x01) {
            throw new InvalidKeyException("Compressed keys must have last byte set as 0x01. Yours is ["
                    + payload.get(payload.size() - 1).intValue() + "]");
        }

        List<Double> keyBytes = payload.subList(1, 33);
        com.metanet4j.bsv.primitives.PrivateKey parsed =
                com.metanet4j.bsv.primitives.PrivateKey.fromString(
                        Utils.toHex(keyBytes), 16);
        return new PrivateKey(parsed, isCompressed, networkType);
    }

    /** 由私钥标量构造（默认主网、压缩）。 */
    public static PrivateKey of(BigInteger scalar) {
        return new PrivateKey(com.metanet4j.bsv.primitives.PrivateKey.fromString(
                scalar.toString(16), 16), true, BsvNetwork.MAINNET);
    }

    /** 由 32 字节私钥材料构造（默认主网、压缩）。 */
    public static PrivateKey of(byte[] keyBytes) {
        return new PrivateKey(com.metanet4j.bsv.primitives.PrivateKey.fromString(
                com.metanet4j.bsv.primitives.Utils.toHex(keyBytes), 16), true, BsvNetwork.MAINNET);
    }

    /** 由新 SDK 私钥构造（默认主网、压缩）。 */
    public static PrivateKey of(com.metanet4j.bsv.primitives.PrivateKey key) {
        return new PrivateKey(key, true, BsvNetwork.MAINNET);
    }

    /** 导出 WIF。 */
    public String toWif(BsvNetwork network) {
        if (compressed) {
            return (String) key.toWif(List.of(network.getDumpedPrivateKeyHeader()));
        }
        // 新 SDK 的 PrivateKey.toWif 固定追加 0x01（只产出压缩 WIF），
        // 故非压缩 WIF 需自行按 Base58Check 组装：版本字节 + 32 字节私钥（不追加 0x01）。
        List<Number> data = new java.util.ArrayList<>(key.toArray("be", 32));
        return Utils.toBase58Check(data, List.of(network.getDumpedPrivateKeyHeader()));
    }

    /** 当前网络。 */
    public BsvNetwork getNetworkType() {
        return networkType;
    }

    /** 是否压缩公钥。 */
    public boolean isCompressed() {
        return compressed;
    }

    /** 业务公钥包装。 */
    public PublicKey getPublicKey() {
        return PublicKey.of(key.toPublicKey());
    }

    /** 底层新 SDK 私钥。 */
    public com.metanet4j.bsv.primitives.PrivateKey getKey() {
        return key;
    }

    /** 私钥标量。 */
    public BigInteger getPrivKey() {
        return new BigInteger(1, toBytes(key.toArray("be", 32)));
    }

    /** 私钥字节（32 字节）。 */
    public byte[] getPrivKeyBytes() {
        return toBytes(key.toArray("be", 32));
    }

    /** 公钥字节。 */
    public byte[] getPubKey() {
        return toBytes(key.toPublicKey().toDER());
    }

    /** 同构的新 SDK 私钥（对应旧的 transformEcKey 语义）。 */
    public com.metanet4j.bsv.primitives.PrivateKey transformEcKey() {
        return key;
    }

    /** BSM 签名包装（对应旧的 transformEcKeyLiteExtend 语义）。 */
    public EcKeyLiteExtend transformEcKeyLiteExtend() {
        return EcKeyLiteExtend.fromPrivateKey(key);
    }

    /** 对摘要签名。 */
    public com.metanet4j.bsv.primitives.Signature sign(BsvHashBytes hash) {
        return com.metanet4j.bsv.primitives.ECDSA.sign(
                new com.metanet4j.bsv.primitives.BigNumber(Utils.toArray(hash.getBytes())), key, true);
    }

    private static BsvNetwork decodeNetworkType(String wifKey) throws InvalidKeyException {
        switch (wifKey.charAt(0)) {
            case '5': {
                if (wifKey.length() != 51) {
                    throw new InvalidKeyException("Uncompressed private keys have a length of 51 bytes");
                }
                return BsvNetwork.MAINNET;
            }
            case '9': {
                if (wifKey.length() != 51) {
                    throw new InvalidKeyException("Uncompressed private keys have a length of 51 bytes");
                }
                return BsvNetwork.TESTNET3;
            }
            case 'L':
            case 'K': {
                return BsvNetwork.MAINNET;
            }
            case 'c': {
                return BsvNetwork.TESTNET3;
            }
            default: {
                throw new InvalidKeyException(
                        "AddressEnhance WIF format must start with either [5] , [9], [L], [K] or [c]");
            }
        }
    }

    private static byte[] toBytes(List<?> values) {
        byte[] bytes = new byte[values.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ((Number) values.get(i)).intValue();
        }
        return bytes;
    }
}
