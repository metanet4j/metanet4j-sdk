package com.metanet4j.sdk.signers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.bap.BapBase;
import com.metanet4j.sdk.exception.SignErrorException;
import io.bitcoinsv.bitcoinjsv.core.Address;
import io.bitcoinsv.bitcoinjsv.core.Coin;
import io.bitcoinsv.bitcoinjsv.core.ECKey;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.params.Net;
import io.bitcoinsv.bitcoinjsv.script.ScriptBuilder;
import io.bitcoinsv.bitcoinjsv.script.SigHash;
import org.junit.jupiter.api.Test;

class SignerTransactionContractTest {

    @Test
    void proposedTransactionKeepsRequestedSigningMode() {
        Transaction tx = new Transaction(Net.MAINNET);
        ExtendForSignTransaction defaults = new ExtendForSignTransaction(tx);
        assertFalse(defaults.isUseForkId());
        assertFalse(defaults.isAnyoneCanPay());
        assertEquals(SigHash.Flags.ALL, defaults.getHashFlags());

        ExtendForSignTransaction configured = new ExtendForSignTransaction(
                tx, SigHash.Flags.SINGLE, true, true);
        assertTrue(configured.isUseForkId());
        assertTrue(configured.isAnyoneCanPay());
        assertEquals(SigHash.Flags.SINGLE, configured.getHashFlags());
    }

    @Test
    void transactionConversionPreservesSerializedHeaderAndPayments() {
        Transaction original = new Transaction(Net.MAINNET);
        original.setVersion(2);
        original.setLockTime(25);
        original.addInput(Sha256Hash.wrap(new byte[32]), 3, new ScriptBuilder().build());
        original.addOutput(Coin.valueOf(546), new ScriptBuilder().data(new byte[] {1, 2}).build());

        TransactionExtend converted = TransactionExtend.toTransactionExtend(original);

        assertEquals(original.getVersion(), converted.getVersion());
        assertEquals(original.getLockTime(), converted.getLockTime());
        assertArrayEquals(original.bitcoinSerialize(), converted.bitcoinSerialize());
    }

    @Test
    void signerUsesRequestedForkIdHashFlagsAndAnyoneCanPay() {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        ECKey key = ECKey.fromPrivate(bap.getPayAccountKey().getPrivKeyBytes());
        Transaction funding = new Transaction(Net.MAINNET);
        Transaction spending = new Transaction(Net.MAINNET);
        Address payment = new Address(bap.getPayAccountAddress());
        spending.addInput(funding.addOutput(Coin.valueOf(10_000), payment))
                .setScriptSig(ScriptBuilder.createInputScript(null, key.getPubKey()));
        spending.addOutput(Coin.valueOf(9_000), payment);
        ExtendForSignTransaction proposed = new ExtendForSignTransaction(
                spending, SigHash.Flags.SINGLE, true, true);

        assertTrue(new OrdinalsTransactionSigner().signInputs(
                proposed, new BapBase.BapProviderKeyBag(bap)));
        byte[] signature = spending.getInput(0).getScriptSig().getChunks().get(0).data();
        assertEquals(0xC3, signature[signature.length - 1] & 0xFF);
    }

    @Test
    void missingConnectedOutputHasExplicitSingleInputErrorAndBatchSkip() {
        Transaction spending = new Transaction(Net.MAINNET);
        spending.addInput(Sha256Hash.wrap(new byte[32]), 0, new ScriptBuilder().build());
        byte[] before = spending.getInput(0).getScriptBytes();
        OrdinalsTransactionSigner signer = new OrdinalsTransactionSigner();

        assertThrows(SignErrorException.class, () -> OrdinalsTransactionSigner.signInput(
                spending, 0, null, SigHash.Flags.ALL, false));
        assertTrue(signer.signInputs(new ExtendForSignTransaction(spending), null));
        assertArrayEquals(before, spending.getInput(0).getScriptBytes());
    }
}
