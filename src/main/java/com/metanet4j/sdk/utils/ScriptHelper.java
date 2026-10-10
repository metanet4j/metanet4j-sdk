package com.metanet4j.sdk.utils;

import cn.hutool.core.codec.Base64;
import com.metanet4j.base.bsv.BsvAddress;
import com.metanet4j.base.bsv.BsvNetwork;
import com.metanet4j.bsv.script.OP;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.script.ScriptChunk;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * 脚本小工具：推送数据判定与 Ordinals 脚本地址解析。
 *
 * <p>旧实现使用 bitcoinj 的 {@code ScriptChunk}／{@code Script}／{@code AddressLite}；
 * 现改用新 SDK（com.metanet4j.bsv）的脚本模型与 base 的 {@link BsvAddress}。
 * 「推送数据」判定与旧实现一致：数据非空、opcode 处于推送范围且不为 0。
 */
@UtilityClass
public class ScriptHelper {

    /** 无操作码（0）不视为推送数据，与旧实现一致。 */
    private static final int OP_ZERO = 0;

    public static boolean hasPushData(ScriptChunk scriptChunk) {
        if (scriptChunk == null || scriptChunk.data == null || scriptChunk.data.isEmpty()) {
            return false;
        }
        return isPushDataOpCode(scriptChunk.op) && scriptChunk.op != OP_ZERO;
    }

    /**
     * 取推送数据的字节；非推送 chunk 返回 {@code null}。
     *
     * <p>对应旧库 {@code scriptChunk.data.data()}；新 SDK 的 {@code data} 为 {@code List<Integer>}。
     */
    public static byte[] getPushData(ScriptChunk scriptChunk) {
        return hasPushData(scriptChunk) ? toBytes(scriptChunk.data) : null;
    }

    /** 取推送数据的 UTF-8 文本；非推送 chunk 返回 {@code null}。 */
    public static String getPushDataAsString(ScriptChunk scriptChunk) {
        byte[] data = getPushData(scriptChunk);
        return data == null ? null : new String(data, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * 操作码是否直接推送数据：{@code 1..75}（直接长度）、{@code OP_PUSHDATA1/2/4}。
     * 对应新 SDK {@code Script.isPushOnly()} 的边界（{@code op <= OP_16}）。
     */
    private static boolean isPushDataOpCode(int op) {
        return op == OP.OP_PUSHDATA1 || op == OP.OP_PUSHDATA2 || op == OP.OP_PUSHDATA4
                || (op > OP_ZERO && op < OP.OP_PUSHDATA1);
    }

    /** 由 Ordinals 锁定脚本（Base64）解析出第 3 个 chunk 的公钥哈希地址。 */
    public static String getAddressByOrdinalScript(String scriptB) {
        Script script = Script.fromBinary(Base64.decode(scriptB));
        List<ScriptChunk> chunks = script.getChunks();
        if (chunks.size() < 3 || chunks.get(2).data == null) {
            throw new IllegalArgumentException("Ordinals 脚本缺少公钥哈希 chunk：" + scriptB);
        }
        return BsvAddress.fromPubKeyHash(BsvNetwork.MAINNET, toBytes(chunks.get(2).data)).toBase58();
    }

    private static byte[] toBytes(List<Integer> values) {
        byte[] bytes = new byte[values.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (int) values.get(i);
        }
        return bytes;
    }
}
