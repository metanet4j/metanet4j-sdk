package com.metanet4j.sdk.ordinals;

import static io.bitcoinsv.bitcoinjsv.script.ScriptOpCodes.OP_ENDIF;
import static io.bitcoinsv.bitcoinjsv.script.ScriptOpCodes.OP_FALSE;
import static io.bitcoinsv.bitcoinjsv.script.ScriptOpCodes.OP_IF;
import static io.bitcoinsv.bitcoinjsv.script.ScriptOpCodes.OP_RETURN;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metanet4j.sdk.ordinals.OrdScriptBuilder.ORDMap;
import io.bitcoinsv.bitcoinjsv.core.Address;
import io.bitcoinsv.bitcoinjsv.core.ECKeyLite;
import io.bitcoinsv.bitcoinjsv.params.MainNetParams;
import io.bitcoinsv.bitcoinjsv.script.Script;
import io.bitcoinsv.bitcoinjsv.script.ScriptBuilder;
import io.bitcoinsv.bitcoinjsv.script.ScriptChunk;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrdScriptBuilderContractTest {

    private static final Address DESTINATION = new Address(MainNetParams.get(),
            ECKeyLite.fromPrivate(BigInteger.TWO).getPubKeyHash());

    @Test
    void inscriptionPreservesDestinationAndPushesMediaEnvelope() {
        byte[] content = {0, 1, 2, 3};
        Script script = OrdScriptBuilder.buildInscription(DESTINATION, content, "image/png", null);
        List<ScriptChunk> chunks = script.getChunks();
        List<ScriptChunk> payment = ScriptBuilder.createOutputScript(DESTINATION).getChunks();

        assertEquals(13, chunks.size());
        for (int i = 0; i < payment.size(); i++) {
            assertEquals(payment.get(i), chunks.get(i));
        }
        assertTrue(chunks.get(5).equalsOpCode(OP_FALSE));
        assertTrue(chunks.get(6).equalsOpCode(OP_IF));
        assertArrayEquals("ord".getBytes(StandardCharsets.UTF_8), chunks.get(7).data());
        assertArrayEquals("image/png".getBytes(StandardCharsets.UTF_8), chunks.get(9).data());
        assertTrue(chunks.get(10).equalsOpCode(OP_FALSE));
        assertArrayEquals(content, chunks.get(11).data());
        assertTrue(chunks.get(12).equalsOpCode(OP_ENDIF));
    }

    @Test
    void missingContentOrMediaTypeKeepsOrdEnvelopeOut() {
        byte[] payment = ScriptBuilder.createOutputScript(DESTINATION).getProgram();
        assertArrayEquals(payment, OrdScriptBuilder.buildInscription(DESTINATION, null, null, null).getProgram());
        assertArrayEquals(payment, OrdScriptBuilder.buildInscription(DESTINATION, new byte[] {1}, null, null).getProgram());
        assertArrayEquals(payment, OrdScriptBuilder.buildInscription(DESTINATION, null, "text/plain", null).getProgram());
    }

    @Test
    void mapMetadataRequiresAppAndTypeAndOmitsCommandField() {
        byte[] payment = ScriptBuilder.createOutputScript(DESTINATION).getProgram();
        assertArrayEquals(payment, OrdScriptBuilder.buildInscription(DESTINATION, null, null, new ORDMap()).getProgram());
        ORDMap typeOnly = new ORDMap();
        typeOnly.put("type", "image");
        assertArrayEquals(payment, OrdScriptBuilder.buildInscription(DESTINATION, null, null, typeOnly).getProgram());
        ORDMap incomplete = new ORDMap();
        incomplete.put("app", "test-app");
        assertArrayEquals(payment, OrdScriptBuilder.buildInscription(DESTINATION, null, null, incomplete).getProgram());
        incomplete.put("type", "");
        assertArrayEquals(payment, OrdScriptBuilder.buildInscription(DESTINATION, null, null, incomplete).getProgram());

        ORDMap metadata = new ORDMap();
        metadata.put("app", "test-app");
        metadata.put("type", "image");
        metadata.put("cmd", "do-not-serialize");
        metadata.put("context", "photo");
        Script script = OrdScriptBuilder.buildInscription(DESTINATION, null, null, metadata);
        List<ScriptChunk> chunks = script.getChunks();
        assertEquals(14, chunks.size());
        assertTrue(chunks.get(5).equalsOpCode(OP_RETURN));
        assertArrayEquals(OrdScriptBuilder.MAP_PREFIX.getBytes(StandardCharsets.UTF_8), chunks.get(6).data());
        assertArrayEquals("SET".getBytes(StandardCharsets.UTF_8), chunks.get(7).data());
        assertArrayEquals("app".getBytes(StandardCharsets.UTF_8), chunks.get(8).data());
        assertArrayEquals("test-app".getBytes(StandardCharsets.UTF_8), chunks.get(9).data());
        assertArrayEquals("type".getBytes(StandardCharsets.UTF_8), chunks.get(10).data());
        assertArrayEquals("image".getBytes(StandardCharsets.UTF_8), chunks.get(11).data());
        assertArrayEquals("context".getBytes(StandardCharsets.UTF_8), chunks.get(12).data());
        assertArrayEquals("photo".getBytes(StandardCharsets.UTF_8), chunks.get(13).data());
        assertFalse(chunks.stream().anyMatch(chunk ->
                chunk.data() != null && "do-not-serialize".equals(new String(chunk.data(), StandardCharsets.UTF_8))));
    }
}
