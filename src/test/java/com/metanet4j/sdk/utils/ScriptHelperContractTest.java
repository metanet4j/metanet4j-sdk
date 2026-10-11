package com.metanet4j.sdk.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metanet4j.bsv.script.OP;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.script.ScriptChunk;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link ScriptHelper#hasPushData} 的判定契约。
 *
 * <p>迁移要点：旧库的 {@code ScriptChunk<>(op, ScriptData/byte[])} 重载在新 SDK 中不存在，
 * 只有 {@code ScriptChunk(int op, List<Integer> data)}；旧用例中「op 为 OP_RETURN 但有数据」
 * 与「op 为 0（OP_0）但有数据」两类仍可表达，故逐条映射如下。
 */
class ScriptHelperContractTest {

    @Test
    void acceptsOnlyNonEmptyPushDataChunks() {
        assertTrue(ScriptHelper.hasPushData(new ScriptChunk(1, List.of(42))),
                "直接推送且数据非空");
        assertFalse(ScriptHelper.hasPushData(new ScriptChunk(OP.OP_RETURN)),
                "无数据");
        assertFalse(ScriptHelper.hasPushData(new ScriptChunk(OP.OP_RETURN, List.of(42))),
                "操作码不是推送");
        assertFalse(ScriptHelper.hasPushData(new ScriptChunk(1, List.of())),
                "数据为空列表");
        assertFalse(ScriptHelper.hasPushData(new ScriptChunk(1, null)),
                "数据为 null");
        assertFalse(ScriptHelper.hasPushData(new ScriptChunk(0, List.of(42))),
                "op 为 0（OP_0）即使带数据也不算推送");
    }

    @Test
    void rejectsNullChunk() {
        assertFalse(ScriptHelper.hasPushData(null));
    }

    @Test
    void rejectsOrdinalScriptsWithoutAPubKeyHashChunk() {
        // 覆盖 getAddressByOrdinalScript 缺少公钥哈希 chunk 时的抛错分支：
        // 单 chunk 脚本（chunk 数 < 3）与第 3 个 chunk 无数据两种形态。
        String tooShort = java.util.Base64.getEncoder().encodeToString(
                new byte[] {(byte) 0x51});
        assertThrows(IllegalArgumentException.class,
                () -> ScriptHelper.getAddressByOrdinalScript(tooShort),
                "chunk 数不足时应拒绝");

        String thirdChunkWithoutData = java.util.Base64.getEncoder().encodeToString(
                new byte[] {(byte) 0x51, (byte) 0x51, (byte) 0x4c, 0x00});
        assertThrows(IllegalArgumentException.class,
                () -> ScriptHelper.getAddressByOrdinalScript(thirdChunkWithoutData),
                "第 3 个 chunk 无数据时应拒绝");
    }

    @Test
    void splitsEveryPushdataVariantAndToleratesTruncatedScripts() {
        // 覆盖 chunksOf 的全部解析分支（该方法已收敛为全项目唯一实现，见 migration-rules §60，
        // 故此处一份测试即覆盖四个调用方所用的同一段逻辑）。
        ScriptHelper.chunksOf(Script.fromBinary(new byte[] {0x00}));                 // op == 0 → OP_0
        assertEquals(1, ScriptHelper.chunksOf(Script.fromBinary(new byte[] {0x02, 1, 2})).size());
        assertEquals(1, ScriptHelper.chunksOf(Script.fromBinary(new byte[] {0x4c, 0x02, 1, 2})).size());      // PUSHDATA1
        assertEquals(1, ScriptHelper.chunksOf(Script.fromBinary(new byte[] {0x4d, 0x02, 0x00, 1, 2})).size()); // PUSHDATA2
        assertEquals(1, ScriptHelper.chunksOf(Script.fromBinary(new byte[] {0x4e, 0x02, 0, 0, 0, 1, 2})).size()); // PUSHDATA4
        assertEquals(1, ScriptHelper.chunksOf(Script.fromBinary(new byte[] {(byte) 0x76})).size());            // 纯操作码

        // 越界：声明长度超出剩余字节 → 走「len = raw.length - i」修正分支
        assertEquals(1, ScriptHelper.chunksOf(Script.fromBinary(new byte[] {0x4c, 0x0a, 1, 2})).size());
        // 越界：长度前缀本身不完整 → 各 break 分支
        assertTrue(ScriptHelper.chunksOf(Script.fromBinary(new byte[] {0x4c})).isEmpty());
        assertTrue(ScriptHelper.chunksOf(Script.fromBinary(new byte[] {0x4d, 0x02})).isEmpty());
        assertTrue(ScriptHelper.chunksOf(Script.fromBinary(new byte[] {0x4e, 1, 2, 3})).isEmpty());
    }

}
