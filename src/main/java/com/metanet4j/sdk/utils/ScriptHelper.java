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
        // 注意：此处**无需**再判 op != OP_ZERO——isPushDataOpCode 已含 (op > OP_ZERO && op < OP_PUSHDATA1)
        // 与三个 PUSHDATA 常量的等值判断，op == 0 必为 false。原条件冗余且其右侧分支不可达（§53），
        // 按 §62.4 方案 C 消除。
        return isPushDataOpCode(scriptChunk.op);
    }

    /**
     * 取推送数据的字节；非推送 chunk 返回 {@code null}。
     *
     * <p>对应旧库 {@code scriptChunk.data.data()}；新 SDK 的 {@code data} 为 {@code List<Integer>}。
     */
    public static byte[] getPushData(ScriptChunk scriptChunk) {
        return hasPushData(scriptChunk) ? toBytes(scriptChunk.data) : null;
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

    /**
     * 按脚本字节自行切分 chunk（**全项目唯一实现**，四处调用方共用）。
     *
     * <p><b>为何不用 {@code Script.getChunks()}</b>：R1 接入的 bsv-sdk 0.2.0 的
     * {@code Script.parseChunks} 会在遇到 {@code OP_RETURN} 后**停止切分**，
     * 导致其后的 push 全部丢失；而本项目的 BOB tape 与 Ordinals 铭文恰好都是
     * 「OP_RETURN 之后才是载荷」的形态。已登记为上游缺陷（migration-rules §40），
     * 这里做等价规避：按 BIP 脚本编码自行解析，行为对正常脚本与上游一致。
     * 上游修复后可回退为 {@code script.getChunks()}。
     */
    public static List<ScriptChunk> chunksOf(Script script) {
        byte[] raw = script.toUint8Array();
        List<ScriptChunk> out = new java.util.ArrayList<>();
        int i = 0;
        while (i < raw.length) {
            int op = raw[i] & 0xFF;
            i++;
            int len;
            if (op == 0) {
                out.add(new ScriptChunk(OP.OP_0));
                continue;
            } else if (op >= 1 && op <= 75) {
                len = op;
            } else if (op == 76) {
                if (i >= raw.length) break;
                len = raw[i] & 0xFF;
                i++;
            } else if (op == 77) {
                if (i + 1 >= raw.length) break;
                len = (raw[i] & 0xFF) | ((raw[i + 1] & 0xFF) << 8);
                i += 2;
            } else if (op == 78) {
                if (i + 3 >= raw.length) break;
                len = (raw[i] & 0xFF) | ((raw[i + 1] & 0xFF) << 8)
                        | ((raw[i + 2] & 0xFF) << 16) | ((raw[i + 3] & 0xFF) << 24);
                i += 4;
            } else {
                out.add(new ScriptChunk(op));
                continue;
            }
            if (i + len > raw.length) {
                len = raw.length - i;
            }
            List<Integer> data = new java.util.ArrayList<>(len);
            for (int k = 0; k < len; k++) {
                data.add(raw[i + k] & 0xFF);
            }
            i += len;
            out.add(new ScriptChunk(op, data));
        }
        return out;
    }

}
