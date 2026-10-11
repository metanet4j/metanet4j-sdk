package com.metanet4j.sdk.remote;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.hutool.core.codec.Base64;
import com.metanet4j.sdk.PublicKey;
import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.bap.BapBase;
import com.metanet4j.sdk.bap.RemoteBapBase;
import com.metanet4j.sdk.context.PreSignHashContext;
import com.metanet4j.sdk.exception.SignErrorException;
import com.metanet4j.sdk.ordinals.OrdScriptBuilder;
import com.metanet4j.sdk.signers.ExtendForSignTransaction;
import com.metanet4j.sdk.transcation.LockingScriptBuilder;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.base.bsv.BsvAmount;
import com.metanet4j.base.bsv.BsvUtxo;
import com.metanet4j.bsv.transaction.Transaction;
import com.metanet4j.bsv.primitives.TransactionSignature;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.script.OP;
import com.metanet4j.sdk.PrivateKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RemoteTransactionContractTest {

    private final BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
    private final RemoteBapBase remoteBap = new RemoteBapBase(bap.getRootAddress(),
            bap.getPreviouAddress(), bap.getCurrentAddress(), bap.getPayAccountAddress(),
            bap.getOrdAddress(), bap.getAppName());
    private final AddressEnhance paymentAddress = bap.getPayAccountAddress();
    private final PrivateKey paymentKey = bap.getPayAccountKey();

    @AfterEach
    void clearSignedHashes() {
        PreSignHashUtils.clear();
    }

    @Test
    void requestsAndAppliesRemotePaymentSignatureInMemory() {
        RemoteTransactionBuilder builder = new RemoteTransactionBuilder(remoteBap, keyProvider);
        builder.addInput(paymentUtxo(1))
                .addOutput(BsvAmount.valueOf(777), paymentAddress)
                .changeAddress(paymentAddress);

        List<PreSignHashContext> requests = builder.getTxInputSignHash(false, remoteBap);
        assertEquals(1, requests.size());
        PreSignHashContext request = requests.get(0);
        assertEquals(RemoteSignType.PAYMENT, request.getRemoteSignType());
        assertEquals(paymentAddress.toBase58(), request.getSignAddress());
        assertEquals(builder.getFee(), request.getFee());
        sign(request, paymentKey);

        Transaction signed = builder.completeAndSignTx(null, true);
        assertTrue(unlockingScriptBytes(signed, 0).length > 0);
        ScriptUtils_legacy.correctlySpends(signed.inputs.get(0).getScriptSig(), signed, 0,
                sourceOutput(signed, 0).getScriptPubKey(),
                sourceOutput(signed, 0).getValue(), ScriptVerifyFlag.GENESIS_SET);
    }

    @Test
    void routesPaymentAndOrdinalHashesToTheirRespectiveAddresses() {
        RemoteTransactionBuilder builder = new RemoteTransactionBuilder(remoteBap, keyProvider);
        builder.addInput(paymentUtxo(2));
        Script inscription = OrdScriptBuilder.buildInscription(bap.getOrdAddress(),
                "prior".getBytes(StandardCharsets.US_ASCII), "text/plain", null);
        Transaction funding = new Transaction(Net.MAINNET);
        funding.addOutput(BsvAmount.SATOSHI, inscription);
        builder.addInput(funding.outputs.get(0));
        builder.addOutput(BsvAmount.valueOf(777), paymentAddress);

        List<PreSignHashContext> requests = builder.getTxInputSignHash(false, remoteBap);

        assertEquals(2, requests.size());
        assertEquals(RemoteSignType.PAYMENT, requests.get(0).getRemoteSignType());
        assertEquals(bap.getPayAccountAddress().toBase58(), requests.get(0).getSignAddress());
        assertEquals(RemoteSignType.ORD, requests.get(1).getRemoteSignType());
        assertEquals(bap.getOrdAddress().toBase58(), requests.get(1).getSignAddress());
    }

    @Test
    void collectsOnlyUnsignedDataHashesAndSkipsDisconnectedInputs() {
        RemoteTransactionBuilder builder = new RemoteTransactionBuilder(remoteBap, keyProvider);
        PreSignHashContext pending = new PreSignHashContext();
        LockingScriptBuilder data = mock(LockingScriptBuilder.class);
        Script script = new Script().writeOpCode(OP.OP_RETURN).writeBin(java.util.List.of(1));
        when(data.getLockingScript()).thenReturn(script);
        when(data.getPreSignHashContext()).thenReturn(pending);

        builder.addDataOutput(data);
        assertSame(pending, builder.getAllBsmSignHash(true, false, remoteBap).get(0));
        when(data.isHaveSign()).thenReturn(true);
        builder.addDataOutput(data);
        assertEquals(1, builder.getAllBsmSignHash(true, false, remoteBap).size());
        assertArrayEquals(script.toUint8Array(), lockingScriptBytes(builder.getTargetTransaction(), 0));

        byte[] hash = new byte[32];
        hash[0] = 3;
        builder.getTargetTransaction().addInput(new BsvHashBytes(hash).toString(), 0, new Script());
        assertTrue(builder.getTxInputSignHash(false, remoteBap).isEmpty());
    }

    @Test
    void signsTheRequestedSingleAnyoneCanPayHash() {
        RemoteTransactionBuilder builder = new RemoteTransactionBuilder(remoteBap, keyProvider);
        builder.addInput(paymentUtxo(4)).addOutput(BsvAmount.valueOf(777), paymentAddress);
        PreSignHashContext request = builder.getTxInputSignHash(
                TransactionSignature.SIGHASH_SINGLE, true, remoteBap).get(0);
        sign(request, paymentKey);
        ExtendForSignTransaction proposed = new ExtendForSignTransaction(
                builder.getTargetTransaction(), TransactionSignature.SIGHASH_SINGLE, true, true);

        assertTrue(new RemoteTransactionSigner().signInputs(proposed.partialTx, null,
                TransactionSignature.SIGHASH_SINGLE, true));
        assertUnlockingScriptValid(proposed.partialTx, 0);
    }

    @Test
    void remoteOrdinalBuilderReportsSignerFailure() {
        RemoteTransactionSigner signer = mock(RemoteTransactionSigner.class);
        RemoteOrdTransactionBuilder builder = new RemoteOrdTransactionBuilder(remoteBap, signer);
        builder.addInput(paymentUtxo(5));

        assertThrows(SignErrorException.class, () -> builder.signTx(null, true));
    }

    @Test
    void remoteOrdinalBuilderForwardsForkId() {
        RemoteTransactionSigner signer = mock(RemoteTransactionSigner.class);
        when(signer.signInputs(any(), any())).thenReturn(true);
        RemoteOrdTransactionBuilder builder = new RemoteOrdTransactionBuilder(remoteBap, signer);
        builder.addInput(paymentUtxo(7));
        builder.signTx(null, false);
        ArgumentCaptor<TransactionSigner.ProposedTransaction> captured =
                ArgumentCaptor.forClass(TransactionSigner.ProposedTransaction.class);
        verify(signer).signInputs(captured.capture(), any());
        assertEquals(false,
                ((ExtendForSignTransaction) captured.getValue()).isUseForkId());
    }

    @Test
    void remoteOrdinalOutputCarriesTheRequestedContentAndPendingBsmHash() {
        RemoteOrdTransactionBuilder builder = new RemoteOrdTransactionBuilder(remoteBap,
                new RemoteTransactionSigner());
        builder.addInput(paymentUtxo(6));
        byte[] content = "new inscription".getBytes(StandardCharsets.US_ASCII);

        builder.addOrdOutput(bap.getOrdAddress(), content, "text/plain", null,
                RemoteSignType.CURRENT);

        assertEquals(BsvAmount.SATOSHI, builder.build().outputs.get(0).getValue());
        assertArrayEquals(OrdScriptBuilder.buildInscription(bap.getOrdAddress(), content,
                "text/plain", null).toUint8Array(), lockingScriptBytes(builder.build(), 0));
        assertEquals(1, builder.getAllBsmSignHash(true, false, remoteBap).size());
        assertEquals(RemoteSignType.CURRENT,
                builder.getAllBsmSignHash(true, false, remoteBap).get(0).getRemoteSignType());
    }

    @Test
    void remoteSignerLeavesAnAlreadyDisconnectedInputUntouched() {
        Transaction tx = new Transaction(Net.MAINNET);
        byte[] hash = new byte[32];
        hash[0] = 8;
        tx.addInput(Sha256Hash.wrap(hash), 0, new ScriptBuilder().build());
        tx.addOutput(BsvAmount.valueOf(777), paymentAddress);
        ExtendForSignTransaction proposed = new ExtendForSignTransaction(
                TransactionExtend.toTransactionExtend(tx), SigHash.Flags.ALL, true, false);

        assertTrue(new RemoteTransactionSigner().signInputs(proposed, null));
        assertEquals(0, proposed.unlockingScriptBytes(partialTx, 0).length);
    }

    @Test
    void fourArgumentRemoteOrdinalOutputUsesTheDefaultSigningIdentity() {
        RemoteOrdTransactionBuilder builder = new RemoteOrdTransactionBuilder(remoteBap,
                new RemoteTransactionSigner());
        builder.addInput(paymentUtxo(9));
        byte[] content = "default".getBytes(StandardCharsets.US_ASCII);

        assertSame(builder, builder.addOrdOutput(bap.getOrdAddress(), content,
                "text/plain", null));

        assertArrayEquals(OrdScriptBuilder.buildInscription(bap.getOrdAddress(), content,
                        "text/plain", null).toUint8Array(),
                lockingScriptBytes(builder.build(), 0));
        assertEquals(RemoteSignType.CURRENT,
                builder.getAllBsmSignHash(true, false, remoteBap).get(0).getRemoteSignType());
    }

    @Test
    void remoteSignerHasNoSerializedPrivateState() {
        RemoteTransactionSigner signer = new RemoteTransactionSigner();

        assertFalse(signer.isReady());
        assertArrayEquals(new byte[0], signer.serialize());
        signer.deserialize(new byte[] {1, 2, 3});
        assertArrayEquals(new byte[0], signer.serialize());
    }

    private BsvUtxo paymentUtxo(int firstHashByte) {
        byte[] hash = new byte[32];
        hash[0] = (byte) firstHashByte;
        return new BsvUtxo(Sha256Hash.wrap(hash), 0, BsvAmount.valueOf(20_000),
                0, false, null, paymentAddress.toBase58());
    }

    private void sign(PreSignHashContext request, PrivateKey key) {
        request.setSigB64(Base64.encode(toBytes(key.sign(request.getPreSignHash()).toDER())));
        request.setPublicKey(key.getPublicKey());
        PreSignHashUtils.setPreSignHashContext(request);
    }

    private static byte[] toBytes(java.util.List<?> values) {
        byte[] bytes = new byte[values.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ((Number) values.get(i)).intValue();
        }
        return bytes;
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

    private static byte[] unlockingScriptBytes(Transaction tx, int index) {
        return asScript(tx.inputs.get(index).unlockingScript).toUint8Array();
    }

    private static byte[] lockingScriptBytes(Transaction tx, int index) {
        return tx.outputs.get(index).lockingScript.toUint8Array();
    }
}
