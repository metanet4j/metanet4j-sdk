package com.metanet4j.sdk.bap;

import com.metanet4j.bsv.primitives.Hash;
import com.metanet4j.bsv.primitives.Utils;
import com.metanet4j.sdk.PrivateKey;
import java.util.List;

/**
 * 签名密钥提供者：按公钥哈希返回可用于签名的私钥。
 *
 * <p>取代旧库的 {@code KeyBag.findKeyFromPubHash} 反查抽象。BAP 侧的实现按派生路径
 * 物化候选密钥（root 轮换区间、支付账户、Ordinals 账户），调用方拿到私钥后自行构造
 * 解锁模板（{@code TransactionInput.unlockingScriptTemplate}）再签名。
 */
public interface SigningKeyProvider {

    /**
     * 按公钥哈希查找签名私钥。
     *
     * @param pubkeyHash 压缩公钥的 hash160
     * @return 对应私钥；找不到时返回 {@code null}
     */
    PrivateKey findKeyFromPubHash(byte[] pubkeyHash);

    /** 按公钥字节查找签名私钥（先取 hash160）。 */
    default PrivateKey findKeyFromPubKey(byte[] pubkey) {
        return findKeyFromPubHash(hash160(pubkey));
    }

    /** 压缩公钥的 hash160。 */
    static byte[] hash160(byte[] data) {
        List<Double> digest = Hash.hash160(Utils.toArray(data), null);
        byte[] bytes = new byte[digest.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (int) (double) digest.get(i);
        }
        return bytes;
    }
}
