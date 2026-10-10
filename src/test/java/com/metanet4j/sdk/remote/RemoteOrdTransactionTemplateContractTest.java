package com.metanet4j.sdk.remote;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.hutool.core.codec.Base64;
import com.metanet4j.sdk.EcKeyLiteExtend;
import com.metanet4j.sdk.PublicKey;
import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.bap.BapBase;
import com.metanet4j.sdk.bap.RemoteBapBase;
import com.metanet4j.sdk.exception.SignErrorException;
import com.metanet4j.sdk.context.PreSignHashContext;
import com.metanet4j.sdk.ordinals.OrdScriptBuilder;
import com.metanet4j.sdk.utxo.UTXOProvider;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.base.bsv.BsvAmount;
import io.bitcoinsv.bitcoinjsv.core.ECKey;
import io.bitcoinsv.bitcoinjsv.core.ECKeyLite;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import com.metanet4j.base.bsv.BsvHashBytes;
import com.metanet4j.base.bsv.BsvUtxo;
import com.metanet4j.bsv.transaction.Transaction;
import io.bitcoinsv.bitcoinjsv.script.Script;
import io.bitcoinsv.bitcoinjsv.script.ScriptBuilder;
import io.bitcoinsv.bitcoinjsv.script.ScriptUtils_legacy;
import io.bitcoinsv.bitcoinjsv.script.ScriptVerifyFlag;
import io.bitcoinsv.bitcoinjsv.signers.TransactionSigner;
import com.metanet4j.sdk.bap.SigningKeyProvider;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.mockito.Mockito.mock;

class RemoteOrdTransactionTemplateContractTest {

    private final BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
    private final RemoteBapBase remoteBap = new RemoteBapBase(bap.getRootAddress(),
            bap.getPreviouAddress(), bap.getCurrentAddress(), bap.getPayAccountAddress(),
            bap.getOrdAddress(), bap.getAppName());
    private final AddressEnhance paymentAddress = bap.getPayAccountAddress();
    private final AddressEnhance destination = bap.getOrdAddress();
    private final SigningKeyProvider keyBag = new BapBase.BapProviderKeyBag(bap);
    private final UTXOProvider<AddressEnhance, BsvUtxo> provider = addresses -> {
        assertEquals(List.of(paymentAddress.toBase58()),
                addresses.stream().map(AddressEnhance::toBase58).toList());
        byte[] hash = new byte[32];
        hash[0] = 1;
        return List.of(new BsvUtxo(BsvHashBytes.of(hash), 0, 20_000L, 0, false, paymentAddress.toBase58(), null));
    };

    @AfterEach
    void clearRemoteSignatures() {
        PreSignHashUtils.clear();
    }

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
        assertEquals(1, builder.build().inputs.size());
        assertEquals(2, builder.build().outputs.size());
        assertEquals(BsvAmount.SATOSHI, builder.build()BsvAmount.fromSdkValue(.outputs.get(0).satoshis));
        assertArrayEquals(OrdScriptBuilder.buildInscription(destination,
                        "first".getBytes(StandardCharsets.US_ASCII), "text/plain", metadata).toUint8Array(),
                builder.build().outputs.get(0).lockingScript.toUint8Array());
        assertEquals(builder.build().getInputSum(), builder.build().getOutputSum().add(builder.getFee()));
        assertEquals(0, builder.build().getInput(0).lockingScript.toUint8Array().length);
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
                expectedMetadata).toUint8Array();
        byte[] actual = template.createPostOrdinal(List.of(bap.getPayAccountAddress()),
                provider, destination, paymentAddress, 500, inscription, remoteBap,
                new RemoteTransactionSigner(), RemoteSignType.CURRENT).outputs.get(0).lockingScript.toUint8Array();

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

        assertEquals(2, sent.inputs.size());
        assertEquals(BsvAmount.SATOSHI, sentBsvAmount.fromSdkValue(.outputs.get(0).satoshis));
        assertArrayEquals(ScriptBuilder.createOutputScript(destination).toUint8Array(),
                sent.outputs.get(0).lockingScript.toUint8Array());
        assertTrue(sent.getInputSum().satoshis > sent.getOutputSum().satoshis);
        for (int i = 0; i < sent.inputs.size(); i++) {
            ScriptUtils_legacy.correctlySpends(sent.getInput(i).getScriptSig(), sent, i,
                    sent.getInput(i).getConnectedOutput().getScriptPubKey(),
                    sent.getInput(i).getConnectedOutput().satoshis,
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
                        "second".getBytes(StandardCharsets.US_ASCII), "text/plain", metadata).toUint8Array(),
                sent.outputs.get(0).lockingScript.toUint8Array());
    }

    @Test
    void incompleteReinscriptionFallsBackToAnOrdinaryTransfer() {
        List<OrdScriptBuilder.Inscription> incomplete = List.of(
                OrdScriptBuilder.Inscription.builder().dataB64("")
                        .contentType("text/plain").build(),
                OrdScriptBuilder.Inscription.builder().dataB64(Base64.encode("x".getBytes(StandardCharsets.US_ASCII)))
                        .contentType("").build());

        for (OrdScriptBuilder.Inscription attempt : incomplete) {
            Transaction sent = new RemoteOrdTransactionTemplate(false, false).sendOrdinal(
                    bap, ordinalUtxo(), paymentUtxo(), destination, paymentAddress,
                    500, attempt, null, keyBag);
            assertArrayEquals(ScriptBuilder.createOutputScript(destination).toUint8Array(),
                    sent.outputs.get(0).lockingScript.toUint8Array());
            assertEquals(BsvAmount.SATOSHI, sentBsvAmount.fromSdkValue(.outputs.get(0).satoshis));
        }
    }

    @Test
    void remoteSendCanReturnAnUnsignedOrdinalTransferWhenSigningIsDisabled() {
        RemoteOrdTransactionTemplate template = new RemoteOrdTransactionTemplate(false, false);

        Transaction tx = template.sendOrdinal(remoteBap, ordinalUtxo(), paymentUtxo(),
                destination, paymentAddress, 500, null, null, RemoteSignType.CURRENT);

        assertEquals(2, tx.inputs.size());
        assertArrayEquals(ordinalUtxo().getScript().toUint8Array(),
                tx.getInput(0).getConnectedOutput().lockingScript.toUint8Array());
        assertEquals(BsvAmount.SATOSHI, txBsvAmount.fromSdkValue(.outputs.get(0).satoshis));
        assertArrayEquals(ScriptBuilder.createOutputScript(destination).toUint8Array(),
                tx.outputs.get(0).lockingScript.toUint8Array());
        assertEquals(0, tx.getInput(0).lockingScript.toUint8Array().length);
        assertEquals(0, tx.getInput(1).lockingScript.toUint8Array().length);
    }

    @Test
    void stagedRemoteTransferSignsBothInputsFromExternallyPreparedHashes() {
        RemoteOrdTransactionTemplate template = new RemoteOrdTransactionTemplate(false, false);
        RemoteOrdTransactionBuilder unsigned = template.prepareSendOrdinal(remoteBap,
                ordinalUtxo(), paymentUtxo(), destination, paymentAddress, 500, null, null,
                RemoteSignType.CURRENT);

        List<PreSignHashContext> pending = unsigned.getTxInputSignHash(false, remoteBap);
        assertEquals(2, pending.size());
        assertEquals(RemoteSignType.ORD, pending.get(0).getRemoteSignType());
        assertEquals(bap.getOrdAddress().toBase58(), pending.get(0).getSignAddress());
        assertEquals(RemoteSignType.PAYMENT, pending.get(1).getRemoteSignType());
        assertEquals(bap.getPayAccountAddress().toBase58(), pending.get(1).getSignAddress());
        signRemoteInputs(pending);

        template.setDoInputSign(true);
        RemoteOrdTransactionBuilder completed = template.prepareSendOrdinal(remoteBap,
                ordinalUtxo(), paymentUtxo(), destination, paymentAddress, 500, null, null,
                RemoteSignType.CURRENT);
        Transaction signed = completed.build();

        assertEquals(2, signed.inputs.size());
        assertArrayEquals(ScriptBuilder.createOutputScript(destination).toUint8Array(),
                signed.outputs.get(0).lockingScript.toUint8Array());
        assertEquals(BsvAmount.SATOSHI, signedBsvAmount.fromSdkValue(.outputs.get(0).satoshis));
        assertEquals(signed.getInputSum(), signed.getOutputSum().add(completed.getFee()));
        assertSpendable(signed);
    }

    @Test
    void reinscribedRemoteTransferSignsBsmThenBothInputs() {
        RemoteOrdTransactionTemplate template = new RemoteOrdTransactionTemplate(false, false);
        OrdScriptBuilder.Inscription reinscription = inscription("second");
        OrdScriptBuilder.ORDMap metadata = new OrdScriptBuilder.ORDMap();
        metadata.put("app", "test");
        metadata.put("type", "ord");

        RemoteOrdTransactionBuilder unsigned = template.prepareSendOrdinal(remoteBap,
                ordinalUtxo(), paymentUtxo(), destination, paymentAddress, 500,
                reinscription, metadata, RemoteSignType.CURRENT);
        List<PreSignHashContext> bsm = unsigned.getAllBsmSignHash(true, false, remoteBap);
        assertEquals(1, bsm.size());
        assertEquals(RemoteSignType.CURRENT, bsm.get(0).getRemoteSignType());
        bsm.get(0).setSigB64(EcKeyLiteExtend.fromPrivate(bap.getCurrentPrivateKey().getPrivKey())
                .signHash(bsm.get(0).getPreSignHash()));
        PreSignHashUtils.setPreSignHashContext(bsm.get(0));

        template.setDoBsmSign(true);
        RemoteOrdTransactionBuilder bsmSigned = template.prepareSendOrdinal(remoteBap,
                ordinalUtxo(), paymentUtxo(), destination, paymentAddress, 500,
                reinscription, metadata, RemoteSignType.CURRENT);
        assertTrue(bsmSigned.getSigma().verify());
        signRemoteInputs(bsmSigned.getTxInputSignHash(false, remoteBap));

        template.setDoInputSign(true);
        Transaction signed = template.prepareSendOrdinal(remoteBap, ordinalUtxo(), paymentUtxo(),
                destination, paymentAddress, 500, reinscription, metadata,
                RemoteSignType.CURRENT).build();

        byte[] inscriptionScript = OrdScriptBuilder.buildInscription(destination,
                "second".getBytes(StandardCharsets.US_ASCII), "text/plain", metadata).toUint8Array();
        assertArrayEquals(inscriptionScript, Arrays.copyOf(signed.outputs.get(0).lockingScript.toUint8Array(),
                inscriptionScript.length));
        assertEquals(BsvAmount.SATOSHI, signedBsvAmount.fromSdkValue(.outputs.get(0).satoshis));
        assertSpendable(signed);
    }

    @Test
    void prepareSendOrdinalFallsBackToAnOrdinaryTransferForIncompleteReinscriptions() {
        List<OrdScriptBuilder.Inscription> incomplete = List.of(
                OrdScriptBuilder.Inscription.builder().dataB64("")
                        .contentType("text/plain").build(),
                OrdScriptBuilder.Inscription.builder()
                        .dataB64(Base64.encode("x".getBytes(StandardCharsets.US_ASCII)))
                        .contentType("").build());

        for (OrdScriptBuilder.Inscription attempt : incomplete) {
            RemoteOrdTransactionBuilder builder = new RemoteOrdTransactionTemplate(false, false)
                    .prepareSendOrdinal(remoteBap, ordinalUtxo(), paymentUtxo(), destination,
                            paymentAddress, 500, attempt, null, RemoteSignType.CURRENT);
            assertEquals(2, builder.build().inputs.size());
            assertEquals(BsvAmount.SATOSHI, builder.build()BsvAmount.fromSdkValue(.outputs.get(0).satoshis));
            assertArrayEquals(ScriptBuilder.createOutputScript(destination).toUint8Array(),
                    builder.build().outputs.get(0).lockingScript.toUint8Array());
        }
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

        assertEquals(1, tx.inputs.size());
        assertEquals(3, tx.outputs.size());
        assertEquals(BsvAmount.SATOSHI, txBsvAmount.fromSdkValue(.outputs.get(0).satoshis));
        assertEquals(0, tx.getInput(0).lockingScript.toUint8Array().length);
        assertTrue(tx.getInputSum().satoshis > tx.getOutputSum().satoshis);
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
                        "post".getBytes(StandardCharsets.US_ASCII), "text/plain", metadata).toUint8Array(),
                tx.outputs.get(0).lockingScript.toUint8Array());
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedSendUtxosSpendsLocalPaymentInputWithoutBroadcast() throws Exception {
        Transaction tx = new RemoteOrdTransactionTemplate(false, false).sendUtxos(
                bap, List.of(paymentUtxo()), paymentAddress, 500, keyBag);

        assertEquals(1, tx.inputs.size());
        assertEquals(1, tx.outputs.size());
        assertTrue(tx.getInputSum().satoshis > tx.getOutputSum().satoshis);
        ScriptUtils_legacy.correctlySpends(tx.getInput(0).getScriptSig(), tx, 0,
                tx.getInput(0).getConnectedOutput().getScriptPubKey(),
                tx.getInput(0).getConnectedOutput().satoshis, ScriptVerifyFlag.GENESIS_SET);
    }

    @Test
    void signsBsmThenInputFromTwoLocallyPreparedRemoteContexts() {
        RemoteOrdTransactionTemplate template = new RemoteOrdTransactionTemplate(false, false);
        OrdScriptBuilder.Inscription content = inscription("staged");
        List<AddressLite> addresses = List.of(bap.getPayAccountAddress());
        RemoteOrdTransactionBuilder unsigned = template.createOrdinal(addresses,
                provider, destination, paymentAddress, 500, content, null,
                remoteBap, RemoteSignType.CURRENT);
        PreSignHashContext bsm = unsigned.getAllBsmSignHash(true, false, remoteBap).get(0);
        bsm.setSigB64(EcKeyLiteExtend.fromPrivate(bap.getCurrentPrivateKey().getPrivKey())
                .signHash(bsm.getPreSignHash()));
        PreSignHashUtils.setPreSignHashContext(bsm);

        template.setDoBsmSign(true);
        RemoteOrdTransactionBuilder bsmSigned = template.createOrdinal(addresses,
                provider, destination, paymentAddress, 500, content, null,
                remoteBap, RemoteSignType.CURRENT);
        assertTrue(bsmSigned.getSigma().verify());
        PreSignHashContext payment = bsmSigned.getTxInputSignHash(false, remoteBap).get(0);
        ECKey paymentKey = ECKey.fromPrivate(bap.getPayAccountKey().getPrivKeyBytes());
        payment.setSigB64(Base64.encode(paymentKey.sign(payment.getPreSignHash()).encodeToDER()));
        payment.setPublicKey(PublicKey.fromBytes(paymentKey.getPubKey()));
        PreSignHashUtils.setPreSignHashContext(payment);

        template.setDoInputSign(true);
        Transaction signed = template.createOrdinal(addresses, provider, destination,
                paymentAddress, 500, content, null, remoteBap,
                RemoteSignType.CURRENT).build();

        assertTrue(signed.getInput(0).lockingScript.toUint8Array().length > 0);
        ScriptUtils_legacy.correctlySpends(signed.getInput(0).getScriptSig(), signed, 0,
                signed.getInput(0).getConnectedOutput().getScriptPubKey(),
                signed.getInput(0).getConnectedOutput().satoshis, ScriptVerifyFlag.GENESIS_SET);
    }

    private void signRemoteInputs(List<PreSignHashContext> pending) {
        for (PreSignHashContext context : pending) {
            ECKeyLite key = context.getRemoteSignType() == RemoteSignType.ORD
                    ? bap.getOrdPrivateKey() : bap.getPayAccountKey();
            context.setSigB64(Base64.encode(key.sign(context.getPreSignHash()).encodeToDER()));
            context.setPublicKey(PublicKey.fromHex(key.getPublicKeyAsHex()));
            PreSignHashUtils.setPreSignHashContext(context);
        }
    }

    private void assertSpendable(Transaction signed) {
        for (int i = 0; i < signed.inputs.size(); i++) {
            assertTrue(signed.getInput(i).lockingScript.toUint8Array().length > 0);
            ScriptUtils_legacy.correctlySpends(signed.getInput(i).getScriptSig(), signed, i,
                    signed.getInput(i).getConnectedOutput().getScriptPubKey(),
                    signed.getInput(i).getConnectedOutput().satoshis,
                    ScriptVerifyFlag.GENESIS_SET);
        }
    }

    private BsvUtxo paymentUtxo() {
        byte[] hash = new byte[32];
        hash[0] = 2;
        return new BsvUtxo(BsvHashBytes.of(hash), 0, 20_000L, 0, false, paymentAddress.toBase58(), null);
    }

    private BsvUtxo ordinalUtxo() {
        byte[] hash = new byte[32];
        hash[0] = 3;
        Script prior = OrdScriptBuilder.buildInscription(destination,
                "prior".getBytes(StandardCharsets.US_ASCII), "text/plain", null);
        return new UTXO(Sha256Hash.wrap(hash), 0, BsvAmount.SATOSHI, 0, false, prior);
    }

    private OrdScriptBuilder.Inscription inscription(String value) {
        return OrdScriptBuilder.Inscription.builder().contentType("text/plain")
                .dataB64(Base64.encode(value.getBytes(StandardCharsets.US_ASCII))).build();
    }
}
