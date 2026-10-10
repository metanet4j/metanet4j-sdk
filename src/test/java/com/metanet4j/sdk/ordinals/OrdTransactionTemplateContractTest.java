package com.metanet4j.sdk.ordinals;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.hutool.core.codec.Base64;
import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.bap.BapBase;
import com.metanet4j.sdk.exception.SignErrorException;
import com.metanet4j.sdk.utxo.UTXOProvider;
import io.bitcoinsv.bitcoinjsv.core.Address;
import io.bitcoinsv.bitcoinjsv.core.AddressLite;
import io.bitcoinsv.bitcoinjsv.core.Coin;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import io.bitcoinsv.bitcoinjsv.core.UTXO;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.script.ScriptBuilder;
import io.bitcoinsv.bitcoinjsv.script.ScriptUtils_legacy;
import io.bitcoinsv.bitcoinjsv.script.ScriptVerifyFlag;
import io.bitcoinsv.bitcoinjsv.signers.TransactionSigner;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrdTransactionTemplateContractTest {

    private final BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
    private final Address payment = new Address(bap.getPayAccountAddress());
    private final Address destination = bap.getOrdAddress();
    private final BapBase.BapProviderKeyBag keyBag = new BapBase.BapProviderKeyBag(bap);
    private final UTXOProvider<AddressLite, UTXO> provider = addresses -> {
        assertEquals(List.of(payment.toBase58()),
                addresses.stream().map(AddressLite::toBase58).toList());
        return List.of(paymentUtxo(1));
    };

    @Test
    void postOrdinalIncludesTheAppAndPostMetadata() {
        OrdTransactionTemplate template = new OrdTransactionTemplate();
        OrdScriptBuilder.Inscription inscription = inscription("post", "text/plain");
        TransactionSigner signer = mock(TransactionSigner.class);
        when(signer.signInputs(any(), any())).thenReturn(true);
        OrdScriptBuilder.ORDMap metadata = new OrdScriptBuilder.ORDMap();
        metadata.put("app", bap.getAppName());
        metadata.put("type", "post");

        Transaction transaction = template.createPostOrdinal(
                List.of(bap.getPayAccountAddress()), provider, destination, payment,
                500, inscription, bap, keyBag, signer);

        byte[] inscriptionScript = OrdScriptBuilder.buildInscription(destination,
                "post".getBytes(StandardCharsets.US_ASCII), "text/plain", metadata).toUint8Array();
        assertArrayEquals(inscriptionScript,
                Arrays.copyOf(transaction.outputs.get(0).lockingScript.toUint8Array(), inscriptionScript.length));
    }

    @Test
    void reinscriptionKeepsItsMediaType() {
        OrdTransactionTemplate template = new OrdTransactionTemplate();
        OrdScriptBuilder.Inscription inscription = inscription("new", "text/plain");
        OrdScriptBuilder.ORDMap metadata = new OrdScriptBuilder.ORDMap();
        metadata.put("type", "post");
        TransactionSigner signer = mock(TransactionSigner.class);
        when(signer.signInputs(any(), any())).thenReturn(true);

        Transaction transaction = template.sendOrdinal(bap, ordinalUtxo(2),
                paymentUtxo(3), destination, payment, 500, inscription, metadata,
                keyBag, signer);

        assertArrayEquals(OrdScriptBuilder.buildInscription(destination,
                "new".getBytes(StandardCharsets.US_ASCII), "text/plain", metadata).toUint8Array(),
                transaction.outputs.get(0).lockingScript.toUint8Array());
    }

    @Test
    void sendOrdinalPropagatesSignerFailure() {
        OrdTransactionTemplate template = new OrdTransactionTemplate();
        TransactionSigner signer = mock(TransactionSigner.class);

        assertThrows(SignErrorException.class, () -> template.sendOrdinal(bap,
                ordinalUtxo(4), paymentUtxo(5), destination, payment, 500,
                null, null, keyBag, signer));
    }

    @Test
    void defaultCreateOrdinalSignsThePaymentInputAndBalancesChange() {
        OrdTransactionBuilder builder = new OrdTransactionTemplate().createOrdinal(
                List.of(bap.getPayAccountAddress()), provider, destination, payment,
                500, inscription("first", "text/plain"), null, bap, keyBag);

        Transaction signed = builder.build();
        assertEquals(Coin.SATOSHI, signedBsvAmount.fromSdkValue(.outputs.get(0).satoshis));
        assertEquals(signed.getInputSum(), signed.getOutputSum().add(builder.getFee()));
        assertSpendable(signed);
    }

    @Test
    void defaultSendOrdinalSignsBothInputsWithoutReinscription() {
        Transaction signed = new OrdTransactionTemplate().sendOrdinal(bap,
                ordinalUtxo(6), paymentUtxo(7), destination, payment, 500,
                null, null, keyBag);

        assertEquals(Coin.SATOSHI, signedBsvAmount.fromSdkValue(.outputs.get(0).satoshis));
        assertArrayEquals(ScriptBuilder.createOutputScript(destination).toUint8Array(),
                signed.outputs.get(0).lockingScript.toUint8Array());
        assertSpendable(signed);
    }

    @Test
    void emptyReinscriptionFieldsUseTheOrdinaryTransferOutput() {
        TransactionSigner signer = mock(TransactionSigner.class);
        when(signer.signInputs(any(), any())).thenReturn(true);
        Transaction missingContent = new OrdTransactionTemplate().sendOrdinal(bap,
                ordinalUtxo(8), paymentUtxo(9), destination, payment, 500,
                inscription("new", ""), null, keyBag, signer);
        Transaction missingData = new OrdTransactionTemplate().sendOrdinal(bap,
                ordinalUtxo(10), paymentUtxo(11), destination, payment, 500,
                inscription("", "text/plain"), null, keyBag, signer);

        byte[] ordinaryOutput = ScriptBuilder.createOutputScript(destination).toUint8Array();
        assertArrayEquals(ordinaryOutput, missingContent.outputs.get(0).lockingScript.toUint8Array());
        assertArrayEquals(ordinaryOutput, missingData.outputs.get(0).lockingScript.toUint8Array());
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedSendUtxosBuildsAndSignsTheChangePayment() throws Exception {
        Transaction signed = new OrdTransactionTemplate().sendUtxos(bap,
                List.of(paymentUtxo(12)), payment, 500, keyBag);

        assertEquals(1, signed.inputs.size());
        assertEquals(1, signed.outputs.size());
        assertTrue(signed.getOutputSum().isLessThan(signed.getInputSum()));
        assertSpendable(signed);
    }

    @Test
    void defaultCreateBapOrdinalIncludesRootAndSigmaProofs() {
        Transaction signed = new OrdTransactionTemplate().createBapOrdinal(
                List.of(bap.getPayAccountAddress()), provider, destination,
                payment, 500, bap, keyBag);

        assertEquals(Coin.SATOSHI, signedBsvAmount.fromSdkValue(.outputs.get(0).satoshis));
        assertTrue(signed.outputs.size() >= 3);
        assertTrue(new String(signed.outputs.get(1).lockingScript.toUint8Array(), StandardCharsets.UTF_8)
                .contains("1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdM"));
        assertSpendable(signed);
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

    private OrdScriptBuilder.Inscription inscription(String value, String contentType) {
        return OrdScriptBuilder.Inscription.builder().contentType(contentType)
                .dataB64(Base64.encode(value.getBytes(StandardCharsets.US_ASCII))).build();
    }

    private UTXO paymentUtxo(int firstHashByte) {
        return new UTXO(hash(firstHashByte), 0, Coin.valueOf(20_000), 0,
                false, null, payment.toBase58());
    }

    private UTXO ordinalUtxo(int firstHashByte) {
        return new UTXO(hash(firstHashByte), 0, Coin.SATOSHI, 0, false,
                OrdScriptBuilder.buildInscription(destination,
                        "old".getBytes(StandardCharsets.US_ASCII), "text/plain", null));
    }

    private Sha256Hash hash(int firstByte) {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) firstByte;
        return Sha256Hash.wrap(bytes);
    }
}
