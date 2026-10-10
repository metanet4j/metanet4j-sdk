package com.metanet4j.sdk.utils;

import com.metanet4j.bsv.script.Script;
import com.metanet4j.sdk.PrivateKey;

/**
 * 输入可花费信息：该输入所需的签名私钥与（P2SH 场景的）赎回脚本。
 *
 * <p>取代旧库的 {@code RedeemData} 与 {@code RedeemDataExtend}。按迁移计划 §3，
 * 旧实现「按公钥哈希在 {@code KeyBag} 中反查密钥」的能力不存在于新 SDK，
 * 因此业务侧改为**显式提供**：调用方通过
 * {@link com.metanet4j.sdk.bap.SigningKeyProvider} 或 BAP 派生规则取得私钥后构造本对象。
 */
public final class RedeemDataExtend {

    /** 可用于签名的私钥。 */
    public final PrivateKey key;
    /** P2SH 场景的赎回脚本；非 P2SH 为 {@code null}。 */
    public final Script redeemScript;

    public RedeemDataExtend(PrivateKey key, Script redeemScript) {
        this.key = key;
        this.redeemScript = redeemScript;
    }

    /** 单密钥构造（P2PKH／P2PK／Ordinals 铭文输入）。 */
    public static RedeemDataExtend of(PrivateKey key, Script redeemScript) {
        return new RedeemDataExtend(key, redeemScript);
    }

    /** 返回私钥；缺省为 {@code null}。 */
    public PrivateKey getFullKey() {
        return key;
    }

    /** 压缩公钥字节；无私钥时返回 {@code null}。 */
    public byte[] getPubKeyBytes() {
        return key == null ? null : toBytes(key.getPubKey());
    }

    /** 私钥的 hex，便于断言。 */
    public String getKeyHex() {
        return key == null ? null : key.getKey().toHex();
    }

    private static byte[] toBytes(java.util.List<?> values) {
        byte[] bytes = new byte[values.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ((Number) values.get(i)).intValue();
        }
        return bytes;
    }
}
