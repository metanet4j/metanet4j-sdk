package com.metanet4j.sdk.sigma;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        // sourceTXID 是**显示序 hex**，而 Sigma.getInputHash 会把它的字节**原样**放入 outpoint
        // （与旧库 input.getOutpoint().getHash().getBytes() 同序，见 §57 的基线直跑取证）。
        // 故此处须用「不做反转」的 hex 编码，使原样解码后恰好还原传入的 prevHash——
        // 若写成 new BsvHashBytes(prevHash).toString()（其 toString 会反转），解码后即与传入字节相反。
        TransactionInput input = new TransactionInput(
                cn.hutool.core.util.HexUtil.encodeHexStr(prevHash), (int) index, new Script());
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

    @Test
    void rejectsUnresolvedAndNullSourceTxidWithItsCause() {
        // 覆盖 Sigma.sourceTxidBytes 在 §86 收敛后保留的两条出口（§86.3「保留抛错兜底」）：
        // :409 `instanceof String` 的**假侧**、:423 的抛错、以及 :424 中 **null 与非 null 两种取名分支**。
        // 这两种输入都是**真实可达**的：字段默认是哨兵 Undefined（从未解析），也可能被显式置为 null。
        Transaction tx = new Transaction();
        TransactionInput input = new TransactionInput(
                com.metanet4j.bsv.primitives.Undefined.INSTANCE, 0, new Script());
        tx.inputs.add(input);
        tx.outputs.add(new TransactionOutput(new Script(), 1d));
        // ① 未解析（哨兵 Undefined）→ 抛错，消息中应含其类型名。
        // 注：**异常在构造 Sigma 时即抛出**（构造器会预计算 inputHash），故 assertThrows 包裹的是构造调用。
        IllegalArgumentException unresolved = assertThrows(IllegalArgumentException.class,
                () -> new Sigma(null, tx), "未解析的 sourceTXID 应报错而非静默产出错误哈希");
        assertTrue(unresolved.getMessage().contains("Undefined"),
                "消息应指明实际类型，便于诊断；实际为：" + unresolved.getMessage());

        // ② 显式 null → 同样抛错，且消息中为 "null"（**不在报错路径上再触发 NPE**）
        input.sourceTXID = null;
        IllegalArgumentException nullCase = assertThrows(IllegalArgumentException.class,
                () -> new Sigma(null, tx), "null 的 sourceTXID 亦应报错");
        assertTrue(nullCase.getMessage().contains("null"),
                "null 情形应安全取名；实际为：" + nullCase.getMessage());
    }

}
