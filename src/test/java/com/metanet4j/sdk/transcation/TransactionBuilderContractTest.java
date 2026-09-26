package com.metanet4j.sdk.transcation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.bap.BapBase;
import com.metanet4j.sdk.exception.SignErrorException;
import com.metanet4j.sdk.sigma.Sigma;
import com.metanet4j.sdk.signers.ExtendForSignTransaction;
import com.metanet4j.sdk.utxo.UTXOProvider;
import io.bitcoinsv.bitcoinjsv.core.Address;
import io.bitcoinsv.bitcoinjsv.core.AddressLite;
import io.bitcoinsv.bitcoinjsv.core.Coin;
import io.bitcoinsv.bitcoinjsv.core.ECKey;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import io.bitcoinsv.bitcoinjsv.core.UTXO;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.params.Net;
import io.bitcoinsv.bitcoinjsv.script.Script;
import io.bitcoinsv.bitcoinjsv.script.ScriptBuilder;
import io.bitcoinsv.bitcoinjsv.script.ScriptOpCodes;
import io.bitcoinsv.bitcoinjsv.script.ScriptUtils_legacy;
import io.bitcoinsv.bitcoinjsv.script.ScriptVerifyFlag;
import io.bitcoinsv.bitcoinjsv.signers.TransactionSigner;
import io.bitcoinsv.bitcoinjsv.signers.LocalTransactionSigner;
import io.bitcoinsv.bitcoinjsv.temp.KeyBag;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TransactionBuilderContractTest {

    private final BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
    private final KeyBag keyBag = new BapBase.BapProviderKeyBag(bap);
    private final Address paymentAddress = new Address(bap.getPayAccountAddress());

    @Test
    void addsProviderUtxoDataAndChangeWithoutLosingValue() {
        UTXO utxo = utxo(1, 0, 20_000);
        UTXOProvider<AddressLite, UTXO> provider = addresses -> {
            assertEquals(1, addresses.size());
            assertEquals(paymentAddress.toBase58(), addresses.get(0).toBase58());
            return List.of(utxo);
        };
        Script dataScript = new ScriptBuilder().op(ScriptOpCodes.OP_RETURN)
                .data("payload".getBytes(StandardCharsets.US_ASCII)).build();
        LockingScriptBuilder data = mock(LockingScriptBuilder.class);
        when(data.getLockingScript()).thenReturn(dataScript);

        TransactionBuilder builder = new TransactionBuilder(bap, keyBag);
        builder.addInput(bap.getPayAccountAddress(), provider)
                .addOutput(Coin.valueOf(777), paymentAddress)
                .addDataOutput(data)
                .withFeeKb(Coin.valueOf(500))
                .changeAddress(paymentAddress);
        Transaction tx = builder.getTargetTransaction();

        assertEquals(1, tx.getInputs().size());
        assertEquals(utxo.getHash(), tx.getInput(0).getOutpoint().getHash());
        assertEquals(utxo.getIndex(), tx.getInput(0).getOutpoint().getIndex());
        assertEquals(Coin.valueOf(20_000), tx.getInputSum());
        assertEquals(3, tx.getOutputs().size());
        assertEquals(Coin.valueOf(777), tx.getOutput(0).getValue());
        assertEquals(Coin.valueOf(0), tx.getOutput(1).getValue());
        assertArrayEquals(dataScript.getProgram(), tx.getOutput(1).getScriptBytes());
        assertEquals(builder.getChangeAmount(), tx.getOutput(2).getValue());
        assertEquals(tx.getInputSum(), tx.getOutputSum().add(builder.getFee()));
        assertTrue(builder.getFee().getValue() > 0);
        assertTrue(builder.getChangeAmount().getValue() > 0);
    }

    @Test
    void acceptsAddressListUtxoListAndExistingFundingOutput() {
        UTXO first = utxo(2, 1, 3_000);
        UTXO second = utxo(3, 2, 4_000);
        UTXOProvider<AddressLite, UTXO> provider = addresses -> {
            assertEquals(List.of(bap.getPayAccountAddress().toBase58()),
                    addresses.stream().map(AddressLite::toBase58).toList());
            return List.of(first, second);
        };
        Transaction funding = new Transaction(Net.MAINNET);
        funding.addOutput(Coin.valueOf(5_000), paymentAddress);

        TransactionBuilder builder = new TransactionBuilder(bap, keyBag);
        builder.addInputs(List.of(bap.getPayAccountAddress()), provider)
                .addInputs(List.of(utxo(4, 3, 6_000)))
                .addInput(funding.getOutput(0));

        assertEquals(4, builder.getTargetTransaction().getInputs().size());
        assertEquals(Coin.valueOf(18_000), builder.getTargetTransaction().getInputSum());
        assertNotNull(builder.getTargetTransaction().getInput(3).getConnectedOutput());
        assertEquals(funding.getOutput(0).getValue(),
                builder.getTargetTransaction().getInput(3).getConnectedOutput().getValue());
    }

    @Test
    void calculateChangeUsesTheFeeRatePassedToThePublicMethod() {
        TransactionBuilder builder = new TransactionBuilder(bap, keyBag);
        builder.addInput(utxo(5, 0, 20_000))
                .addOutput(Coin.valueOf(777), paymentAddress);

        assertEquals(Coin.valueOf(19_223), builder.calculateChangeAmount(
                builder.getTargetTransaction(), paymentAddress, Coin.valueOf(0)));
        assertEquals(Coin.valueOf(0), builder.getFee());
    }

    @Test
    void signsTheConnectedInputAndProducesASpendableTransaction() {
        TransactionBuilder builder = new TransactionBuilder(bap, keyBag);
        builder.addInput(utxo(6, 0, 20_000))
                .addOutput(Coin.valueOf(777), paymentAddress)
                .changeAddress(paymentAddress);

        Transaction signed = builder.completeAndSignTx(keyBag, true);

        assertNotNull(signed.getInput(0).getScriptSig());
        assertTrue(signed.getInput(0).getScriptBytes().length > 0);
        assertEquals(signed.getInputSum(), signed.getOutputSum().add(builder.getFee()));
        ScriptUtils_legacy.correctlySpends(signed.getInput(0).getScriptSig(), signed, 0,
                signed.getInput(0).getConnectedOutput().getScriptPubKey(),
                signed.getInput(0).getConnectedOutput().getValue(), ScriptVerifyFlag.GENESIS_SET);
    }

    @Test
    void reportsSignerFailureInsteadOfReturningAnUnsignedTransaction() {
        TransactionSigner refusingSigner = mock(TransactionSigner.class);
        TransactionBuilder builder = new TransactionBuilder(bap, keyBag, refusingSigner);
        builder.addInput(utxo(7, 0, 20_000))
                .addOutput(Coin.valueOf(777), paymentAddress);

        assertThrows(SignErrorException.class, () -> builder.completeAndSignTx(keyBag, true));
    }

    @Test
    void broadcastDelegatesOnlyToTheProvidedLocalFunction() {
        AtomicReference<String> receivedRaw = new AtomicReference<>();

        String result = TransactionBuilder.broadcast("010203", raw -> {
            receivedRaw.set(raw);
            return "local-txid";
        });

        assertEquals("010203", receivedRaw.get());
        assertEquals("local-txid", result);
    }

    @Test
    void constructorsPreserveTheSelectedNetworkAndExistingTransaction() {
        Address testAddress = new Address(Net.TESTNET3.params(), bap.getPayAccountKey().getPubKeyHash());
        Transaction existing = new Transaction(Net.TESTNET3);
        existing.addOutput(Coin.valueOf(11), testAddress);
        TransactionBuilder builder = new TransactionBuilder(bap, Net.TESTNET3, existing, keyBag);
        assertSame(existing, builder.getTargetTransaction());
        assertEquals(Net.TESTNET3, builder.getNet());
        assertSame(keyBag, builder.getKeyBag());
        assertInstanceOf(LocalTransactionSigner.class, builder.getTransactionSigner());
        builder.addOutput(Coin.valueOf(22), testAddress);
        assertEquals(Coin.valueOf(33), existing.getOutputSum());
        assertEquals(Coin.valueOf(11), existing.getOutput(0).getValue());

        TransactionSigner signer = mock(TransactionSigner.class);
        TransactionBuilder explicit = new TransactionBuilder(bap, Net.TESTNET3, existing, signer, keyBag);
        assertSame(existing, explicit.getTargetTransaction());
        assertSame(signer, explicit.getTransactionSigner());
        TransactionBuilder empty = new TransactionBuilder(bap, Net.TESTNET3, keyBag);
        assertEquals(Net.TESTNET3, empty.getNet());
        assertEquals(0, empty.getTargetTransaction().getOutputs().size());
    }

    @Test
    void sigmaSigningKeepsThePayloadAndExposesAVerifiableCurrentIdentitySignature() {
        BapDataLockBuilder data = new BapDataLockBuilder(bap).buildAlias("profile");
        TransactionBuilder builder = new TransactionBuilder(bap, keyBag)
                .addInput(utxo(8, 0, 20_000)).addDataOutput(data);
        List<byte[]> payload = builder.getTargetTransaction().getOutput(0).getScriptPubKey().getChunks()
                .stream().skip(2).map(chunk -> chunk.data()).toList();
        assertNull(builder.getSigma());
        assertSame(builder, builder.addSigmaSign(bap));
        Sigma sigma = builder.getSigma();
        assertSame(builder.getTargetTransaction(), sigma.getTransaction());
        assertEquals(bap.getCurrentAddress().toBase58(), sigma.getSig().getAddress());
        assertEquals(1, sigma.getSigInstanceCount());
        assertTrue(new Sigma(bap, builder.getTargetTransaction()).verify());
        assertEquals(Coin.valueOf(20_000), builder.getTargetTransaction().getInputSum());
        assertEquals(Coin.ZERO, builder.getTargetTransaction().getOutput(0).getValue());
        for (int i = 0; i < payload.size(); i++) {
            assertArrayEquals(payload.get(i), builder.getTargetTransaction().getOutput(0)
                    .getScriptPubKey().getChunks().get(i + 2).data());
        }
    }

    @Test
    void publicOnlyKeyUsesAConservativeUnlockingSizeForChangeEstimation() {
        KeyBag publicOnlyBag = mock(KeyBag.class);
        when(publicOnlyBag.findKeyFromPubHash(any(byte[].class)))
                .thenReturn(ECKey.fromPublicOnly(bap.getPayAccountKey().getPubKey()));
        TransactionBuilder full = new TransactionBuilder(bap, keyBag)
                .addInput(utxo(9, 0, 20_000)).addOutput(Coin.valueOf(777), paymentAddress);
        TransactionBuilder publicOnly = new TransactionBuilder(bap, publicOnlyBag)
                .addInput(utxo(9, 0, 20_000)).addOutput(Coin.valueOf(777), paymentAddress);

        Coin fullChange = full.calculateChangeAmount(full.getTargetTransaction(), paymentAddress, Coin.valueOf(1000));
        Coin publicChange = publicOnly.calculateChangeAmount(
                publicOnly.getTargetTransaction(), paymentAddress, Coin.valueOf(1000));
        // 缺少私钥时保守按 65 字节公钥计费，比已知压缩公钥多 32 字节。
        assertEquals(full.getFee().add(Coin.valueOf(32)), publicOnly.getFee());
        assertEquals(fullChange.subtract(Coin.valueOf(32)), publicChange);
        assertEquals(Coin.valueOf(19_223), publicChange.add(publicOnly.getFee()));
    }

    @Test
    void completionForwardsTheRequestedForkIdModeToTheSigner() {
        TransactionSigner signer = mock(TransactionSigner.class);
        when(signer.signInputs(any(), eq(keyBag))).thenReturn(true);
        TransactionBuilder builder = new TransactionBuilder(bap, keyBag, signer)
                .addInput(utxo(10, 0, 20_000)).addOutput(Coin.valueOf(777), paymentAddress);
        builder.completeAndSignTx(keyBag, false);
        ArgumentCaptor<TransactionSigner.ProposedTransaction> captured =
                ArgumentCaptor.forClass(TransactionSigner.ProposedTransaction.class);
        verify(signer).signInputs(captured.capture(), eq(keyBag));
        assertEquals(false, ((ExtendForSignTransaction) captured.getValue()).isUseForkId());
    }

    private UTXO utxo(int hashByte, long index, long satoshis) {
        byte[] hash = new byte[32];
        hash[0] = (byte) hashByte;
        return new UTXO(Sha256Hash.wrap(hash), index, Coin.valueOf(satoshis),
                0, false, null, paymentAddress.toBase58());
    }
}
