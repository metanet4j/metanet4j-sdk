package com.metanet4j.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.hutool.core.util.HexUtil;
import com.alibaba.fastjson.JSONArray;
import com.metanet4j.base.bsv.BsvAmount;
import com.metanet4j.base.bsv.BsvHashBytes;
import com.metanet4j.bsv.script.OP;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.script.ScriptChunk;
import com.metanet4j.bsv.script.templates.P2PKH;
import com.metanet4j.bsv.transaction.Transaction;
import com.metanet4j.bsv.transaction.TransactionInput;
import com.metanet4j.bsv.transaction.TransactionOutput;
import com.metanet4j.sdk.PrivateKey;
import com.metanet4j.sdk.address.AddressEnhance;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BobTxoConversionContractTest {

    @Test
    void convertsP2pkhInputAndOutputToPublicAddressAndValueFields() {
        PrivateKey key = PrivateKey.of(BigInteger.TWO);
        AddressEnhance address = AddressEnhance.fromPrivateKey(key);
        byte[] previousHash = new byte[32];
        previousHash[0] = 42;
        Transaction tx = new Transaction();
        tx.inputs.add(new TransactionInput(new BsvHashBytes(previousHash).toString(), 7,
                new Script().writeBin(toBoxed(new byte[] {48, 1}))
                        .writeBin(toBoxed(key.getPubKey()))));
        tx.inputs.get(0).sequence = 11L;
        tx.outputs.add(new TransactionOutput(new P2PKH().lock(address.toBase58()), 546d));
        String hex = HexUtil.encodeHexStr(toBytes(tx.toBinary()));

        Map<String, Object> bob = BobHelper.toBob(hex);
        Map<String, Object> txo = TxoHelper.toTxo(hex);
        assertEquals(tx.id("hex"), field(bob, "tx").get("h"));
        assertEquals(field(bob, "tx"), field(txo, "tx"));
        assertEquals(1, items(bob, "in").size());
        assertEquals(1, items(txo, "out").size());

        Map<String, Object> bobInput = item(bob, "in", 0);
        Map<String, Object> txoInput = item(txo, "in", 0);
        assertEquals(0, bobInput.get("i"));
        assertEquals(11L, ((Number) bobInput.get("seq")).longValue());
        assertEquals(7L, ((Number) field(bobInput, "e").get("i")).longValue());
        assertEquals(address.toBase58(), field(bobInput, "e").get("a"));
        assertEquals(address.toBase58(), field(txoInput, "e").get("a"));
        assertEquals(2, bobInput.get("len"));
        assertEquals(HexUtil.encodeHexStr(key.getPubKey()), txoInput.get("h1"));

        assertEquals(546L, ((Number) field(item(bob, "out", 0), "e").get("v")).longValue());
        assertEquals(address.toBase58(), field(item(bob, "out", 0), "e").get("a"));
        assertEquals(address.toBase58(), field(item(txo, "out", 0), "e").get("a"));
    }

    @Test
    void separatesOpReturnTapeAndKeepsPayloadInTxoFields() {
        Transaction tx = new Transaction();
        tx.inputs.add(new TransactionInput(new BsvHashBytes(new byte[32]).toString(), 0, new Script()));
        tx.outputs.add(new TransactionOutput(new Script().writeOpCode(OP.OP_RETURN)
                .writeBin(toBoxed("alpha".getBytes(StandardCharsets.UTF_8)))
                .writeBin(toBoxed("|".getBytes(StandardCharsets.UTF_8)))
                .writeBin(toBoxed("beta".getBytes(StandardCharsets.UTF_8))), 0d));
        tx.outputs.add(new TransactionOutput(new Script()
                .writeOpCode(OP.OP_0)
                .writeOpCode(OP.OP_RETURN)
                .writeBin(toBoxed("genesis".getBytes(StandardCharsets.UTF_8))), 0d));
        String hex = HexUtil.encodeHexStr(toBytes(tx.toBinary()));

        Map<String, Object> bobOutput = item(BobHelper.toBob(hex), "out", 0);
        Map<String, Object> txoOutput = item(TxoHelper.toTxo(hex), "out", 0);
        assertTrue(String.valueOf(field(bobOutput, "e").get("a")).contains("RETURN"));
        assertTrue(String.valueOf(field(txoOutput, "e").get("a")).contains("RETURN"));
        assertEquals("false", field(item(BobHelper.toBob(hex), "out", 1), "e").get("a"));
        assertEquals("false", field(item(TxoHelper.toTxo(hex), "out", 1), "e").get("a"));
        assertEquals("alpha", txoOutput.get("s1"));
        assertEquals("YWxwaGE=", txoOutput.get("b1"));
        assertEquals("616c706861", txoOutput.get("h1"));
        assertEquals("|", txoOutput.get("s2"));
        assertEquals("beta", txoOutput.get("s3"));

        List<Map<String, Object>> tape = tape(bobOutput);
        assertEquals(3, tape.size());
        assertEquals(OP.OP_RETURN, cell(tape.get(0), 0).get("op"));
        assertEquals("alpha", cell(tape.get(1), 0).get("s"));
        assertEquals("YWxwaGE=", cell(tape.get(1), 0).get("b"));
        assertEquals("616c706861", cell(tape.get(1), 0).get("h"));
        assertEquals(1, cells(tape.get(1)).size());
        assertEquals("beta", cell(tape.get(2), 0).get("s"));

        List<Map<String, Object>> unsplitTape = tape(item(BobHelper.toBob(hex, "::"), "out", 0));
        assertEquals(2, unsplitTape.size());
        assertEquals("|", cell(unsplitTape.get(1), 1).get("s"));
    }

    @Test
    void rejectsIncompleteRawTransaction() {
        assertThrows(RuntimeException.class, () -> BobHelper.toBob("00"));
        assertThrows(RuntimeException.class, () -> TxoHelper.toTxo("00"));
    }

    @Test
    void keepsNonP2pkhInputAndOpReturnAfterEarlierData() {
        Transaction tx = new Transaction();
        tx.inputs.add(new TransactionInput(new BsvHashBytes(new byte[32]).toString(), 2,
                new Script().writeOpCode(OP.OP_1)));
        tx.outputs.add(new TransactionOutput(new Script()
                .writeBin(toBoxed("left".getBytes(StandardCharsets.UTF_8)))
                .writeBin(toBoxed("right".getBytes(StandardCharsets.UTF_8)))
                .writeOpCode(OP.OP_RETURN)
                .writeBin(toBoxed("tail".getBytes(StandardCharsets.UTF_8))), 0d));
        String hex = HexUtil.encodeHexStr(toBytes(tx.toBinary()));

        Map<String, Object> bob = BobHelper.toBob(hex);
        Map<String, Object> txo = TxoHelper.toTxo(hex);
        assertEquals(64, String.valueOf(field(item(bob, "in", 0), "e").get("a")).length());
        assertTrue(String.valueOf(field(item(txo, "in", 0), "e").get("a")).contains("1"));
        assertEquals(OP.OP_1, cell(tape(item(bob, "in", 0)).get(0), 0).get("op"));
        assertEquals("left", item(txo, "out", 0).get("s0"));
        assertEquals("right", item(txo, "out", 0).get("s1"));
        assertEquals("tail", item(txo, "out", 0).get("s3"));

        List<Map<String, Object>> tape = tape(item(bob, "out", 0));
        assertEquals(3, tape.size());
        assertEquals("left", cell(tape.get(0), 0).get("s"));
        assertEquals("right", cell(tape.get(0), 1).get("s"));
        assertEquals(OP.OP_RETURN, cell(tape.get(1), 0).get("op"));
        assertEquals("tail", cell(tape.get(2), 0).get("s"));
    }

    @Test
    void keepsFalseReturnPairTogetherAfterEarlierPayload() {
        Transaction tx = new Transaction();
        tx.inputs.add(new TransactionInput(new BsvHashBytes(new byte[32]).toString(), 0, new Script()));
        tx.outputs.add(new TransactionOutput(new Script()
                .writeBin(toBoxed("left".getBytes(StandardCharsets.UTF_8)))
                .writeBin(toBoxed("right".getBytes(StandardCharsets.UTF_8)))
                .writeOpCode(OP.OP_0)
                .writeOpCode(OP.OP_RETURN)
                .writeBin(toBoxed("tail".getBytes(StandardCharsets.UTF_8))), 0d));

        List<Map<String, Object>> tape = tape(item(BobHelper.toBob(
                HexUtil.encodeHexStr(toBytes(tx.toBinary()))), "out", 0));
        assertEquals(3, tape.size());
        assertEquals(OP.OP_0, cell(tape.get(1), 0).get("op"));
        assertEquals(OP.OP_RETURN, cell(tape.get(1), 1).get("op"));
        assertEquals("tail", cell(tape.get(2), 0).get("s"));
    }

    @Test
    void opcodeOnlyInputFallsBackToScriptIdentity() {
        Script inputScript = new Script().writeOpCode(OP.OP_DUP)
                .writeOpCode(OP.OP_HASH160);
        Transaction tx = new Transaction();
        tx.inputs.add(new TransactionInput(new BsvHashBytes(new byte[32]).toString(), 0, inputScript));
        tx.outputs.add(new TransactionOutput(
                new Script().writeBin(toBoxed(new byte[] {1})), 1d));
        String hex = HexUtil.encodeHexStr(toBytes(tx.toBinary()));

        // 旧库 Sha256Hash.toString() 输出**显示序**（§108 实测：等于 BsvHashBytes.getBytes() 的 hex），
        // 故此处按显示序 hex 断言，而非用会反转的 BsvHashBytes.toString()。
        assertEquals(HexUtil.encodeHexStr(
                        com.metanet4j.base.bsv.BsvHashBytes.of(inputScript.toUint8Array()).getBytes()),
                field(item(BobHelper.toBob(hex), "in", 0), "e").get("a"));
        // 基线此处为 assertEquals(inputScript.toString(), ...)，旧库 Script.toString() 输出**可读操作码名**
        // （如 "DUP HASH160"）。新 SDK 的 Script.toString() 是默认对象实现，故按基线语义直接写字面量
        // ——这也正是 §109 让 TxoHelper 输出可读操作码名所对齐的形式。
        assertEquals("DUP HASH160",
                field(item(TxoHelper.toTxo(hex), "in", 0), "e").get("a"));
    }

    @Test
    void zeroPushInputIsReportedAsAnOpcode() {
        Transaction tx = new Transaction();
        tx.inputs.add(new TransactionInput(new BsvHashBytes(new byte[32]).toString(), 0,
                new Script().writeOpCode(OP.OP_0)));
        tx.outputs.add(new TransactionOutput(
                new Script().writeBin(toBoxed(new byte[] {1})), 1d));

        Map<String, Object> input = item(TxoHelper.toTxo(
                HexUtil.encodeHexStr(toBytes(tx.toBinary()))), "in", 0);
        assertTrue(input.containsKey("o0"));
        assertTrue(String.valueOf(input.get("o0")).startsWith("OP_"));
        assertEquals(1, input.get("len"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> field(Map<String, Object> item, String name) {
        return (Map<String, Object>) item.get(name);
    }

    private static JSONArray items(Map<String, Object> transaction, String name) {
        return (JSONArray) transaction.get(name);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> item(Map<String, Object> transaction, String name, int index) {
        return (Map<String, Object>) items(transaction, name).get(index);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> tape(Map<String, Object> output) {
        return (List<Map<String, Object>>) output.get("tape");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> cells(Map<String, Object> tapeItem) {
        return (List<Map<String, Object>>) tapeItem.get("cell");
    }

    private static Map<String, Object> cell(Map<String, Object> tapeItem, int index) {
        return cells(tapeItem).get(index);
    }

    private static List<Integer> toBoxed(byte[] data) {
        List<Integer> out = new ArrayList<>(data.length);
        for (byte b : data) {
            out.add(b & 0xFF);
        }
        return out;
    }


    private static byte[] toBytes(List<Integer> values) {
        byte[] out = new byte[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) (values.get(i) & 0xFF);
        }
        return out;
    }

}
