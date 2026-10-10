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
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.base.bsv.BsvAmount;
import com.metanet4j.base.bsv.BsvHashBytes;
import com.metanet4j.base.bsv.BsvUtxo;
import com.metanet4j.bsv.transaction.Transaction;
import com.metanet4j.bsv.transaction.TransactionOutput;

import com.metanet4j.bsv.script.OP;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.script.templates.P2PKH;

import com.metanet4j.sdk.bap.SigningKeyProvider;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TransactionBuilderContractTest {

    private final BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
    private final SigningKeyProvider keyProvider = new BapBase.BapProviderKeyBag(bap);
    private final AddressEnhance paymentAddress = bap.getPayAccountAddress();

    @Test
    void addsProviderUtxoDataAndChangeWithoutLosingValue() {
        BsvUtxo utxo = utxo(1, 0, 20_000);
        UTXOProvider<AddressEnhance, BsvUtxo> provider = addresses -> {
            assertEquals(1, addresses.size());
            assertEquals(paymentAddress.toBase58(), addresses.get(0).toBase58());
            return List.of(utxo);
        };
        Script dataScript = new Script().writeOpCode(OP.OP_RETURN)
                .writeBin(toBoxed("payload".getBytes(StandardCharsets.US_ASCII)));
        LockingScriptBuilder data = mock(LockingScriptBuilder.class);
        when(data.getLockingScript()).thenReturn(dataScript);

        TransactionBuilder builder = new TransactionBuilder(bap, keyProvider);
        builder.addInput(bap.getPayAccountAddress(), provider)
                .addOutput(BsvAmount.valueOf(777), paymentAddress)
                .addDataOutput(data)
                .withFeeKb(BsvAmount.valueOf(500))
                .changeAddress(paymentAddress);
        Transaction tx = builder.getTargetTransaction();

        assertEquals(1, tx.inputs.size());
        assertEquals(utxo.getHash().toString(), tx.inputs.get(0).sourceTXID);
        assertEquals(utxo.getIndex(), tx.inputs.get(0).sourceOutputIndex);
        assertEquals(BsvAmount.valueOf(20_000), inputSum(tx));
        assertEquals(3, tx.outputs.size());
        assertEquals(BsvAmount.valueOf(777), BsvAmount.fromSdkValue(tx.outputs.get(0).satoshis));
        assertEquals(BsvAmount.valueOf(0), BsvAmount.fromSdkValue(tx.outputs.get(1).satoshis));
        assertArrayEquals(dataScript.toUint8Array(), tx.outputs.get(1).lockingScript.toUint8Array());
        assertEquals(builder.getChangeAmount(), BsvAmount.fromSdkValue(tx.outputs.get(2).satoshis));
        assertEquals(inputSum(tx), outputSum(tx).add(builder.getFee()));
        assertTrue(builder.getFee().getValue() > 0);
        assertTrue(builder.getChangeAmount().getValue() > 0);
    }

    @Test
    void acceptsAddressListUtxoListAndExistingFundingOutput() {
        BsvUtxo first = utxo(2, 1, 3_000);
        BsvUtxo second = utxo(3, 2, 4_000);
        UTXOProvider<AddressEnhance, BsvUtxo> provider = addresses -> {
            assertEquals(List.of(bap.getPayAccountAddress().toBase58()),
                    addresses.stream().map(AddressEnhance::toBase58).toList());
            return List.of(first, second);
        };
        Transaction funding = new Transaction();
        funding.outputs.add(new TransactionOutput(
                new P2PKH().lock(paymentAddress.getHash160()), 5_000d));

        TransactionBuilder builder = new TransactionBuilder(bap, keyProvider);
        builder.addInputs(List.of(bap.getPayAccountAddress()), provider)
                .addInputs(List.of(utxo(4, 3, 6_000)))
                .addInput(funding.outputs.get(0));

        assertEquals(4, builder.getTargetTransaction().inputs.size());
        assertEquals(BsvAmount.valueOf(18_000), inputSum(builder.getTargetTransaction()));
        assertNotNull(sourceOutput(builder.getTargetTransaction(), 3));
        assertEquals(BsvAmount.fromSdkValue(funding.outputs.get(0).satoshis),
                BsvAmount.fromSdkValue(sourceOutput(builder.getTargetTransaction(), 3).satoshis));
    }

    @Test
    void calculateChangeUsesTheFeeRatePassedToThePublicMethod() {
        TransactionBuilder builder = new TransactionBuilder(bap, keyProvider);
        builder.addInput(utxo(5, 0, 20_000))
                .addOutput(BsvAmount.valueOf(777), paymentAddress);

        assertEquals(BsvAmount.valueOf(19_223), builder.calculateChangeAmount(
                builder.getTargetTransaction(), paymentAddress, BsvAmount.valueOf(0)));
        assertEquals(BsvAmount.valueOf(0), builder.getFee());
    }

    @Test
    void signsTheConnectedInputAndProducesASpendableTransaction() {
        TransactionBuilder builder = new TransactionBuilder(bap, keyProvider);
        builder.addInput(utxo(6, 0, 20_000))
                .addOutput(BsvAmount.valueOf(777), paymentAddress)
                .changeAddress(paymentAddress);

        Transaction signed = builder.completeAndSignTx(keyProvider, true);

        assertNotNull(signed.inputs.get(0).unlockingScript);
        assertTrue(signed.inputs.get(0).lockingScript.toUint8Array().length > 0);
        assertEquals(inputSum(signed), outputSum(signed).add(builder.getFee()));
        assertUnlockingScriptValid(signed, 0);
    }

    @Test
    void reportsSignerFailureInsteadOfReturningAnUnsignedTransaction() {
        TransactionSigner refusingSigner = mock(TransactionSigner.class);
        TransactionBuilder builder = new TransactionBuilder(bap, keyProvider, refusingSigner);
        builder.addInput(utxo(7, 0, 20_000))
                .addOutput(BsvAmount.valueOf(777), paymentAddress);

        assertThrows(SignErrorException.class, () -> builder.completeAndSignTx(keyProvider, true));
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
        AddressEnhance testAddress = new AddressEnhance(Net.TESTNET3.params(), bap.getPayAccountKey().getPubKeyHash());
        Transaction existing = new Transaction();
        existing.addOutput(BsvAmount.valueOf(11), testAddress);
        TransactionBuilder builder = new TransactionBuilder(bap, Net.TESTNET3, existing, keyProvider);
        assertSame(existing, builder.getTargetTransaction());
        assertEquals(Net.TESTNET3, builder.getNet());
        assertSame(keyProvider, builder.getKeyBag());
        assertInstanceOf(LocalTransactionSigner.class, builder.getTransactionSigner());
        builder.addOutput(BsvAmount.valueOf(22), testAddress);
        assertEquals(BsvAmount.valueOf(33), outputSum(existing));
        assertEquals(BsvAmount.valueOf(11), BsvAmount.fromSdkValue(existing.outputs.get(0).satoshis));

        TransactionSigner signer = mock(TransactionSigner.class);
        TransactionBuilder explicit = new TransactionBuilder(bap, Net.TESTNET3, existing, signer, keyProvider);
        assertSame(existing, explicit.getTargetTransaction());
        assertSame(signer, explicit.getTransactionSigner());
        TransactionBuilder empty = new TransactionBuilder(bap, Net.TESTNET3, keyProvider);
        assertEquals(Net.TESTNET3, empty.getNet());
        assertEquals(0, empty.getTargetTransaction().outputs.size());
    }

    @Test
    void sigmaSigningKeepsThePayloadAndExposesAVerifiableCurrentIdentitySignature() {
        BapDataLockBuilder data = new BapDataLockBuilder(bap).buildAlias("profile");
        TransactionBuilder builder = new TransactionBuilder(bap, keyProvider)
                .addInput(utxo(8, 0, 20_000)).addDataOutput(data);
        List<byte[]> payload = builder.getTargetTransaction().outputs.get(0).getScriptPubKey().getChunks()
                .stream().skip(2).map(chunk -> chunk.writeBin(toBoxed())).toList();
        assertNull(builder.getSigma());
        assertSame(builder, builder.addSigmaSign(bap));
        Sigma sigma = builder.getSigma();
        assertSame(builder.getTargetTransaction(), sigma.getTransaction());
        assertEquals(bap.getCurrentAddress().toBase58(), sigma.getSig().getAddress());
        assertEquals(1, sigma.getSigInstanceCount());
        assertTrue(new Sigma(bap, builder.getTargetTransaction()).verify());
        assertEquals(BsvAmount.valueOf(20_000), inputSum(builder.getTargetTransaction()));
        assertEquals(BsvAmount.ZERO, BsvAmount.fromSdkValue(builder.getTargetTransaction().outputs.get(0).satoshis));
        for (int i = 0; i < payload.size(); i++) {
            assertArrayEquals(payload.get(i), builder.getTargetTransaction().outputs.get(0)
                    .getScriptPubKey().getChunks().get(i + 2).writeBin(toBoxed()));
        }
    }

    @Test
    void publicOnlyKeyUsesAConservativeUnlockingSizeForChangeEstimation() {
        KeyBag publicOnlyBag = mock(KeyBag.class);
        when(publicOnlyBag.findKeyFromPubHash(any(byte[].class)))
                .thenReturn(ECKey.fromPublicOnly(bap.getPayAccountKey().getPubKey()));
        TransactionBuilder full = new TransactionBuilder(bap, keyProvider)
                .addInput(utxo(9, 0, 20_000)).addOutput(BsvAmount.valueOf(777), paymentAddress);
        TransactionBuilder publicOnly = new TransactionBuilder(bap, publicOnlyBag)
                .addInput(utxo(9, 0, 20_000)).addOutput(BsvAmount.valueOf(777), paymentAddress);

        Coin fullChange = full.calculateChangeAmount(full.getTargetTransaction(), paymentAddress, BsvAmount.valueOf(1000));
        Coin publicChange = publicOnly.calculateChangeAmount(
                publicOnly.getTargetTransaction(), paymentAddress, BsvAmount.valueOf(1000));
        // 缺少私钥时保守按 65 字节公钥计费，比已知压缩公钥多 32 字节。
        assertEquals(full.getFee().add(BsvAmount.valueOf(32)), publicOnly.getFee());
        assertEquals(fullChange.subtract(BsvAmount.valueOf(32)), publicChange);
        assertEquals(BsvAmount.valueOf(19_223), publicChange.add(publicOnly.getFee()));
    }

    @Test
    void completionForwardsTheRequestedForkIdModeToTheSigner() {
        TransactionSigner signer = mock(TransactionSigner.class);
        when(signer.signInputs(any(), eq(keyProvider))).thenReturn(true);
        TransactionBuilder builder = new TransactionBuilder(bap, keyProvider, signer)
                .addInput(utxo(10, 0, 20_000)).addOutput(BsvAmount.valueOf(777), paymentAddress);
        builder.completeAndSignTx(keyProvider, false);
        ArgumentCaptor<TransactionSigner.ProposedTransaction> captured =
                ArgumentCaptor.forClass(TransactionSigner.ProposedTransaction.class);
        verify(signer).signInputs(captured.capture(), eq(keyProvider));
        assertEquals(false, ((ExtendForSignTransaction) captured.getValue()).isUseForkId());
    }

    private BsvUtxo utxo(int hashByte, long index, long satoshis) {
        byte[] hash = new byte[32];
        hash[0] = (byte) hashByte;
        return new BsvUtxo(Sha256Hash.wrap(hash), index, BsvAmount.valueOf(satoshis),
                0, false, null, paymentAddress.toBase58());
    }

    private static List<Integer> toBoxed(byte[] data) {
        List<Integer> out = new java.util.ArrayList<>(data.length);
        for (byte b : data) {
            out.add(b & 0xFF);
        }
        return out;
    }

    private static void assertUnlockingScriptValid(Transaction tx, int index) {
        byte[] signature = com.metanet4j.sdk.utils.ScriptHelper
                .getPushData(asScript(tx.inputs.get(index).unlockingScript).getChunks().get(0));
        assertNotNull(signature, "解锁脚本首项应为签名");
    }

    private static Script asScript(Object unlockingScript) {
        if (unlockingScript instanceof Script script) {
            return script;
        }
        if (unlockingScript instanceof byte[] bytes) {
            return Script.fromBinary(bytes);
        }
        return new Script();
    }

    private static TransactionOutput sourceOutput(Transaction tx, int index) {
        if (tx.inputs.get(index).sourceTransaction instanceof Transaction source
                && source.outputs != null && !source.outputs.isEmpty()) {
            return source.outputs.get(0);
        }
        throw new IllegalStateException("输入 " + index + " 缺少连接输出");
    }

    private static BsvAmount inputSum(Transaction tx) {
        BsvAmount sum = BsvAmount.ZERO;
        for (var input : tx.inputs) {
            if (input.sourceTransaction instanceof Transaction source && source.outputs != null
                    && !source.outputs.isEmpty()) {
                sum = sum.add(BsvAmount.fromSdkValue(source.outputs.get(0).satoshis));
            }
        }
        return sum;
    }

    private static BsvAmount outputSum(Transaction tx) {
        BsvAmount sum = BsvAmount.ZERO;
        for (var output : tx.outputs) {
            sum = sum.add(BsvAmount.fromSdkValue(output.satoshis));
        }
        return sum;
    }
}