package com.metanet4j.sdk.utils;

import cn.hutool.core.codec.Base64;
import com.metanet4j.base.bsv.BsvAddress;
import com.metanet4j.base.bsv.BsvNetwork;
import com.metanet4j.bsv.script.OP;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.script.ScriptChunk;
import com.metanet4j.sdk.PrivateKey;
import com.metanet4j.sdk.bap.SigningKeyProvider;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Ordinals 脚本识别与地址解析（业务工具）。
 *
 * <p>旧实现继承 bitcoinj 的 {@code TxHelper} 并提供 {@code getConnectedRedeemDataExtend}
 * （按 {@code KeyBag} 反查密钥）。按迁移计划 §3，KeyBag／RedeemData 反查抽象已取消：
 * 该入口改由调用方按 {@link com.metanet4j.sdk.bap.SigningKeyProvider} 显式取得私钥，
 * 再注入解锁模板，因此本类只保留脚本识别与地址解析。
 *
 * <p>脚本判定与新 SDK（com.metanet4j.bsv）的 {@code ScriptChunk} 对齐：
 * opcode 字段为 {@code op}，推送数据为 {@code data}（{@code List<Integer>}）。
 */
public final class TxHelperExtend {

    private static final int ADDRESS_LENGTH = 20;

    private TxHelperExtend() {
    }

    /**
     * 是否为 Ordinals 铭文脚本：
     * {@code OP_DUP OP_HASH160 <20> OP_EQUALVERIFY OP_CHECKSIG OP_0 OP_IF "ord" OP_1 <媒体类型> OP_0 <内容> OP_ENDIF}。
     */
    public static boolean isSentToOrdinals(Script connectedScript) {
        try {
            List<ScriptChunk> chunks = com.metanet4j.sdk.utils.ScriptHelper.chunksOf(connectedScript);
            return chunks.size() >= 13
                    && chunks.get(0).op == OP.OP_DUP
                    && chunks.get(1).op == OP.OP_HASH160
                    && dataOf(chunks.get(2)) != null && dataOf(chunks.get(2)).length == ADDRESS_LENGTH
                    && chunks.get(3).op == OP.OP_EQUALVERIFY
                    && chunks.get(4).op == OP.OP_CHECKSIG
                    && chunks.get(5).op == OP.OP_0
                    && chunks.get(6).op == OP.OP_IF
                    && Arrays.equals(dataOf(chunks.get(7)), "ord".getBytes(StandardCharsets.UTF_8))
                    && chunks.get(8).op == OP.OP_1
                    && isPushData(chunks.get(9))
                    && chunks.get(10).op == OP.OP_0
                    && isPushData(chunks.get(11))
                    && chunks.get(12).op == OP.OP_ENDIF;
        } catch (Exception e) {
            return false;
        }
    }

    /** 由 Ordinals 脚本（Base64）取公钥哈希；非 Ordinals 脚本返回空数组。 */
    public static byte[] getPubKeyHashByOrdinalScript(String scriptB64) {
        return getPubKeyHashByOrdinalScript(Script.fromBinary(Base64.decode(scriptB64)));
    }

    /** 由 Ordinals 脚本取公钥哈希；非 Ordinals 脚本返回空数组。 */
    public static byte[] getPubKeyHashByOrdinalScript(Script script) {
        if (!isSentToOrdinals(script)) {
            return new byte[0];
        }
        return dataOf(com.metanet4j.sdk.utils.ScriptHelper.chunksOf(script).get(2));
    }

    /** 由 Ordinals 脚本（Base64）解析 P2PKH 地址。 */
    public static String getAddressByOrdinalScript(String scriptB64) {
        return getAddressByOrdinalScript(Script.fromBinary(Base64.decode(scriptB64)));
    }

    /** 由 Ordinals 脚本解析 P2PKH 地址。 */
    public static String getAddressByOrdinalScript(Script script) {
        return BsvAddress.fromPubKeyHash(BsvNetwork.MAINNET, getPubKeyHashByOrdinalScript(script))
                .toBase58();
    }

    /** 推送数据的字节形式；无数据时返回 {@code null}。 */
    private static byte[] dataOf(ScriptChunk chunk) {
        // 调用方只传 chunks.get(i)（永不为 null），故不设 chunk == null 判断——
        // 该侧不可达（§62.4 方案 C）。
        if (chunk.data == null) {
            return null;
        }
        byte[] bytes = new byte[chunk.data.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (int) chunk.data.get(i);
        }
        return bytes;
    }

    /** 是否为直接推送数据的 chunk（opcode 处于推送范围且非 0）。 */
    private static boolean isPushData(ScriptChunk chunk) {
        int op = chunk.op;
        // 原式为 `op != 0 && (op == OP_PUSHDATA1 || op == OP_PUSHDATA2 || op == OP_PUSHDATA4
        // || op < OP_PUSHDATA1)`。由于 OP_PUSHDATA1 < OP_PUSHDATA2 < OP_PUSHDATA4 依次相邻，
        // 该 `||` 链**等价于区间判断** `op >= 1 && op <= OP_PUSHDATA4`（op 为无符号字节）。
        // 按 §62.4 方案 C 收敛为区间形式——既消去不可达的 `op == OP_PUSHDATA4` 等分支，
        // 也让语义（"是长度为 1..78 的推送"）直接可读。
        return chunk.data != null && !chunk.data.isEmpty()
                && op >= 1 && op <= OP.OP_PUSHDATA4;
    }


    /**
     * 取某输入的可花费信息（签名私钥 + 赎回脚本）。
     *
     * <p>按迁移计划 §3，旧实现按 {@code KeyBag.findKeyFromPubHash} 反查密钥；
     * 现改为经 {@link SigningKeyProvider} 显式派生取得私钥，
     * 公钥哈希优先取 Ordinals 铭文脚本中的地址哈希，否则取连接输出锁定脚本的 P2PKH 哈希。
     *
     * @param connectedOutput 连接输出（新 SDK 输出）
     * @param keyProvider     显式密钥提供者
     * @return 可花费信息；锁定脚本无法识别或找不到密钥时返回 {@code null}
     */
    public static RedeemDataExtend getConnectedRedeemDataExtend(
            com.metanet4j.bsv.transaction.TransactionOutput connectedOutput,
            SigningKeyProvider keyProvider) {
        if (connectedOutput == null || connectedOutput.lockingScript == null) {
            return null;
        }
        Script lockingScript = connectedOutput.lockingScript;
        byte[] pubKeyHash = getPubKeyHashByOrdinalScript(lockingScript);
        if (pubKeyHash.length == 0) {
            pubKeyHash = pubKeyHashOfP2pkh(lockingScript);
        }
        if (pubKeyHash.length == 0) {
            return null;
        }
        // 注意：此处**不设** key == null 分支——提供者（BapProviderKeyBag）在解析不到密钥时
        // **抛 RuntimeException 而非返回 null**（§132 已确认，BapProviderKeyBagTest 要求该行为），
        // 故该分支永不成立、属不可达代码，按 §62.4 方案 C 消除。
        PrivateKey key = keyProvider.findKeyFromPubHash(pubKeyHash);
        // 把连接输出的锁定脚本作为赎回脚本一并返回：调用方据此重建输入脚本
        // 或校验地址；P2SH 场景下即为赎回脚本本身。
        return RedeemDataExtend.of(key, lockingScript);
    }

    /** P2PKH 锁定脚本的公钥哈希；非 P2PKH 返回空数组。 */
    private static byte[] pubKeyHashOfP2pkh(Script script) {
        List<ScriptChunk> chunks = com.metanet4j.sdk.utils.ScriptHelper.chunksOf(script);
        if (chunks.size() == 5
                && chunks.get(0).op == OP.OP_DUP
                && chunks.get(1).op == OP.OP_HASH160
                && dataOf(chunks.get(2)) != null && dataOf(chunks.get(2)).length == ADDRESS_LENGTH
                && chunks.get(3).op == OP.OP_EQUALVERIFY
                && chunks.get(4).op == OP.OP_CHECKSIG) {
            return dataOf(chunks.get(2));
        }
        return new byte[0];
    }

    /** 按脚本取公钥哈希字节列表（便于调用方复用）。 */
    public static List<Integer> pubKeyHashList(Script script) {
        byte[] hash = getPubKeyHashByOrdinalScript(script);
        List<Integer> out = new ArrayList<>(hash.length);
        for (byte b : hash) {
            out.add(b & 0xFF);
        }
        return out;
    }

}
