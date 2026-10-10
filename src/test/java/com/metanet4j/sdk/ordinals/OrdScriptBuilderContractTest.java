package com.metanet4j.sdk.ordinals;

import static com.metanet4j.bsv.script.OP.OP_0;
import static com.metanet4j.bsv.script.OP.OP_ENDIF;
import static com.metanet4j.bsv.script.OP.OP_IF;
import static com.metanet4j.bsv.script.OP.OP_RETURN;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.script.ScriptChunk;
import com.metanet4j.bsv.script.templates.P2PKH;
import com.metanet4j.sdk.PrivateKey;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.ordinals.OrdScriptBuilder.ORDMap;
import com.metanet4j.sdk.utils.ScriptHelper;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link OrdScriptBuilder} 的铭文脚本契约。
 *
 * <p>迁移要点：目标地址用业务 {@link AddressEnhance}；P2PKH 前缀由 {@code new P2PKH().lock(hash160)}
 * 生成；章节判定用 {@code OP.*} 常量；推送数据经 {@link ScriptHelper#getPushData} 取字节；
 * 脚本字节用 {@code toUint8Array()}（旧 {@code getProgram()}）。
 */
class OrdScriptBuilderContractTest {

    private static final AddressEnhance DESTINATION =
            AddressEnhance.fromPrivateKey(PrivateKey.of(BigInteger.TWO));

    @Test
    void inscriptionPreservesDestinationAndPushesMediaEnvelope() {
        byte[] content = {0, 1, 2, 3};
        Script script = OrdScriptBuilder.buildInscription(DESTINATION, content, "image/png", null);
        List<ScriptChunk> chunks = script.getChunks();
        List<ScriptChunk> payment = new P2PKH().lock(DESTINATION.getHash160()).getChunks();

        assertEquals(13, chunks.size());
        for (int i = 0; i < payment.size(); i++) {
            assertSameChunk(payment.get(i), chunks.get(i));
        }
        assertEquals(OP_0, chunks.get(5).op);
        assertEquals(OP_IF, chunks.get(6).op);
        assertArrayEquals("ord".getBytes(StandardCharsets.UTF_8), ScriptHelper.getPushData(chunks.get(7)));
        assertArrayEquals("image/png".getBytes(StandardCharsets.UTF_8), ScriptHelper.getPushData(chunks.get(9)));
        assertEquals(OP_0, chunks.get(10).op);
        assertArrayEquals(content, ScriptHelper.getPushData(chunks.get(11)));
        assertEquals(OP_ENDIF, chunks.get(12).op);
    }

    @Test
    void missingContentOrMediaTypeKeepsOrdEnvelopeOut() {
        byte[] payment = new P2PKH().lock(DESTINATION.getHash160()).toUint8Array();
        assertArrayEquals(payment, OrdScriptBuilder.buildInscription(DESTINATION, null, null, null).toUint8Array());
        assertArrayEquals(payment,
                OrdScriptBuilder.buildInscription(DESTINATION, new byte[] {1}, null, null).toUint8Array());
        assertArrayEquals(payment,
                OrdScriptBuilder.buildInscription(DESTINATION, null, "text/plain", null).toUint8Array());
    }

    @Test
    void mapMetadataRequiresAppAndTypeAndOmitsCommandField() {
        byte[] payment = new P2PKH().lock(DESTINATION.getHash160()).toUint8Array();
        assertArrayEquals(payment,
                OrdScriptBuilder.buildInscription(DESTINATION, null, null, new ORDMap()).toUint8Array());
        ORDMap typeOnly = new ORDMap();
        typeOnly.put("type", "image");
        assertArrayEquals(payment,
                OrdScriptBuilder.buildInscription(DESTINATION, null, null, typeOnly).toUint8Array());
        ORDMap incomplete = new ORDMap();
        incomplete.put("app", "test-app");
        assertArrayEquals(payment,
                OrdScriptBuilder.buildInscription(DESTINATION, null, null, incomplete).toUint8Array());
        incomplete.put("type", "");
        assertArrayEquals(payment,
                OrdScriptBuilder.buildInscription(DESTINATION, null, null, incomplete).toUint8Array());

        ORDMap metadata = new ORDMap();
        metadata.put("app", "test-app");
        metadata.put("type", "image");
        metadata.put("cmd", "do-not-serialize");
        metadata.put("context", "photo");
        Script script = OrdScriptBuilder.buildInscription(DESTINATION, null, null, metadata);
        List<ScriptChunk> chunks = script.getChunks();
        assertEquals(14, chunks.size());
        assertEquals(OP_RETURN, chunks.get(5).op);
        assertArrayEquals(OrdScriptBuilder.MAP_PREFIX.getBytes(StandardCharsets.UTF_8),
                ScriptHelper.getPushData(chunks.get(6)));
        assertArrayEquals("SET".getBytes(StandardCharsets.UTF_8), ScriptHelper.getPushData(chunks.get(7)));
        assertArrayEquals("app".getBytes(StandardCharsets.UTF_8), ScriptHelper.getPushData(chunks.get(8)));
        assertArrayEquals("test-app".getBytes(StandardCharsets.UTF_8), ScriptHelper.getPushData(chunks.get(9)));
        assertArrayEquals("type".getBytes(StandardCharsets.UTF_8), ScriptHelper.getPushData(chunks.get(10)));
        assertArrayEquals("image".getBytes(StandardCharsets.UTF_8), ScriptHelper.getPushData(chunks.get(11)));
        assertArrayEquals("context".getBytes(StandardCharsets.UTF_8), ScriptHelper.getPushData(chunks.get(12)));
        assertArrayEquals("photo".getBytes(StandardCharsets.UTF_8), ScriptHelper.getPushData(chunks.get(13)));
        assertFalse(chunks.stream().anyMatch(chunk -> {
            byte[] data = ScriptHelper.getPushData(chunk);
            return data != null && "do-not-serialize".equals(new String(data, StandardCharsets.UTF_8));
        }));
    }

    /** 新 SDK 的 ScriptChunk 未实现 equals，按操作码与推送数据比较。 */
    private static void assertSameChunk(ScriptChunk expected, ScriptChunk actual) {
        assertEquals(expected.op, actual.op, "操作码不一致");
        assertArrayEquals(ScriptHelper.getPushData(expected), ScriptHelper.getPushData(actual), "推送数据不一致");
    }
}
