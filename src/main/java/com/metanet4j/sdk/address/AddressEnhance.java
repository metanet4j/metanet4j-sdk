package com.metanet4j.sdk.address;

import com.metanet4j.base.bsv.BsvAddress;
import com.metanet4j.base.bsv.BsvNetwork;
import com.metanet4j.sdk.PrivateKey;
import com.metanet4j.sdk.PublicKey;

/**
 * 业务地址：以 base 的 {@link BsvAddress} 为底层，保留原有构造与工厂入口。
 *
 * <p>原实现继承旧库 {@code AddressLite}；现改为组合，公开方法
 * {@link #toBase58()}、{@link #getHash160()}、{@link #getNetwork()}、{@link #isP2SHAddress()}
 * 语义不变。网络参数类型由 {@code NetworkParameters} 换为 {@link BsvNetwork}，
 * 跨网络解析与非法编码以 {@link IllegalArgumentException} 表达（旧库为受检异常）。
 */
public class AddressEnhance {

    private final BsvAddress address;

    protected AddressEnhance(BsvAddress address) {
        this.address = address;
    }

    /** 由既有地址包装（保持原构造入口语义）。 */
    public AddressEnhance(AddressEnhance other) {
        this(other.address);
    }

    public AddressEnhance(BsvNetwork network, byte[] hash160) {
        this(BsvAddress.fromPubKeyHash(network, hash160));
    }

    /** 以主网构造。 */
    public AddressEnhance(byte[] hash160) {
        this(BsvNetwork.MAINNET, hash160);
    }

    /** 指定版本字节构造（P2PKH 或 P2SH）。 */
    public AddressEnhance(BsvNetwork network, int version, byte[] hash160) {
        this(version == network.getP2SHHeader()
                ? BsvAddress.fromScriptHash(network, hash160)
                : BsvAddress.fromPubKeyHash(network, hash160));
    }

    /** 解析 base58 地址并由版本字节推断网络。 */
    public static AddressEnhance fromBase58(String address) {
        return new AddressEnhance(BsvAddress.fromBase58(address));
    }

    /** 解析 base58 地址并校验网络。 */
    public static AddressEnhance fromBase58(BsvNetwork network, String address) {
        return new AddressEnhance(BsvAddress.fromBase58(network, address));
    }

    public static AddressEnhance fromPubKeyHash(byte[] hash160) {
        return fromPubKeyHash(BsvNetwork.MAINNET, hash160);
    }

    public static AddressEnhance fromPubKeyHash(BsvNetwork network, byte[] hash160) {
        return new AddressEnhance(network, network.getAddressHeader(), hash160);
    }

    public static AddressEnhance fromPrivateKey(PrivateKey privateKey) {
        return fromPublicKey(BsvNetwork.MAINNET, privateKey.getPublicKey());
    }

    public static AddressEnhance fromPublicKey(PublicKey key) {
        return fromPublicKey(BsvNetwork.MAINNET, key);
    }

    public static AddressEnhance fromPublicKey(BsvNetwork network, PublicKey key) {
        return fromPubKeyHash(network, key.getPubKeyHash());
    }

    /** 由新 SDK 公钥构造。 */
    public static AddressEnhance fromPublicKey(BsvNetwork network,
                                               com.metanet4j.bsv.primitives.PublicKey key) {
        return fromPubKeyHash(network, toBytes(key.toHash()));
    }

    /** 由新 SDK 私钥构造。 */
    public static AddressEnhance fromPrivateKey(BsvNetwork network,
                                                com.metanet4j.bsv.primitives.PrivateKey key) {
        return fromPublicKey(network, key.toPublicKey());
    }

    /** 由业务私钥构造（原 {@code fromECkeyLite} 的替代）。 */
    public static AddressEnhance fromECkeyLite(PrivateKey key) {
        return fromPublicKey(BsvNetwork.MAINNET, key.getPublicKey());
    }

    /** 由公钥构造（原 {@code fromECkey} 的替代）。 */
    public static AddressEnhance fromECkey(PublicKey key) {
        return fromPublicKey(BsvNetwork.MAINNET, key);
    }

    /** 网络。 */
    public BsvNetwork getNetwork() {
        return address.getNetwork();
    }

    /** 版本字节。 */
    public int getVersion() {
        return address.getVersion();
    }

    /** hash160 副本。 */
    public byte[] getHash160() {
        return address.getHash160();
    }

    /** 是否 P2SH 地址。 */
    public boolean isP2SHAddress() {
        return address.isP2SHAddress();
    }

    /** base58 编码。 */
    public String toBase58() {
        return address.toBase58();
    }

    /** 底层 base 地址。 */
    public BsvAddress toBsvAddress() {
        return address;
    }

    @Override
    public String toString() {
        return address.toBase58();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AddressEnhance value && value.address.equals(address);
    }

    @Override
    public int hashCode() {
        return address.hashCode();
    }

    private static byte[] toBytes(java.util.List<?> values) {
        byte[] bytes = new byte[values.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ((Number) values.get(i)).intValue();
        }
        return bytes;
    }
}
