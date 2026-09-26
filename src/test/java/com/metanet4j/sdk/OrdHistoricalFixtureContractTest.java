package com.metanet4j.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.hutool.core.util.HexUtil;
import com.alibaba.fastjson.JSONArray;
import com.metanet4j.sdk.bap.BapBase;
import com.metanet4j.sdk.sigma.Sigma;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.params.Net;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OrdHistoricalFixtureContractTest {

    @Test
    void reInscriptionCarriesGltfAndMap() throws IOException {
        Converted tx = convert("8e8378.hex",
                "662104c5bb6a9e912f260795f5d770e670f0a966bc6fd89fe8c0e88fc78e8378", 2, 2);
        assertEquals(1L, value(tx.txo(), 0));
        assertEquals("ord", output(tx.txo(), 0).get("s7"));
        assertEquals("model/gltf-binary", output(tx.txo(), 0).get("s9"));
        assertEquals("ord-demo", output(tx.txo(), 0).get("s17"));
        assertEquals("dree547h7", output(tx.txo(), 0).get("s23"));
        assertEquals(268302L, value(tx.bob(), 1));
    }

    @Test
    void opnsCarriesTextMapAndSigma() throws IOException {
        Converted tx = convert("opns_0b8704.hex",
                "76441b4865ff50b937ce0467544e25064d2bbdc54457e94902c4de82b30b8704", 3, 4);
        assertEquals("text/plain;charset=utf-8", output(tx.txo(), 0).get("s9"));
        assertEquals("my god", output(tx.txo(), 0).get("s11"));
        assertEquals("aym", output(tx.txo(), 0).get("s17"));
        assertEquals("SIGMA", output(tx.txo(), 0).get("s23"));
        assertEquals("BSM", output(tx.txo(), 0).get("s24"));
        assertEquals(7399725L, value(tx.bob(), 3));
    }

    @Test
    void pngInscriptionCarriesPngSignature() throws IOException {
        Converted tx = convert("26a81e.hex",
                "e749a32652a69601ee407ccda605d4eb19d93bddf5b355e1b1cae383bc26a81e", 1, 2);
        assertEquals("ord", output(tx.txo(), 0).get("s7"));
        assertEquals("image/png", output(tx.txo(), 0).get("s9"));
        assertEquals("89504e470d0a1a0a", String.valueOf(output(tx.txo(), 0).get("h11")).substring(0, 16));
        assertEquals(1L, value(tx.bob(), 0));
        assertEquals(9539L, value(tx.txo(), 1));
    }

    @Test
    void metadataInscriptionCarriesGltfAndGeohash() throws IOException {
        assertGltfMetadata("2809d6.hex");
    }

    @Test
    void ordinaryInscriptionFixtureMatchesTheMetadataVector() throws IOException {
        assertEquals(readFixture("2809d6.hex"), readFixture("ord1.hex"));
    }

    @Test
    void sigmaFixtureVerifiesItsFixedSignedMessageAddress() throws IOException {
        String hex = readFixture("040c6d48c.hex");
        Converted tx = convert("040c6d48c.hex",
                "db6d12611fbbbacf5ae652deee3a8e018d93eaa048348fb49f76173040c6d48c", 1, 2);
        assertEquals(1L, value(tx.txo(), 0));
        assertEquals("SIGMA", output(tx.txo(), 0).get("s27"));
        assertEquals("BSM", output(tx.txo(), 0).get("s28"));
        assertEquals("12cxrp1b47DgxtxisBCzuL24QHdkrAKf8Y", output(tx.txo(), 0).get("s29"));

        Transaction transaction = new Transaction(Net.MAINNET, HexUtil.decodeHex(hex));
        assertTrue(new Sigma(BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey), transaction).verify());
    }

    @Test
    void transferHasOneSatDestinationWithoutNewInscription() throws IOException {
        Converted tx = convert("ord-transfer.hex",
                "61fd6e240610a9e9e071c34fc87569ef871760ea1492fe1225d668de4d76407e", 2, 2);
        assertEquals(1L, value(tx.txo(), 0));
        assertEquals("b87db78cba867b9f5def9f48d00ec732493ee543", output(tx.txo(), 0).get("h2"));
        assertEquals(5, output(tx.txo(), 0).get("len"));
        assertFalse(output(tx.txo(), 0).containsKey("s7"));
        assertEquals(269114L, value(tx.bob(), 1));
    }

    @Test
    void marketFixtureHasFourPaymentOutputsWithoutNewInscription() throws IOException {
        Converted tx = convert("offchain.hex",
                "a0787ffdf4414dac9cb5d248186a545d374fc28caf40b68780d22ced0f1d24d7", 4, 4);
        assertEquals(40L, value(tx.txo(), 0));
        assertEquals(1L, value(tx.txo(), 1));
        assertEquals(100000000L, value(tx.txo(), 2));
        assertEquals(27601L, value(tx.txo(), 3));
        assertEquals("5bd9baf4dc6270bad6e4152363bbeca2f5abc7c4", output(tx.txo(), 0).get("h2"));
        assertEquals(output(tx.txo(), 0).get("h2"), output(tx.txo(), 3).get("h2"));
        assertFalse(output(tx.txo(), 1).containsKey("s7"));
    }

    private static void assertGltfMetadata(String fixture) throws IOException {
        Converted tx = convert(fixture,
                "10f4465cd18c39fbc7aa4089268e57fc719bf19c8c24f2e09156f4a89a2809d6", 1, 2);
        assertEquals("ord", output(tx.txo(), 0).get("s7"));
        assertEquals("model/gltf-binary", output(tx.txo(), 0).get("s9"));
        assertEquals("glTF", String.valueOf(output(tx.txo(), 0).get("s11")).substring(0, 4));
        assertEquals("ord-demo", output(tx.txo(), 0).get("s17"));
        assertEquals("geohash", output(tx.txo(), 0).get("s21"));
        assertEquals("dhxnd1pwn", output(tx.txo(), 0).get("s23"));
        assertEquals(269156L, value(tx.bob(), 1));
    }

    private static Converted convert(String fixture, String txid, int inputs, int outputs) throws IOException {
        String hex = readFixture(fixture);
        Map<String, Object> bob = BobHelper.toBob(hex);
        Map<String, Object> txo = TxoHelper.toTxo(hex);
        assertEquals(txid, field(bob, "tx").get("h"), fixture);
        assertEquals(txid, field(txo, "tx").get("h"), fixture);
        assertEquals(inputs, array(bob, "in").size(), fixture);
        assertEquals(inputs, array(txo, "in").size(), fixture);
        assertEquals(outputs, array(bob, "out").size(), fixture);
        assertEquals(outputs, array(txo, "out").size(), fixture);
        assertEquals(1, ((Number) bob.get("v")).intValue(), fixture);
        assertEquals(0, ((Number) bob.get("lock")).intValue(), fixture);
        return new Converted(bob, txo);
    }

    private static String readFixture(String fixture) throws IOException {
        String path = "/ord/" + fixture;
        try (InputStream stream = OrdHistoricalFixtureContractTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }

    private static long value(Map<String, Object> tx, int index) {
        return ((Number) field(output(tx, index), "e").get("v")).longValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> field(Map<String, Object> item, String key) {
        return (Map<String, Object>) item.get(key);
    }

    private static JSONArray array(Map<String, Object> item, String key) {
        return (JSONArray) item.get(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> output(Map<String, Object> tx, int index) {
        return (Map<String, Object>) array(tx, "out").get(index);
    }

    private record Converted(Map<String, Object> bob, Map<String, Object> txo) {}
}
