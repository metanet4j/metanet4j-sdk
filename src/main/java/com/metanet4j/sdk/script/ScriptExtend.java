package com.metanet4j.sdk.script;

import com.metanet4j.bsv.script.OP;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.script.ScriptChunk;
import com.metanet4j.sdk.utils.TxHelperExtend;
import java.util.ArrayList;
import java.util.List;

/**
 * 脚本类型判定与签名位填充（业务侧工具）。
 *
 * <p>旧实现继承 bitcoinj 的 {@code Script}，复用其 {@code isPayToScriptHash}／
 * {@code isSentToMultiSig}／{@code isSentToRawPubKey}／{@code isSentToAddress}、
 * {@code SIG_SIZE} 与 {@code ScriptBuilder.updateScriptWithSignature}；
 * 新 SDK（com.metanet4j.bsv）不提供这些能力，故在本类内按既有语义重建：
 *
 * <ul>
 *   <li>{@link #SIG_SIZE} = 75，与原库 {@code Script.SIG_SIZE} 取值一致；</li>
 *   <li>脚本类型判定按 chunk 结构（与旧库同判据）；</li>
 *   <li>签名位填充按旧库算法：跳过前 {@code sigsPrefixCount} 与后 {@code sigsSuffixCount} 个
 *       chunk，在中间段的第 {@code index} 位放入签名。</li>
 * </ul>
 */
public final class ScriptExtend {

    /** 单个签名占用的估算字节数，与原库 {@code Script.SIG_SIZE} 一致。 */
    public static final int SIG_SIZE = 75;

    private ScriptExtend() {
    }

    /** P2PKH：OP_DUP OP_HASH160 &lt;20 字节&gt; OP_EQUALVERIFY OP_CHECKSIG。 */
    public static boolean isSentToAddress(Script script) {
        List<ScriptChunk> chunks = script.getChunks();
        return chunks.size() == 5
                && chunks.get(0).op == OP.OP_DUP
                && chunks.get(1).op == OP.OP_HASH160
                && chunks.get(2).data != null && chunks.get(2).data.size() == 20
                && chunks.get(3).op == OP.OP_EQUALVERIFY
                && chunks.get(4).op == OP.OP_CHECKSIG;
    }

    /** P2SH：OP_HASH160 &lt;20 字节&gt; OP_EQUAL。 */
    public static boolean isPayToScriptHash(Script script) {
        List<ScriptChunk> chunks = script.getChunks();
        return chunks.size() == 3
                && chunks.get(0).op == OP.OP_HASH160
                && chunks.get(1).data != null && chunks.get(1).data.size() == 20
                && chunks.get(2).op == OP.OP_EQUAL;
    }

    /** 裸公钥：&lt;公钥&gt; OP_CHECKSIG。 */
    public static boolean isSentToRawPubKey(Script script) {
        List<ScriptChunk> chunks = script.getChunks();
        return chunks.size() == 2
                && isStandardPubKey(chunks.get(0).data)
                && chunks.get(1).op == OP.OP_CHECKSIG;
    }

    /** 多签：OP_m &lt;公钥...&gt; OP_n OP_CHECKMULTISIG。 */
    public static boolean isSentToMultiSig(Script script) {
        List<ScriptChunk> chunks = script.getChunks();
        if (chunks.size() < 4) {
            return false;
        }
        if (chunks.get(chunks.size() - 1).op != OP.OP_CHECKMULTISIG) {
            return false;
        }
        if (chunks.get(0).op < OP.OP_1 || chunks.get(0).op > OP.OP_16) {
            return false;
        }
        if (chunks.get(chunks.size() - 2).op < OP.OP_1 || chunks.get(chunks.size() - 2).op > OP.OP_16) {
            return false;
        }
        for (int i = 1; i < chunks.size() - 2; i++) {
            if (!isStandardPubKey(chunks.get(i).data)) {
                return false;
            }
        }
        return true;
    }

    /** 多签所需签名个数（OP_m 的操作码减 OP_1 加 1）。 */
    public static int getNumberOfSignaturesRequiredToSpend(Script script) {
        if (!isSentToMultiSig(script)) {
            throw new IllegalArgumentException("脚本不是多签脚本");
        }
        return script.getChunks().get(0).op - OP.OP_1 + 1;
    }

    /**
     * 把签名填入脚本的指定签名位。
     *
     * @param scriptSig        待填充的解锁脚本
     * @param sigBytes         签名（含 sighash 尾字节）
     * @param index            签名位下标
     * @param sigsPrefixCount  前置 chunk 数（P2SH 为 1 个 OP_0）
     * @param sigsSuffixCount  后置 chunk 数（P2SH 为 redeemScript，P2PKH 为公钥）
     */
    public static Script updateScriptWithSignature(Script scriptSig, byte[] sigBytes, int index,
                                                   int sigsPrefixCount, int sigsSuffixCount) {
        List<ScriptChunk> chunks = scriptSig.getChunks();
        if (index < 0 || index >= chunks.size() - sigsPrefixCount - sigsSuffixCount) {
            throw new IllegalArgumentException("签名位下标越界：" + index
                    + "（chunk 数 " + chunks.size() + "，前置 " + sigsPrefixCount
                    + "，后置 " + sigsSuffixCount + "）");
        }
        List<ScriptChunk> out = new ArrayList<>(chunks.size());
        for (int i = 0; i < sigsPrefixCount; i++) {
            out.add(chunks.get(i));
        }
        for (int i = sigsPrefixCount; i < chunks.size() - sigsSuffixCount; i++) {
            if (i == sigsPrefixCount + index) {
                out.add(new ScriptChunk(pushDataOpCode(sigBytes.length), toBoxed(sigBytes)));
            } else {
                out.add(chunks.get(i));
            }
        }
        for (int i = chunks.size() - sigsSuffixCount; i < chunks.size(); i++) {
            out.add(chunks.get(i));
        }
        return new Script(out);
    }

    /**
     * 按脚本类型把签名写入解锁脚本（原 {@code Script.getScriptSigWithSignature} 的替代）。
     *
     * <p>前缀／后缀 chunk 数按脚本类型判定：P2SH 前后各 1；多签前置 1；
     * P2PKH 与 Ordinals 脚本后置 1。
     */
    public static Script getScriptSigWithSignature(Script connectedScript, Script scriptSig,
                                                   byte[] sigBytes, int index) {
        int sigsPrefixCount = 0;
        int sigsSuffixCount = 0;
        if (isPayToScriptHash(connectedScript)) {
            sigsPrefixCount = 1; // OP_0 <sig>* <redeemScript>
            sigsSuffixCount = 1;
        } else if (isSentToMultiSig(connectedScript)) {
            sigsPrefixCount = 1; // OP_0 <sig>*
        } else if (isSentToAddress(connectedScript)) {
            sigsSuffixCount = 1; // <sig> <pubkey>
        } else if (TxHelperExtend.isSentToOrdinals(connectedScript)) {
            sigsSuffixCount = 1; // <sig> <pubkey>
        }
        return updateScriptWithSignature(scriptSig, sigBytes, index, sigsPrefixCount, sigsSuffixCount);
    }

    /**
     * 估算花费该脚本所需字节数（原 {@code Script.getNumberOfBytesRequiredToSpend} 的替代）。
     *
     * @param script        被花费的锁定脚本
     * @param pubKeyLen     公钥长度；不大于 0 时按 65 字节非压缩估算
     * @param redeemScript P2SH 赎回脚本；非 P2SH 可为 {@code null}
     */
    public static int getNumberOfBytesRequiredToSpend(Script script, int pubKeyLen,
                                                      Script redeemScript) {
        if (isPayToScriptHash(script)) {
            if (redeemScript == null) {
                throw new IllegalArgumentException("P2SH script requires redeemScript to be spent");
            }
            return getNumberOfSignaturesRequiredToSpend(redeemScript) * SIG_SIZE
                    + redeemScript.toUint8Array().length;
        }
        if (isSentToMultiSig(script)) {
            return getNumberOfSignaturesRequiredToSpend(script) * SIG_SIZE + 1;
        }
        if (isSentToRawPubKey(script)) {
            return SIG_SIZE;
        }
        if (isSentToAddress(script) || TxHelperExtend.isSentToOrdinals(script)) {
            int uncompressedPubKeySize = 65;
            return SIG_SIZE + (pubKeyLen > 0 ? pubKeyLen : uncompressedPubKeySize);
        }
        throw new IllegalStateException("Unsupported script type");
    }

    private static boolean isStandardPubKey(List<Integer> data) {
        return data != null && (data.size() == 33 || data.size() == 65);
    }

    /** 与旧库 {@code Script.pushdataOpCodeNum} 一致的长度前缀操作码。 */
    private static int pushDataOpCode(int length) {
        if (length >= 0 && length < OP.OP_PUSHDATA1) {
            return length;
        }
        if (length < 256) {
            return OP.OP_PUSHDATA1;
        }
        if (length < 65536) {
            return OP.OP_PUSHDATA2;
        }
        return OP.OP_PUSHDATA4;
    }

    private static List<Integer> toBoxed(byte[] data) {
        List<Integer> values = new ArrayList<>(data.length);
        for (byte b : data) {
            values.add(b & 0xFF);
        }
        return values;
    }
}
