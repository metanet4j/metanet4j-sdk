package com.metanet4j.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.hutool.core.util.HexUtil;
import com.alibaba.fastjson.JSONArray;
import io.bitcoinsv.bitcoinjsv.core.Address;
import io.bitcoinsv.bitcoinjsv.core.Coin;
import io.bitcoinsv.bitcoinjsv.core.ECKey;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.params.MainNetParams;
import io.bitcoinsv.bitcoinjsv.params.Net;
import io.bitcoinsv.bitcoinjsv.script.Script;
import io.bitcoinsv.bitcoinjsv.script.ScriptBuilder;
import io.bitcoinsv.bitcoinjsv.script.ScriptChunk;
import io.bitcoinsv.bitcoinjsv.script.ScriptOpCodes;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BobTxoConversionContractTest {

    @Test
    void convertsP2pkhInputAndOutputToPublicAddressAndValueFields() {
        ECKey key = ECKey.fromPrivate(BigInteger.TWO);
        Address address = key.toAddress(MainNetParams.get());
        byte[] previousHash = new byte[32];
        previousHash[0] = 42;
        Transaction tx = new Transaction(Net.MAINNET);
        tx.addInput(Sha256Hash.wrap(previousHash), 7,
                new ScriptBuilder().data(new byte[] {48, 1}).data(key.getPubKey()).build())
                .setSequenceNumber(11);
        tx.addOutput(Coin.valueOf(546), address);
        String hex = HexUtil.encodeHexStr(tx.bitcoinSerialize());

        Map<String, Object> bob = BobHelper.toBob(hex);
        Map<String, Object> txo = TxoHelper.toTxo(hex);
        assertEquals(tx.getHashAsString(), field(bob, "tx").get("h"));
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
        Transaction tx = new Transaction(Net.MAINNET);
        tx.addInput(Sha256Hash.wrap(new byte[32]), 0, new ScriptBuilder().build());
        tx.addOutput(Coin.valueOf(0), new ScriptBuilder().op(ScriptOpCodes.OP_RETURN)
                .data("alpha".getBytes(StandardCharsets.UTF_8))
                .data("|".getBytes(StandardCharsets.UTF_8))
                .data("beta".getBytes(StandardCharsets.UTF_8)).build());
        tx.addOutput(Coin.valueOf(0), new ScriptBuilder()
                .addChunk(new ScriptChunk(ScriptOpCodes.OP_FALSE, null))
                .op(ScriptOpCodes.OP_RETURN)
                .data("genesis".getBytes(StandardCharsets.UTF_8)).build());
        String hex = HexUtil.encodeHexStr(tx.bitcoinSerialize());

        Map<String, Object> bobOutput = item(BobHelper.toBob(hex), "out", 0);
        Map<String, Object> txoOutput = item(TxoHelper.toTxo(hex), "out", 0);
        assertTrue(String.valueOf(field(bobOutput, "e").get("a")).contains("RETURN"));
        assertTrue(String.valueOf(field(txoOutput, "e").get("a")).contains("RETURN"));
        assertEquals("false", field(item(BobHelper.toBob(hex), "out", 1), "e").get("a"));
        assertEquals("false", field(item(TxoHelper.toTxo(hex), "out", 1), "e").get("a"));
        assertEquals("alpha", txoOutput.get("s1"));
        assertEquals("|", txoOutput.get("s2"));
        assertEquals("beta", txoOutput.get("s3"));

        List<Map<String, Object>> tape = tape(bobOutput);
        assertEquals(3, tape.size());
        assertEquals(ScriptOpCodes.OP_RETURN, cell(tape.get(0), 0).get("op"));
        assertEquals("alpha", cell(tape.get(1), 0).get("s"));
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
        Transaction tx = new Transaction(Net.MAINNET);
        tx.addInput(Sha256Hash.wrap(new byte[32]), 2,
                new ScriptBuilder().op(ScriptOpCodes.OP_1).build());
        tx.addOutput(Coin.valueOf(0), new ScriptBuilder()
                .data("left".getBytes(StandardCharsets.UTF_8))
                .data("right".getBytes(StandardCharsets.UTF_8))
                .op(ScriptOpCodes.OP_RETURN)
                .data("tail".getBytes(StandardCharsets.UTF_8)).build());
        String hex = HexUtil.encodeHexStr(tx.bitcoinSerialize());

        Map<String, Object> bob = BobHelper.toBob(hex);
        Map<String, Object> txo = TxoHelper.toTxo(hex);
        assertEquals(64, String.valueOf(field(item(bob, "in", 0), "e").get("a")).length());
        assertTrue(String.valueOf(field(item(txo, "in", 0), "e").get("a")).contains("1"));
        assertEquals(ScriptOpCodes.OP_1, cell(tape(item(bob, "in", 0)).get(0), 0).get("op"));
        assertEquals("left", item(txo, "out", 0).get("s0"));
        assertEquals("right", item(txo, "out", 0).get("s1"));
        assertEquals("tail", item(txo, "out", 0).get("s3"));

        List<Map<String, Object>> tape = tape(item(bob, "out", 0));
        assertEquals(3, tape.size());
        assertEquals("left", cell(tape.get(0), 0).get("s"));
        assertEquals("right", cell(tape.get(0), 1).get("s"));
        assertEquals(ScriptOpCodes.OP_RETURN, cell(tape.get(1), 0).get("op"));
        assertEquals("tail", cell(tape.get(2), 0).get("s"));
    }

    @Test
    void keepsFalseReturnPairTogetherAfterEarlierPayload() {
        Transaction tx = new Transaction(Net.MAINNET);
        tx.addInput(Sha256Hash.wrap(new byte[32]), 0, new ScriptBuilder().build());
        tx.addOutput(Coin.valueOf(0), new ScriptBuilder()
                .data("left".getBytes(StandardCharsets.UTF_8))
                .data("right".getBytes(StandardCharsets.UTF_8))
                .addChunk(new ScriptChunk(ScriptOpCodes.OP_FALSE, null))
                .op(ScriptOpCodes.OP_RETURN)
                .data("tail".getBytes(StandardCharsets.UTF_8)).build());

        List<Map<String, Object>> tape = tape(item(BobHelper.toBob(
                HexUtil.encodeHexStr(tx.bitcoinSerialize())), "out", 0));
        assertEquals(3, tape.size());
        assertEquals(ScriptOpCodes.OP_FALSE, cell(tape.get(1), 0).get("op"));
        assertEquals(ScriptOpCodes.OP_RETURN, cell(tape.get(1), 1).get("op"));
        assertEquals("tail", cell(tape.get(2), 0).get("s"));
    }

    @Test
    void opcodeOnlyInputFallsBackToScriptIdentity() {
        Script inputScript = new ScriptBuilder().op(ScriptOpCodes.OP_DUP)
                .op(ScriptOpCodes.OP_HASH160).build();
        Transaction tx = new Transaction(Net.MAINNET);
        tx.addInput(Sha256Hash.wrap(new byte[32]), 0, inputScript);
        tx.addOutput(Coin.valueOf(1), new ScriptBuilder().data(new byte[] {1}).build());
        String hex = HexUtil.encodeHexStr(tx.bitcoinSerialize());

        assertEquals(Sha256Hash.of(inputScript.getProgram()).toString(),
                field(item(BobHelper.toBob(hex), "in", 0), "e").get("a"));
        assertEquals(inputScript.toString(),
                field(item(TxoHelper.toTxo(hex), "in", 0), "e").get("a"));
    }

    @Test
    void zeroPushInputIsReportedAsAnOpcode() {
        Transaction tx = new Transaction(Net.MAINNET);
        tx.addInput(Sha256Hash.wrap(new byte[32]), 0,
                new ScriptBuilder().addChunk(new ScriptChunk(ScriptOpCodes.OP_FALSE, null)).build());
        tx.addOutput(Coin.valueOf(1), new ScriptBuilder().data(new byte[] {1}).build());

        Map<String, Object> input = item(TxoHelper.toTxo(
                HexUtil.encodeHexStr(tx.bitcoinSerialize())), "in", 0);
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
}
