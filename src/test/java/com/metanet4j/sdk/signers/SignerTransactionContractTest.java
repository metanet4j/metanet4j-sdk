package com.metanet4j.sdk.signers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.bap.BapBase;
import com.metanet4j.sdk.exception.SignErrorException;
import io.bitcoinsv.bitcoinjsv.core.Address;
import io.bitcoinsv.bitcoinjsv.core.Coin;
import io.bitcoinsv.bitcoinjsv.core.ECKey;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.params.Net;
import io.bitcoinsv.bitcoinjsv.params.SerializeMode;
import io.bitcoinsv.bitcoinjsv.script.ScriptBuilder;
import io.bitcoinsv.bitcoinjsv.script.ScriptUtils_legacy;
import io.bitcoinsv.bitcoinjsv.script.ScriptVerifyFlag;
import io.bitcoinsv.bitcoinjsv.script.SigHash;
import io.bitcoinsv.bitcoinjsv.temp.KeyBag;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
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

        ExtendForSignTransaction forkIdAndAnyone = new ExtendForSignTransaction(tx, true, true);
        assertTrue(forkIdAndAnyone.isUseForkId());
        assertTrue(forkIdAndAnyone.isAnyoneCanPay());
        assertEquals(SigHash.Flags.ALL, forkIdAndAnyone.getHashFlags());
    }

    @Test
    void twoArgumentRequestKeepsItsForkIdFlag() {
        Transaction tx = new Transaction(Net.MAINNET);
        assertTrue(new ExtendForSignTransaction(tx, true).isUseForkId());
        assertFalse(new ExtendForSignTransaction(tx, false).isUseForkId());
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
    void transactionConversionRetainsTheOriginalNetwork() {
        Transaction original = new Transaction(Net.TESTNET3);
        original.addInput(Sha256Hash.wrap(new byte[32]), 3, new ScriptBuilder().build());
        original.addOutput(Coin.valueOf(546), new ScriptBuilder().data(new byte[] {1, 2}).build());
        TransactionExtend converted = TransactionExtend.toTransactionExtend(original);
        assertEquals(Net.TESTNET3, converted.getNet());
        assertArrayEquals(original.bitcoinSerialize(), converted.bitcoinSerialize());
    }

    @Test
    void parserConstructorsReadTheSameTransactionWithOffsetsAndRetentionModes() {
        byte[] raw = HexFormat.of().parseHex("0200000001" + "00".repeat(32)
                + "0300000000ffffffff0122020000000000000302010219000000");
        byte[] prefixed = ByteBuffer.allocate(raw.length + 4).putInt(42).put(raw).array();
        List<TransactionExtend> transactions = List.of(
                new TransactionExtend(Net.TESTNET3, raw),
                new TransactionExtend(Net.TESTNET3, prefixed, 4),
                new TransactionExtend(Net.TESTNET3, prefixed, 4, null, SerializeMode.DEFAULT, raw.length),
                new TransactionExtend(Net.TESTNET3, raw, null, new SerializeMode(false, true), raw.length));
        for (TransactionExtend tx : transactions) {
            assertEquals(Net.TESTNET3, tx.getNet());
            assertEquals(2, tx.getVersion());
            assertEquals(25, tx.getLockTime());
            assertEquals(3, tx.getInput(0).getOutpoint().getIndex());
            assertEquals(Coin.valueOf(546), tx.getOutput(0).getValue());
            assertArrayEquals(raw, tx.bitcoinSerialize());
        }
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

    @Test
    void statelessSignerIsReadyBeforeAndAfterStateSerialization() {
        OrdinalsTransactionSigner signer = new OrdinalsTransactionSigner();
        assertTrue(signer.isReady());
        byte[] serialized = signer.serialize();
        assertArrayEquals(new byte[0], serialized);
        signer.deserialize(serialized);
        assertTrue(signer.isReady());
        assertArrayEquals(serialized, signer.serialize());
    }

    @Test
    void singleInputSigningCreatesAValidForkIdSignature() {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        Transaction spending = spending(bap);
        assertTrue(OrdinalsTransactionSigner.signInput(spending, 0,
                new BapBase.BapProviderKeyBag(bap), SigHash.Flags.ALL, false));
        byte[] signature = spending.getInput(0).getScriptSig().getChunks().get(0).data();
        assertEquals(0x41, signature[signature.length - 1] & 0xff);
        verifySpends(spending, ScriptVerifyFlag.GENESIS_SET);
    }

    @Test
    void batchSigningSupportsLegacyHashingAndAnyoneCanPay() {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        Transaction spending = spending(bap);
        assertTrue(new OrdinalsTransactionSigner().signInputs(
                new ExtendForSignTransaction(spending, SigHash.Flags.SINGLE, false, true),
                new BapBase.BapProviderKeyBag(bap)));
        byte[] signature = spending.getInput(0).getScriptSig().getChunks().get(0).data();
        assertEquals(0x83, signature[signature.length - 1] & 0xff);
        verifySpends(spending, EnumSet.noneOf(ScriptVerifyFlag.class));
    }

    @Test
    void publicOnlyKeysAreSkippedByBatchAndReportedUnavailableBySingleInput() {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        KeyBag watchOnly = keyBag(ECKey.fromPublicOnly(bap.getPayAccountKey().getPubKey()));
        Transaction spending = spending(bap);
        byte[] unsigned = spending.getInput(0).getScriptBytes();
        assertTrue(new OrdinalsTransactionSigner().signInputs(new ExtendForSignTransaction(spending), watchOnly));
        assertArrayEquals(unsigned, spending.getInput(0).getScriptBytes());
        assertFalse(OrdinalsTransactionSigner.signInput(spending, 0, watchOnly, SigHash.Flags.ALL, false));
        assertArrayEquals(unsigned, spending.getInput(0).getScriptBytes());
    }

    @Test
    void keyLockFailuresPropagateWithoutChangingTheInputScript() {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        ECKey key = spy(ECKey.fromPrivate(bap.getPayAccountKey().getPrivKeyBytes()));
        ECKey.KeyIsEncryptedException failure = new ECKey.KeyIsEncryptedException();
        // 只在 KeyBag 提供的签名边界注入钱包锁定，交易、脚本及其余密钥行为保持真实。
        doThrow(failure).when(key).sign(any(Sha256Hash.class));
        KeyBag keys = keyBag(key);
        Transaction spending = spending(bap);
        byte[] unsigned = spending.getInput(0).getScriptBytes();
        assertSame(failure, assertThrows(ECKey.KeyIsEncryptedException.class,
                () -> OrdinalsTransactionSigner.signInput(spending, 0, keys, SigHash.Flags.ALL, false)));
        assertSame(failure, assertThrows(ECKey.KeyIsEncryptedException.class,
                () -> new OrdinalsTransactionSigner().signInputs(
                        new ExtendForSignTransaction(spending, true, false), keys)));
        assertArrayEquals(unsigned, spending.getInput(0).getScriptBytes());
    }

    @Test
    void privateKeyAccessLostDuringSigningLeavesTheInputUnsigned() {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        ECKey key = spy(ECKey.fromPrivate(bap.getPayAccountKey().getPrivKeyBytes()));
        doThrow(new ECKey.MissingPrivateKeyException()).when(key).sign(any(Sha256Hash.class));
        KeyBag keys = keyBag(key);
        Transaction spending = spending(bap);
        byte[] unsigned = spending.getInput(0).getScriptBytes();
        assertTrue(new OrdinalsTransactionSigner().signInputs(
                new ExtendForSignTransaction(spending, true, false), keys));
        assertArrayEquals(unsigned, spending.getInput(0).getScriptBytes());
        assertFalse(OrdinalsTransactionSigner.signInput(spending, 0, keys, SigHash.Flags.ALL, false));
        assertArrayEquals(unsigned, spending.getInput(0).getScriptBytes());
    }

    private static Transaction spending(BapBase bap) {
        Transaction funding = new Transaction(Net.MAINNET);
        Address payment = new Address(bap.getPayAccountAddress());
        Transaction spending = new Transaction(Net.MAINNET);
        spending.addInput(funding.addOutput(Coin.valueOf(10_000), payment))
                .setScriptSig(ScriptBuilder.createInputScript(null, bap.getPayAccountKey().getPubKey()));
        spending.addOutput(Coin.valueOf(9_000), payment);
        return spending;
    }

    private static KeyBag keyBag(ECKey key) {
        KeyBag keys = mock(KeyBag.class);
        when(keys.findKeyFromPubHash(any(byte[].class))).thenReturn(key);
        return keys;
    }

    private static void verifySpends(Transaction spending, java.util.Set<ScriptVerifyFlag> flags) {
        ScriptUtils_legacy.correctlySpends(spending.getInput(0).getScriptSig(), spending, 0,
                spending.getInput(0).getConnectedOutput().getScriptPubKey(),
                spending.getInput(0).getConnectedOutput().getValue(), flags);
    }
}
