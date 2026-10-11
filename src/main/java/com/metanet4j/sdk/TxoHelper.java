package com.metanet4j.sdk;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.HexUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.metanet4j.base.bsv.BsvAddress;
import com.metanet4j.base.bsv.BsvNetwork;
import com.metanet4j.base.bsv.BsvHashBytes;
import com.metanet4j.bsv.script.OP;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.script.ScriptChunk;
import com.metanet4j.bsv.transaction.Transaction;
import com.metanet4j.bsv.transaction.TransactionInput;
import com.metanet4j.bsv.transaction.TransactionOutput;
import com.metanet4j.sdk.utils.ScriptHelper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.metanet4j.bsv.script.OP;

/**
 * 交易 → TXO JSON 映射（与旧实现字段一致）。
 *
 * <p>旧实现基于 bitcoinj 的 {@code TxBean}／{@code TxInput}／{@code TxOutput}／{@code Script}／
 * {@code ScriptUtils}；现改用新 SDK（com.metanet4j.bsv）的交易与脚本模型，以及 base 的
 * {@link BsvAddress}／{@link BsvHashBytes}。输出 JSON 的键名与含义保持不变（V07／V14）。
 */
public class TxoHelper {

    /** 主网 P2PKH 长度：OP_DUP OP_HASH160 <20> OP_EQUALVERIFY OP_CHECKSIG。 */
    private static final int P2PKH_CHUNK_COUNT = 5;

    /**
     * 解析 raw 交易并输出 TXO 结构。
     *
     * @param s raw 交易 hex
     * @throws RuntimeException 交易字节非法（与旧实现一致）
     */
    public static Map<String, Object> toTxo(String s) {
        final Transaction tx;
        try {
            tx = Transaction.fromHex(s);
        } catch (RuntimeException e) {
            throw new RuntimeException("交易解析失败：" + e.getMessage(), e);
        }
        String txId = tx.id("hex");

        Map<String, Object> txMap = new LinkedHashMap<>();
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("h", txId);
        txMap.put("tx", head);

        JSONArray inArray = new JSONArray();
        List<TransactionInput> inputs = tx.inputs;
        for (int i = 0; i < inputs.size(); i++) {
            Map<String, Object> inputItem = new LinkedHashMap<>();
            TransactionInput txInput = inputs.get(i);
            Script script = toScript(txInput.unlockingScript);

            List<ScriptChunk> chunks = com.metanet4j.sdk.utils.ScriptHelper.chunksOf(script);
            inputItem.put("i", i);
            inputItem.put("seq", txInput.sequence);

            Map<String, Object> outpoint = new LinkedHashMap<>();
            outpoint.put("h", txidOf(txInput.sourceTXID));
            outpoint.put("i", txInput.sourceOutputIndex);
            if (chunks.size() == 2 && chunks.get(1).data != null) {
                // P2PKH 输入脚本：由推送的公钥反推地址（与旧实现 same）
                ScriptChunk scriptChunk = chunks.get(1);
                byte[] pubKey = ScriptHelper.getPushData(scriptChunk);
                // 非标准长度公钥（旧库 BasicAddress 会容忍并按 hash160 编码）回退为脚本 hex
                outpoint.put("a", isStandardPubKey(pubKey)
                        ? addressOfPubKey(pubKey) : script.toHex());
            } else {
                // 契约（冻结向量 V07）要求的是**可读操作码名**（如 "DUP HASH160"），
                // 与旧库 Script.toString() 一致；早期实现给的是裸 hex（"76a9"）。
                outpoint.put("a", opcodeNames(script));
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("e", outpoint);
            inputItem.putAll(entry);

            inputItem.put("len", chunks.size());
            appendChunkFields(inputItem, chunks);
            inArray.add(inputItem);
        }

        JSONArray outArray = new JSONArray();
        List<TransactionOutput> outputs = tx.outputs;
        for (int i = 0; i < outputs.size(); i++) {
            Map<String, Object> outItem = new LinkedHashMap<>();
            TransactionOutput txOutput = outputs.get(i);
            Script script = txOutput.lockingScript;

            outItem.put("i", i);
            JSONObject e = new JSONObject();
            e.put("v", satoshisOf(txOutput.satoshis));
            // 与旧实现一致：OP_RETURN 记 "false"，其余脚本尽量解析为地址，无法解析时回退脚本 hex
            // 与基线一致：仅「OP_FALSE + OP_RETURN」记 "false"；
            // 纯 OP_RETURN 走地址解析、失败时回退**可读操作码名**（旧 Script.toString()）。
            String scriptAddress;
            try {
                scriptAddress = isOpReturnAfterGenesis(script) ? "false" : addressOfScript(script);
            } catch (RuntimeException ex) {
                scriptAddress = opcodeNames(script);
            }
            e.put("a", scriptAddress);
            e.put("i", i);
            outItem.put("e", e);
            outItem.put("len", com.metanet4j.sdk.utils.ScriptHelper.chunksOf(script).size());
            appendChunkFields(outItem, com.metanet4j.sdk.utils.ScriptHelper.chunksOf(script));
            outArray.add(outItem);
        }

        txMap.put("in", inArray);
        txMap.put("out", outArray);
        txMap.put("lock", tx.lockTime);
        txMap.put("v", tx.version);
        return txMap;
    }

    /** 按 chunk 逐个写入 s／b／h（推送数据）与 o（操作码名），与旧实现字段一致。 */
    private static void appendChunkFields(Map<String, Object> target, List<ScriptChunk> chunks) {
        for (int j = 0; j < chunks.size(); j++) {
            ScriptChunk scriptChunk = chunks.get(j);
            byte[] pushData = ScriptHelper.getPushData(scriptChunk);
            if (pushData != null) {
                target.put("s" + j, new String(pushData, StandardCharsets.UTF_8));
                target.put("b" + j, Base64.encode(pushData));
                target.put("h" + j, HexUtil.encodeHexStr(pushData));
            }
            if (scriptChunk.op == OP.OP_0 || scriptChunk.op > OP.OP_16) {
                // OP.name() 已含 "OP_" 前缀
target.put("o" + j, OP.name(scriptChunk.op));
            }
        }
    }

    /**
     * 取解锁脚本。
     *
     * <p>与 {@code BobHelper.asScript} 同源、同理由（migration-rules §62）：
     * 公共入口只有 hex 形式，解析器与签名器对该字段只会产出 {@code Script}
     * （未签名时为默认哨兵 {@code Undefined}，落兜底返回空脚本），
     * 故 {@code byte[]}／{@code List<?>} 分支**不可达**，按 §62.4 方案 C 收敛。
     */
    private static Script toScript(Object unlockingScript) {
        if (unlockingScript instanceof Script script) {
            return script;
        }
        return new Script();
    }

    /**
     * txid 的显示序 hex。
     *
     * <p>该字段由 SDK 解析器赋**显示序 hex 串**，故只需处理 {@code String} 与兜底；
     * 原实现另有 {@code byte[]}／{@code List<?>} 两个分支，但新 SDK 的
     * {@code TransactionInput.unlockingScript}/{@code sourceTXID} 虽声明为 {@code Object}，
     * **解析器对同一字段只产出一种类型**，而本项目公共入口只有 hex 形式
     * （{@code toTxo(String)}），故那两个分支**不可达**（migration-rules §62）。
     * 按 §62.4 方案 C「简化代码、消除不可达分支」收敛为此形态。
     */
    private static String txidOf(Object sourceTXID) {
        if (sourceTXID == null) {
            return BsvHashBytes.ZERO_HASH.toString();
        }
        if (sourceTXID instanceof String text) {
            return text;
        }
        return String.valueOf(sourceTXID);
    }

    /** 金额字段可能是 {@code Undefined}、数字或字符串。 */
    private static long satoshisOf(Object satoshis) {
        if (satoshis instanceof Number number) {
            return number.longValue();
        }
        // 解析器对金额字段赋 Number；其余形态（含默认哨兵 Undefined）落 0 兜底。
        return 0L;
    }

    /** 是否 OP_RETURN 输出（首个操作码为 OP_RETURN）。 */
    private static boolean isOpReturn(Script script) {
        List<ScriptChunk> chunks = com.metanet4j.sdk.utils.ScriptHelper.chunksOf(script);
        return !chunks.isEmpty() && chunks.get(0).op == OP.OP_RETURN;
    }

    /** 公钥是否为 33 字节压缩或 65 字节非压缩。 */
    private static boolean isStandardPubKey(byte[] pubKey) {
        return pubKey != null && (pubKey.length == 33 || pubKey.length == 65);
    }

    /** 由公钥字节推导 P2PKH 地址。 */
    private static String addressOfPubKey(byte[] pubKey) {
        return BsvAddress.fromPublicKey(BsvNetwork.MAINNET,
                com.metanet4j.bsv.primitives.PublicKey.fromDER(toBoxed(pubKey))).toBase58();
    }

    /** 由锁定脚本推导地址：P2PKH / P2SH；其他脚本抛异常（与旧实现 getToAddress 行为一致）。 */
    private static String addressOfScript(Script script) {
        List<ScriptChunk> chunks = com.metanet4j.sdk.utils.ScriptHelper.chunksOf(script);
        if (chunks.size() == P2PKH_CHUNK_COUNT
                && chunks.get(0).op == OP.OP_DUP
                && chunks.get(1).op == OP.OP_HASH160
                && chunks.get(3).op == OP.OP_EQUALVERIFY
                && chunks.get(4).op == OP.OP_CHECKSIG
                && chunks.get(2).data != null) {
            return BsvAddress.fromPubKeyHash(BsvNetwork.MAINNET,
                    toBytes(chunks.get(2).data)).toBase58();
        }
        if (chunks.size() == 3 && chunks.get(0).op == OP.OP_HASH160
                && chunks.get(1).data != null && chunks.get(2).op == OP.OP_EQUAL) {
            return BsvAddress.fromScriptHash(BsvNetwork.MAINNET,
                    toBytes(chunks.get(1).data)).toBase58();
        }
        throw new IllegalArgumentException("脚本不是可识别的地址类型：" + script.toHex());
    }

    private static List<Double> toBoxed(byte[] data) {
        Double[] values = new Double[data.length];
        for (int i = 0; i < data.length; i++) {
            values[i] = (double) (data[i] & 0xFF);
        }
        return List.of(values);
    }

    private static byte[] toBytes(List<Integer> values) {
        byte[] bytes = new byte[values.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (int) values.get(i);
        }
        return bytes;
    }

    /** 把脚本渲染为可读操作码名（去掉新 SDK 的 "OP_" 前缀），与旧库 Script.toString() 对齐。 */
    private static String opcodeNames(Script script) {
        StringBuilder sb = new StringBuilder();
        for (var chunk : com.metanet4j.sdk.utils.ScriptHelper.chunksOf(script)) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            String name = OP.name(chunk.op);
            if (name == null) {
                name = String.valueOf(chunk.op);
            }
            sb.append(name.startsWith("OP_") ? name.substring(3) : name);
        }
        return sb.toString();
    }


    /** 是否为「OP_FALSE + OP_RETURN」形态（旧库 isOpReturnAfterGenesis）。 */
    private static boolean isOpReturnAfterGenesis(Script script) {
        List<com.metanet4j.bsv.script.ScriptChunk> cs = com.metanet4j.sdk.utils.ScriptHelper.chunksOf(script);
        return cs.size() >= 2 && cs.get(0).op == com.metanet4j.bsv.script.OP.OP_0
                && cs.get(1).op == com.metanet4j.bsv.script.OP.OP_RETURN;
    }


    private static List<ScriptChunk> chunksOf(Script script) {
        // 委托给全项目唯一实现（见 migration-rules §60：此前四个类各存一份副本，
        // 其边界分支需在四处重复覆盖；收敛为一份后只需覆盖一次）。
        return com.metanet4j.sdk.utils.ScriptHelper.chunksOf(script);
    }

}
