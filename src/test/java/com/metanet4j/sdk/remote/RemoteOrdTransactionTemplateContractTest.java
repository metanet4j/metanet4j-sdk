package com.metanet4j.sdk.remote;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.hutool.core.codec.Base64;
import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.bap.BapBase;
import com.metanet4j.sdk.bap.RemoteBapBase;
import com.metanet4j.sdk.exception.SignErrorException;
import com.metanet4j.sdk.ordinals.OrdScriptBuilder;
import com.metanet4j.sdk.utxo.UTXOProvider;
import io.bitcoinsv.bitcoinjsv.core.Address;
import io.bitcoinsv.bitcoinjsv.core.AddressLite;
import io.bitcoinsv.bitcoinjsv.core.Coin;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import io.bitcoinsv.bitcoinjsv.core.UTXO;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.script.Script;
import io.bitcoinsv.bitcoinjsv.script.ScriptBuilder;
import io.bitcoinsv.bitcoinjsv.script.ScriptUtils_legacy;
import io.bitcoinsv.bitcoinjsv.script.ScriptVerifyFlag;
import io.bitcoinsv.bitcoinjsv.signers.TransactionSigner;
import io.bitcoinsv.bitcoinjsv.temp.KeyBag;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;

class RemoteOrdTransactionTemplateContractTest {

    private final BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
    private final RemoteBapBase remoteBap = new RemoteBapBase(bap.getRootAddress(),
            bap.getPreviouAddress(), bap.getCurrentAddress(), bap.getPayAccountAddress(),
            bap.getOrdAddress(), bap.getAppName());
    private final Address paymentAddress = new Address(bap.getPayAccountAddress());
    private final Address destination = bap.getOrdAddress();
    private final KeyBag keyBag = new BapBase.BapProviderKeyBag(bap);
    private final UTXOProvider<AddressLite, UTXO> provider = addresses -> {
        assertEquals(List.of(paymentAddress.toBase58()),
                addresses.stream().map(AddressLite::toBase58).toList());
        byte[] hash = new byte[32];
        hash[0] = 1;
        return List.of(new UTXO(Sha256Hash.wrap(hash), 0, Coin.valueOf(20_000),
                0, false, null, paymentAddress.toBase58()));
    };

    @Test
    void createsAnUnsignedOrdinalWithPendingHashesAndBalancedChange() {
        RemoteOrdTransactionTemplate template = new RemoteOrdTransactionTemplate(false, false);
        OrdScriptBuilder.Inscription inscription = inscription("first");
        OrdScriptBuilder.ORDMap metadata = new OrdScriptBuilder.ORDMap();
        metadata.put("app", "test");
        metadata.put("type", "ord");

        RemoteOrdTransactionBuilder builder = template.createOrdinal(
                List.of(bap.getPayAccountAddress()), provider, destination, paymentAddress,
                500, inscription, metadata, remoteBap, RemoteSignType.CURRENT);

        assertFalse(template.isDoBsmSign());
        assertFalse(template.isDoInputSign());
        assertEquals(1, builder.build().getInputs().size());
        assertEquals(2, builder.build().getOutputs().size());
        assertEquals(Coin.SATOSHI, builder.build().getOutput(0).getValue());
        assertArrayEquals(OrdScriptBuilder.buildInscription(destination,
                        "first".getBytes(StandardCharsets.US_ASCII), "text/plain", metadata).getProgram(),
                builder.build().getOutput(0).getScriptBytes());
        assertEquals(builder.build().getInputSum(), builder.build().getOutputSum().add(builder.getFee()));
        assertEquals(0, builder.build().getInput(0).getScriptBytes().length);
        assertNull(builder.getSigma());
        assertEquals(RemoteSignType.CURRENT,
                builder.getAllBsmSignHash(true, false, remoteBap).get(0).getRemoteSignType());
        assertEquals(1, builder.getTxInputSignHash(false, remoteBap).size());
    }

    @Test
    void postOrdinalIncludesPostMetadataInItsLockingScript() {
        RemoteOrdTransactionTemplate template = new RemoteOrdTransactionTemplate(false, false);
        OrdScriptBuilder.Inscription inscription = inscription("post");
        OrdScriptBuilder.ORDMap expectedMetadata = new OrdScriptBuilder.ORDMap();
        expectedMetadata.put("app", remoteBap.getAppName());
        expectedMetadata.put("type", "post");

        byte[] expected = OrdScriptBuilder.buildInscription(destination,
                "post".getBytes(StandardCharsets.US_ASCII), "text/plain",
                expectedMetadata).getProgram();
        byte[] actual = template.createPostOrdinal(List.of(bap.getPayAccountAddress()),
                provider, destination, paymentAddress, 500, inscription, remoteBap,
                new RemoteTransactionSigner(), RemoteSignType.CURRENT).getOutput(0).getScriptBytes();

        assertArrayEquals(expected, actual);
    }

    @Test
    void inputSigningSwitchControlsWhetherTheSignerRuns() {
        RemoteOrdTransactionTemplate template = new RemoteOrdTransactionTemplate(false, false);
        template.setDoInputSign(true);
        template.setDoBsmSign(false);
        assertTrue(template.isDoInputSign());
        assertFalse(template.isDoBsmSign());

        assertThrows(SignErrorException.class, () -> template.createOrdinal(
                List.of(bap.getPayAccountAddress()), provider, destination, paymentAddress,
                500, inscription("switch"), null, remoteBap,
                mock(RemoteTransactionSigner.class), RemoteSignType.CURRENT));
    }

    @Test
    void defaultConstructorEnablesBothSigningStages() {
        RemoteOrdTransactionTemplate template = new RemoteOrdTransactionTemplate();
        assertTrue(template.isDoBsmSign());
        assertTrue(template.isDoInputSign());
    }

    @Test
    void sendsAnExistingOrdinalWithTwoSpendableInputs() {
        Transaction sent = new RemoteOrdTransactionTemplate(false, false).sendOrdinal(
                bap, ordinalUtxo(), paymentUtxo(), destination, paymentAddress,
                500, null, null, keyBag);

        assertEquals(2, sent.getInputs().size());
        assertEquals(Coin.SATOSHI, sent.getOutput(0).getValue());
        assertArrayEquals(ScriptBuilder.createOutputScript(destination).getProgram(),
                sent.getOutput(0).getScriptBytes());
        assertTrue(sent.getInputSum().getValue() > sent.getOutputSum().getValue());
        for (int i = 0; i < sent.getInputs().size(); i++) {
            ScriptUtils_legacy.correctlySpends(sent.getInput(i).getScriptSig(), sent, i,
                    sent.getInput(i).getConnectedOutput().getScriptPubKey(),
                    sent.getInput(i).getConnectedOutput().getValue(),
                    ScriptVerifyFlag.GENESIS_SET);
        }
    }

    @Test
    void reinscriptionKeepsTheRequestedMediaType() {
        OrdScriptBuilder.ORDMap metadata = new OrdScriptBuilder.ORDMap();
        metadata.put("app", "test");
        metadata.put("type", "ord");
        OrdScriptBuilder.Inscription reinscription = inscription("second");

        Transaction sent = new RemoteOrdTransactionTemplate(false, false).sendOrdinal(
                bap, ordinalUtxo(), paymentUtxo(), destination, paymentAddress,
                500, reinscription, metadata, keyBag);

        assertArrayEquals(OrdScriptBuilder.buildInscription(destination,
                        "second".getBytes(StandardCharsets.US_ASCII), "text/plain", metadata).getProgram(),
                sent.getOutput(0).getScriptBytes());
    }

    @Test
    void sendOrdinalPropagatesSignerFailure() {
        TransactionSigner refusingSigner = mock(TransactionSigner.class);

        assertThrows(SignErrorException.class, () ->
                new RemoteOrdTransactionTemplate(false, false).sendOrdinal(
                        bap, ordinalUtxo(), paymentUtxo(), destination, paymentAddress,
                        500, null, null, keyBag, refusingSigner, null));
    }

    @Test
    void createsUnsignedBapOrdinalWhenBothSigningStagesAreDisabled() {
        RemoteOrdTransactionTemplate template = new RemoteOrdTransactionTemplate(false, false);

        Transaction tx = template.createBapOrdinal(List.of(bap.getPayAccountAddress()),
                provider, destination, paymentAddress, 500, remoteBap,
                new RemoteTransactionSigner(), RemoteSignType.CURRENT);

        assertEquals(1, tx.getInputs().size());
        assertEquals(3, tx.getOutputs().size());
        assertEquals(Coin.SATOSHI, tx.getOutput(0).getValue());
        assertEquals(0, tx.getInput(0).getScriptBytes().length);
        assertTrue(tx.getInputSum().getValue() > tx.getOutputSum().getValue());
    }

    @Test
    void defaultPostOverloadBuildsPostMetadataWithoutSigning() {
        RemoteOrdTransactionTemplate template = new RemoteOrdTransactionTemplate(false, false);
        OrdScriptBuilder.ORDMap metadata = new OrdScriptBuilder.ORDMap();
        metadata.put("app", remoteBap.getAppName());
        metadata.put("type", "post");

        Transaction tx = template.createPostOrdinal(List.of(bap.getPayAccountAddress()),
                provider, destination, paymentAddress, 500, inscription("post"),
                remoteBap, RemoteSignType.CURRENT);

        assertArrayEquals(OrdScriptBuilder.buildInscription(destination,
                        "post".getBytes(StandardCharsets.US_ASCII), "text/plain", metadata).getProgram(),
                tx.getOutput(0).getScriptBytes());
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedSendUtxosSpendsLocalPaymentInputWithoutBroadcast() throws Exception {
        Transaction tx = new RemoteOrdTransactionTemplate(false, false).sendUtxos(
                bap, List.of(paymentUtxo()), paymentAddress, 500, keyBag);

        assertEquals(1, tx.getInputs().size());
        assertEquals(1, tx.getOutputs().size());
        assertTrue(tx.getInputSum().getValue() > tx.getOutputSum().getValue());
        ScriptUtils_legacy.correctlySpends(tx.getInput(0).getScriptSig(), tx, 0,
                tx.getInput(0).getConnectedOutput().getScriptPubKey(),
                tx.getInput(0).getConnectedOutput().getValue(), ScriptVerifyFlag.GENESIS_SET);
    }

    private UTXO paymentUtxo() {
        byte[] hash = new byte[32];
        hash[0] = 2;
        return new UTXO(Sha256Hash.wrap(hash), 0, Coin.valueOf(20_000),
                0, false, null, paymentAddress.toBase58());
    }

    private UTXO ordinalUtxo() {
        byte[] hash = new byte[32];
        hash[0] = 3;
        Script prior = OrdScriptBuilder.buildInscription(destination,
                "prior".getBytes(StandardCharsets.US_ASCII), "text/plain", null);
        return new UTXO(Sha256Hash.wrap(hash), 0, Coin.SATOSHI, 0, false, prior);
    }

    private OrdScriptBuilder.Inscription inscription(String value) {
        return OrdScriptBuilder.Inscription.builder().contentType("text/plain")
                .dataB64(Base64.encode(value.getBytes(StandardCharsets.US_ASCII))).build();
    }
}
