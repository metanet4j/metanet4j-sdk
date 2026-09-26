package com.metanet4j.sdk.utils;

import static io.bitcoinsv.bitcoinjsv.script.ScriptOpCodes.OP_NOP;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.hutool.core.codec.Base64;
import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.bap.BapBase;
import com.metanet4j.sdk.ordinals.OrdScriptBuilder;
import com.metanet4j.sdk.ordinals.OrdScriptBuilder.ORDMap;
import io.bitcoinsv.bitcoinjsv.core.Address;
import io.bitcoinsv.bitcoinjsv.core.Coin;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.msg.protocol.TransactionOutPoint;
import io.bitcoinsv.bitcoinjsv.params.MainNetParams;
import io.bitcoinsv.bitcoinjsv.params.Net;
import io.bitcoinsv.bitcoinjsv.script.Script;
import io.bitcoinsv.bitcoinjsv.script.ScriptBuilder;
import io.bitcoinsv.bitcoinjsv.script.ScriptChunk;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TxHelperExtendContractTest {

    @Test
    void extractsDestinationFromInscriptionWithOptionalMapMetadata() {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        Address destination = bap.getOrdAddress();
        ORDMap metadata = new ORDMap();
        metadata.put("app", "test");
        metadata.put("type", "image");
        Script script = OrdScriptBuilder.buildInscription(destination,
                "body".getBytes(StandardCharsets.UTF_8), "text/plain", metadata);
        String encoded = Base64.encode(script.getProgram());

        assertTrue(TxHelperExtend.isSentToOrdinals(script));
        assertArrayEquals(destination.getHash160(), TxHelperExtend.getPubKeyHashByOrdinalScript(script));
        assertArrayEquals(destination.getHash160(), TxHelperExtend.getPubKeyHashByOrdinalScript(encoded));
        assertEquals(destination.toBase58(), TxHelperExtend.getAddressByOrdinalScript(script));
        assertEquals(destination.toBase58(), TxHelperExtend.getAddressByOrdinalScript(encoded));
    }

    @Test
    void rejectsOrdinaryAndIncompleteInscriptionScripts() {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        Address destination = bap.getOrdAddress();
        Script payment = ScriptBuilder.createOutputScript(destination);
        Script inscription = OrdScriptBuilder.buildInscription(destination,
                new byte[] {1}, "application/octet-stream", null);

        assertFalse(TxHelperExtend.isSentToOrdinals(payment));
        assertArrayEquals(new byte[0], TxHelperExtend.getPubKeyHashByOrdinalScript(payment));
        assertFalse(TxHelperExtend.isSentToOrdinals(new ScriptBuilder().build()));

        List<ScriptChunk> valid = inscription.getChunks();
        for (int invalidPosition = 0; invalidPosition < 13; invalidPosition++) {
            ScriptBuilder changed = new ScriptBuilder();
            for (int i = 0; i < valid.size(); i++) {
                if (i == invalidPosition) {
                    if (i == 2) {
                        changed.data(new byte[19]);
                    } else if (i == 7) {
                        changed.data("other".getBytes(StandardCharsets.US_ASCII));
                    } else {
                        changed.op(OP_NOP);
                    }
                } else {
                    changed.addChunk(valid.get(i));
                }
            }
            Script malformed = changed.build();
            assertFalse(TxHelperExtend.isSentToOrdinals(malformed), "chunk " + invalidPosition);
            assertArrayEquals(new byte[0], TxHelperExtend.getPubKeyHashByOrdinalScript(malformed));
        }
    }

    @Test
    void resolvesConnectedInscriptionAndOrdinaryPaymentThroughKeyBag() {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        BapBase.BapProviderKeyBag bag = new BapBase.BapProviderKeyBag(bap);
        Address destination = bap.getOrdAddress();
        Script inscription = OrdScriptBuilder.buildInscription(destination,
                new byte[] {1, 2}, "image/png", null);

        RedeemDataExtend ordinal = TxHelperExtend.getConnectedRedeemDataExtend(outpoint(inscription), bag);
        assertNotNull(ordinal);
        assertArrayEquals(inscription.getProgram(), ordinal.redeemScript.getProgram());
        assertEquals(bap.getOrdPrivateKey().getPrivKey(), ordinal.getFullKey().getPrivKey());

        Script payment = ScriptBuilder.createOutputScript(destination);
        RedeemDataExtend ordinary = TxHelperExtend.getConnectedRedeemDataExtend(outpoint(payment), bag);
        assertNotNull(ordinary);
        assertArrayEquals(payment.getProgram(), ordinary.redeemScript.getProgram());
        assertEquals(bap.getOrdPrivateKey().getPrivKey(), ordinary.getFullKey().getPrivKey());
    }

    @Test
    void rejectsDisconnectedOutputAndUnknownInscriptionKey() {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        BapBase.BapProviderKeyBag bag = new BapBase.BapProviderKeyBag(bap);
        TransactionOutPoint disconnected = new TransactionOutPoint(Net.MAINNET, 0,
                Sha256Hash.wrap(new byte[32]));
        assertThrows(NullPointerException.class,
                () -> TxHelperExtend.getConnectedRedeemDataExtend(disconnected, bag));

        byte[] unknownHash = new byte[20];
        Arrays.fill(unknownHash, (byte) 7);
        Address unknown = new Address(MainNetParams.get(), unknownHash);
        Script inscription = OrdScriptBuilder.buildInscription(unknown, new byte[] {1}, "text/plain", null);
        assertThrows(RuntimeException.class,
                () -> TxHelperExtend.getConnectedRedeemDataExtend(outpoint(inscription), bag));
    }

    private static TransactionOutPoint outpoint(Script script) {
        Transaction funding = new Transaction(Net.MAINNET);
        return new TransactionOutPoint(Net.MAINNET, funding.addOutput(Coin.valueOf(546), script));
    }
}
