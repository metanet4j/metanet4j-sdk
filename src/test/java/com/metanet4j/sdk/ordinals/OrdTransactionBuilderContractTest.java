package com.metanet4j.sdk.ordinals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.bap.BapBase;
import com.metanet4j.sdk.exception.SignErrorException;
import com.metanet4j.sdk.signers.ExtendForSignTransaction;
import com.metanet4j.sdk.signers.OrdinalsTransactionSigner;
import io.bitcoinsv.bitcoinjsv.core.Address;
import io.bitcoinsv.bitcoinjsv.core.Coin;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import io.bitcoinsv.bitcoinjsv.core.UTXO;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.params.Net;
import io.bitcoinsv.bitcoinjsv.script.Script;
import io.bitcoinsv.bitcoinjsv.script.ScriptUtils_legacy;
import io.bitcoinsv.bitcoinjsv.script.ScriptVerifyFlag;
import io.bitcoinsv.bitcoinjsv.script.SigHash;
import io.bitcoinsv.bitcoinjsv.signers.TransactionSigner;
import io.bitcoinsv.bitcoinjsv.temp.KeyBag;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OrdTransactionBuilderContractTest {

    private final BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
    private final KeyBag keyBag = new BapBase.BapProviderKeyBag(bap);
    private final Address paymentAddress = new Address(bap.getPayAccountAddress());
    private final Address ordAddress = bap.getOrdAddress();

    @Test
    void constructorsKeepTheSelectedNetworkTransactionAndSigner() {
        Transaction existing = new Transaction(Net.TESTNET3);
        existing.addOutput(Coin.valueOf(546), paymentAddress);
        TransactionSigner signer = mock(TransactionSigner.class);

        OrdTransactionBuilder defaults = new OrdTransactionBuilder(bap, keyBag);
        OrdTransactionBuilder testnet = new OrdTransactionBuilder(bap, Net.TESTNET3, keyBag);
        OrdTransactionBuilder existingTransaction = new OrdTransactionBuilder(
                bap, Net.TESTNET3, existing, keyBag);
        OrdTransactionBuilder configured = new OrdTransactionBuilder(
                bap, Net.TESTNET3, existing, signer, keyBag);

        assertEquals(Net.MAINNET, defaults.getNet());
        assertTrue(defaults.getTransactionSigner() != null);
        assertEquals(Net.TESTNET3, testnet.getNet());
        assertEquals(Net.TESTNET3, testnet.build().getNet());
        assertSame(existing, existingTransaction.build());
        assertSame(existing, configured.build());
        assertSame(signer, configured.getTransactionSigner());
        assertEquals(Coin.valueOf(546), configured.build().getOutput(0).getValue());
    }

    @Test
    void signsPaymentAndInscribedInputsInMemory() {
        OrdTransactionBuilder builder = new OrdTransactionBuilder(bap, keyBag,
                new OrdinalsTransactionSigner());
        builder.addOrdInput(ordinalUtxo(1, 0))
                .addInput(paymentUtxo(2, 0, 20_000));
        builder.addOrdOutput(ordAddress, "x".getBytes(StandardCharsets.US_ASCII),
                "text/plain", null);
        builder.withFeeKb(Coin.valueOf(500)).changeAddress(paymentAddress);

        assertSame(builder, builder.signTx(keyBag, true));
        Transaction signed = builder.build();
        assertEquals(Coin.SATOSHI, signed.getOutput(0).getValue());
        assertEquals(2, signed.getInputs().size());
        assertEquals(signed.getInputSum(), signed.getOutputSum().add(builder.getFee()));
        for (int i = 0; i < signed.getInputs().size(); i++) {
            assertTrue(signed.getInput(i).getScriptBytes().length > 0);
            ScriptUtils_legacy.correctlySpends(signed.getInput(i).getScriptSig(), signed, i,
                    signed.getInput(i).getConnectedOutput().getScriptPubKey(),
                    signed.getInput(i).getConnectedOutput().getValue(),
                    ScriptVerifyFlag.GENESIS_SET);
        }
    }

    @Test
    void rejectsASignerThatReportsFailure() {
        TransactionSigner signer = mock(TransactionSigner.class);
        OrdTransactionBuilder builder = new OrdTransactionBuilder(bap, keyBag, signer);
        builder.addInput(paymentUtxo(3, 0, 20_000));

        assertThrows(SignErrorException.class, () -> builder.signTx(keyBag, true));
    }

    @Test
    void forwardsRequestedSignatureModeToTheSigner() {
        TransactionSigner signer = mock(TransactionSigner.class);
        when(signer.signInputs(any(), eq(keyBag))).thenReturn(true);
        OrdTransactionBuilder builder = new OrdTransactionBuilder(bap, keyBag, signer);
        builder.addInput(paymentUtxo(4, 0, 20_000));

        builder.signTx(keyBag, SigHash.Flags.SINGLE, false, true);

        ArgumentCaptor<TransactionSigner.ProposedTransaction> captured =
                ArgumentCaptor.forClass(TransactionSigner.ProposedTransaction.class);
        verify(signer).signInputs(captured.capture(), eq(keyBag));
        ExtendForSignTransaction request = (ExtendForSignTransaction) captured.getValue();
        assertEquals(SigHash.Flags.SINGLE, request.getHashFlags());
        assertTrue(request.isAnyoneCanPay());
        assertEquals(false, request.isUseForkId());
    }

    @Test
    void simpleSignatureOverloadHonorsItsForkIdArgument() {
        TransactionSigner signer = mock(TransactionSigner.class);
        when(signer.signInputs(any(), eq(keyBag))).thenReturn(true);
        OrdTransactionBuilder builder = new OrdTransactionBuilder(bap, keyBag, signer);
        builder.addInput(paymentUtxo(5, 0, 20_000));

        builder.signTx(keyBag, false);

        ArgumentCaptor<TransactionSigner.ProposedTransaction> captured =
                ArgumentCaptor.forClass(TransactionSigner.ProposedTransaction.class);
        verify(signer).signInputs(captured.capture(), eq(keyBag));
        assertEquals(false, ((ExtendForSignTransaction) captured.getValue()).isUseForkId());
    }

    private UTXO paymentUtxo(int hashByte, long index, long satoshis) {
        return new UTXO(hash(hashByte), index, Coin.valueOf(satoshis), 0, false,
                null, paymentAddress.toBase58());
    }

    private UTXO ordinalUtxo(int hashByte, long index) {
        Script script = OrdScriptBuilder.buildInscription(ordAddress,
                "prior".getBytes(StandardCharsets.US_ASCII), "text/plain", null);
        return new UTXO(hash(hashByte), index, Coin.SATOSHI, 0, false, script);
    }

    private Sha256Hash hash(int firstByte) {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) firstByte;
        return Sha256Hash.wrap(bytes);
    }
}
