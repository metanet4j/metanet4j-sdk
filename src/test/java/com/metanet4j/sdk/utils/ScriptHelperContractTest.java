package com.metanet4j.sdk.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metanet4j.bsv.script.OP;
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
}
