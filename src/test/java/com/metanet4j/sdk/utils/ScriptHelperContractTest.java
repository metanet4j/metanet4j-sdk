package com.metanet4j.sdk.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.bitcoinsv.bitcoinjsv.script.ScriptChunk;
import io.bitcoinsv.bitcoinjsv.script.ScriptData;
import io.bitcoinsv.bitcoinjsv.script.ScriptOpCodes;
import org.junit.jupiter.api.Test;

class ScriptHelperContractTest {

    @Test
    void acceptsOnlyNonEmptyPushDataChunks() {
        assertTrue(ScriptHelper.hasPushData(new ScriptChunk<>(1, new byte[] {42})));
        assertFalse(ScriptHelper.hasPushData(new ScriptChunk<>(ScriptOpCodes.OP_RETURN,
                (ScriptData) null, null)));
        assertFalse(ScriptHelper.hasPushData(new ScriptChunk<>(ScriptOpCodes.OP_RETURN, (byte[]) null)));
        assertFalse(ScriptHelper.hasPushData(new ScriptChunk<>(1, (ScriptData) () -> null, null)));
        assertFalse(ScriptHelper.hasPushData(new ScriptChunk<>(ScriptOpCodes.OP_RETURN, new byte[] {42})));
        assertFalse(ScriptHelper.hasPushData(new ScriptChunk<>(0, new byte[0])));
        assertFalse(ScriptHelper.hasPushData(new ScriptChunk<>(0, new byte[] {42})));
    }
}
