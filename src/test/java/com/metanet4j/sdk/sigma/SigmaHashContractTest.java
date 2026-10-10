package com.metanet4j.sdk.sigma;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.hutool.core.codec.Base64;
import com.metanet4j.base.bsv.BsvAmount;
import com.metanet4j.base.bsv.BsvHashBytes;
import com.metanet4j.bsv.transaction.Transaction;
import com.metanet4j.bsv.transaction.TransactionInput;
import com.metanet4j.bsv.transaction.TransactionOutput;
import com.metanet4j.bsv.script.OP;
import com.metanet4j.bsv.script.Script;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SigmaHashContractTest {

    @Test
    void hashesOutpointAndUnsignedOutputScript() throws Exception {
        byte[] prevHash = new byte[32];
        prevHash[0] = 42;
        Script script = new Script().writeBin(toBoxed("payload".getBytes(StandardCharsets.US_ASCII)));
        Transaction tx = transaction(prevHash, 7, script);
        Sigma sigma = new Sigma(null, tx);

        byte[] expectedInput = sha256(ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN)
                .put(prevHash).putInt(7).array());
        byte[] expectedData = sha256(script.toUint8Array());
        byte[] combined = ByteBuffer.allocate(64).put(expectedInput).put(expectedData).array();
        assertArrayEquals(expectedInput, sigma.getInputHash().getBytes());
        assertArrayEquals(expectedData, sigma.getDataHash().getBytes());
        assertArrayEquals(sha256(combined), sigma.getMessageHash().getBytes());
        assertNull(sigma.getSig());
        assertEquals(0, sigma.getSigInstanceCount());
        assertEquals(-1, sigma.getSigInstancePosition());
        assertSame(tx.outputs.get(0), sigma.getTargetTxOut());
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
        Script before = new Script().writeBin(toBoxed("payload".getBytes(StandardCharsets.US_ASCII)));
        Script signed = new Script()
                .writeBin(toBoxed("payload".getBytes(StandardCharsets.US_ASCII)))
                .writeOpCode(OP.OP_RETURN)
                .writeBin(toBoxed("SIGMA".getBytes(StandardCharsets.US_ASCII)))
                .writeBin(toBoxed("BSM".getBytes(StandardCharsets.US_ASCII)))
                .writeBin(toBoxed(address.getBytes(StandardCharsets.US_ASCII)))
                .writeBin(toBoxed(signature))
                .writeBin(toBoxed("0".getBytes(StandardCharsets.US_ASCII)))
                ;
        Transaction tx = transaction(new byte[32], 0, signed);
        Sigma sigma = new Sigma(null, tx);

        assertEquals(address, sigma.getSig().getAddress());
        assertEquals(Base64.encode(signature), sigma.getSig().getSignature());
        assertEquals("BSM", sigma.getSig().getAlgorithm());
        assertEquals(0, sigma.getSig().getVin());
        assertEquals(1, sigma.getSigInstanceCount());
        assertEquals(2, sigma.getSigInstancePosition());
        assertArrayEquals(sha256(before.toUint8Array()), sigma.getDataHash().getBytes());
        assertFalse(sigma.verify());
    }

    @Test
    void missingInputUsesTheDocumentedDummyHash() {
        Transaction tx = new Transaction();
        tx.outputs.add(new TransactionOutput(
                new Script().writeBin(toBoxed("payload".getBytes(StandardCharsets.US_ASCII))), 100d));
        Sigma sigma = new Sigma(null, tx);
        assertEquals(BsvHashBytes.ZERO_HASH, sigma.getInputHash());
        sigma.setRefVin(-2);
        assertEquals(BsvHashBytes.ZERO_HASH, sigma.getInputHash());
        sigma.setRefVin(0);
        tx.inputs.add(null);
        assertEquals(BsvHashBytes.ZERO_HASH, sigma.getInputHash());
    }

    private static Transaction transaction(byte[] prevHash, long index, Script outputScript) {
        Transaction tx = new Transaction();
        TransactionInput input = new TransactionInput(new BsvHashBytes(prevHash).toString(),
                (int) index, new Script());
        input.sequence = 0xffff_ffffL;
        tx.inputs.add(input);
        tx.outputs.add(new TransactionOutput(outputScript, 100d));
        return tx;
    }

    private static List<Integer> toBoxed(byte[] data) {
        List<Integer> out = new java.util.ArrayList<>(data.length);
        for (byte b : data) {
            out.add(b & 0xFF);
        }
        return out;
    }

    private static byte[] sha256(byte[] bytes) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(bytes);
    }
}
