package com.metanet4j.sdk.sigma;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.hutool.core.codec.Base64;
import io.bitcoinsv.bitcoinjsv.core.Coin;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.msg.protocol.TransactionInput;
import io.bitcoinsv.bitcoinjsv.params.Net;
import io.bitcoinsv.bitcoinjsv.script.Script;
import io.bitcoinsv.bitcoinjsv.script.ScriptBuilder;
import io.bitcoinsv.bitcoinjsv.script.ScriptOpCodes;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SigmaHashContractTest {

    @Test
    void hashesOutpointAndUnsignedOutputScript() throws Exception {
        byte[] prevHash = new byte[32];
        prevHash[0] = 42;
        Script script = new ScriptBuilder().data("payload".getBytes(StandardCharsets.US_ASCII)).build();
        Transaction tx = transaction(prevHash, 7, script);
        Sigma sigma = new Sigma(null, tx);

        byte[] expectedInput = sha256(ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN)
                .put(prevHash).putInt(7).array());
        byte[] expectedData = sha256(script.getProgram());
        byte[] combined = ByteBuffer.allocate(64).put(expectedInput).put(expectedData).array();
        assertArrayEquals(expectedInput, sigma.getInputHash().getBytes());
        assertArrayEquals(expectedData, sigma.getDataHash().getBytes());
        assertArrayEquals(sha256(combined), sigma.getMessageHash().getBytes());
        assertNull(sigma.getSig());
        assertEquals(0, sigma.getSigInstanceCount());
        assertEquals(-1, sigma.getSigInstancePosition());
        assertSame(tx.getOutput(0), sigma.getTargetTxOut());
        assertSame(tx, sigma.getTransaction());
        assertThrows(IllegalArgumentException.class, sigma::verify);

        sigma.setInputHash(null);
        assertThrows(IllegalStateException.class, sigma::getMessageHash);
        sigma.setTransaction(tx);
        sigma.setDataHash(null);
        assertThrows(IllegalStateException.class, sigma::getMessageHash);
        sigma.setHashes();
        sigma.setTargetVout(0);
        sigma.setSigmaInstance(0);
        assertEquals(0, sigma.getTargetVout());
        assertEquals(0, sigma.getRefVin());
        assertEquals(0, sigma.getSigmaInstance());
        assertArrayEquals(expectedInput, sigma.getInputHash().getBytes());
        assertNotNull(new Sigma(null, tx, 0, 0, -1).getMessageHash());
        assertNotNull(new Sigma(null, tx, 0).getMessageHash());
        assertThrows(NullPointerException.class, () -> new Sigma(null, null));
        assertThrows(NullPointerException.class, () -> sigma.setTransaction(null));
        assertSame(tx, sigma.getTransaction());
    }

    @Test
    void parsesSigmaPushesAndHashesOnlyPriorData() throws Exception {
        String address = "1cMh228HTCiwS8ZsaakH8A8wze1JR5ZsP";
        byte[] signature = {1, 2, 3};
        Script before = new ScriptBuilder().data("payload".getBytes(StandardCharsets.US_ASCII)).build();
        Script signed = new ScriptBuilder()
                .data("payload".getBytes(StandardCharsets.US_ASCII))
                .op(ScriptOpCodes.OP_RETURN)
                .data("SIGMA".getBytes(StandardCharsets.US_ASCII))
                .data("BSM".getBytes(StandardCharsets.US_ASCII))
                .data(address.getBytes(StandardCharsets.US_ASCII))
                .data(signature)
                .data("0".getBytes(StandardCharsets.US_ASCII))
                .build();
        Transaction tx = transaction(new byte[32], 0, signed);
        Sigma sigma = new Sigma(null, tx);

        assertEquals(address, sigma.getSig().getAddress());
        assertEquals(Base64.encode(signature), sigma.getSig().getSignature());
        assertEquals("BSM", sigma.getSig().getAlgorithm());
        assertEquals(0, sigma.getSig().getVin());
        assertEquals(1, sigma.getSigInstanceCount());
        assertEquals(2, sigma.getSigInstancePosition());
        assertArrayEquals(sha256(before.getProgram()), sigma.getDataHash().getBytes());
        assertFalse(sigma.verify());
    }

    @Test
    void missingInputUsesTheDocumentedDummyHash() {
        Transaction tx = new Transaction(Net.MAINNET);
        tx.addOutput(Coin.valueOf(100), new ScriptBuilder()
                .data("payload".getBytes(StandardCharsets.US_ASCII)).build());
        Sigma sigma = new Sigma(null, tx);
        assertEquals(Sha256Hash.of(new byte[32]), sigma.getInputHash());
        sigma.setRefVin(-2);
        assertEquals(Sha256Hash.of(new byte[32]), sigma.getInputHash());
        sigma.setRefVin(0);
        tx.setInputs(Arrays.asList((TransactionInput) null));
        assertEquals(Sha256Hash.of(new byte[32]), sigma.getInputHash());
    }

    private static Transaction transaction(byte[] prevHash, long index, Script outputScript) {
        Transaction tx = new Transaction(Net.MAINNET);
        tx.addInput(Sha256Hash.wrap(prevHash), index, new ScriptBuilder().build());
        tx.addOutput(Coin.valueOf(100), outputScript);
        return tx;
    }

    private static byte[] sha256(byte[] bytes) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(bytes);
    }
}
