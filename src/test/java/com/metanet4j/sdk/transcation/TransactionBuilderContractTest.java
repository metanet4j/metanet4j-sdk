package com.metanet4j.sdk.transcation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import io.bitcoinsv.bitcoinjsv.params.Net;
import io.bitcoinsv.bitcoinjsv.script.Script;
import io.bitcoinsv.bitcoinjsv.script.ScriptBuilder;
import io.bitcoinsv.bitcoinjsv.script.ScriptOpCodes;
import io.bitcoinsv.bitcoinjsv.script.ScriptUtils_legacy;
import io.bitcoinsv.bitcoinjsv.script.ScriptVerifyFlag;
import io.bitcoinsv.bitcoinjsv.signers.TransactionSigner;
import io.bitcoinsv.bitcoinjsv.temp.KeyBag;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

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

    private UTXO utxo(int hashByte, long index, long satoshis) {
        byte[] hash = new byte[32];
        hash[0] = (byte) hashByte;
        return new UTXO(Sha256Hash.wrap(hash), index, Coin.valueOf(satoshis),
                0, false, null, paymentAddress.toBase58());
    }
}
