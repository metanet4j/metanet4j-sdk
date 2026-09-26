package com.metanet4j.sdk.remote;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.metanet4j.sdk.signers.TransactionExtend;
import com.metanet4j.sdk.transcation.LockingScriptBuilder;
import io.bitcoinsv.bitcoinjsv.core.Address;
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
import io.bitcoinsv.bitcoinjsv.script.SigHash;
import io.bitcoinsv.bitcoinjsv.signers.TransactionSigner;
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
    private final Address paymentAddress = new Address(bap.getPayAccountAddress());
    private final ECKey paymentKey = ECKey.fromPrivate(bap.getPayAccountKey().getPrivKeyBytes());

    @AfterEach
    void clearSignedHashes() {
        PreSignHashUtils.clear();
    }

    @Test
    void requestsAndAppliesRemotePaymentSignatureInMemory() {
        RemoteTransactionBuilder builder = new RemoteTransactionBuilder(remoteBap,
                new RemoteTransactionSigner());
        builder.addInput(paymentUtxo(1))
                .addOutput(Coin.valueOf(777), paymentAddress)
                .changeAddress(paymentAddress);

        List<PreSignHashContext> requests = builder.getTxInputSignHash(false, remoteBap);
        assertEquals(1, requests.size());
        PreSignHashContext request = requests.get(0);
        assertEquals(RemoteSignType.PAYMENT, request.getRemoteSignType());
        assertEquals(paymentAddress.toBase58(), request.getSignAddress());
        assertEquals(builder.getFee(), request.getFee());
        sign(request, paymentKey);

        Transaction signed = builder.completeAndSignTx(null, true);
        assertTrue(signed.getInput(0).getScriptBytes().length > 0);
        ScriptUtils_legacy.correctlySpends(signed.getInput(0).getScriptSig(), signed, 0,
                signed.getInput(0).getConnectedOutput().getScriptPubKey(),
                signed.getInput(0).getConnectedOutput().getValue(), ScriptVerifyFlag.GENESIS_SET);
    }

    @Test
    void routesPaymentAndOrdinalHashesToTheirRespectiveAddresses() {
        RemoteTransactionBuilder builder = new RemoteTransactionBuilder(remoteBap,
                new RemoteTransactionSigner());
        builder.addInput(paymentUtxo(2));
        Script inscription = OrdScriptBuilder.buildInscription(bap.getOrdAddress(),
                "prior".getBytes(StandardCharsets.US_ASCII), "text/plain", null);
        Transaction funding = new Transaction(Net.MAINNET);
        funding.addOutput(Coin.SATOSHI, inscription);
        builder.addInput(funding.getOutput(0));
        builder.addOutput(Coin.valueOf(777), paymentAddress);

        List<PreSignHashContext> requests = builder.getTxInputSignHash(false, remoteBap);

        assertEquals(2, requests.size());
        assertEquals(RemoteSignType.PAYMENT, requests.get(0).getRemoteSignType());
        assertEquals(bap.getPayAccountAddress().toBase58(), requests.get(0).getSignAddress());
        assertEquals(RemoteSignType.ORD, requests.get(1).getRemoteSignType());
        assertEquals(bap.getOrdAddress().toBase58(), requests.get(1).getSignAddress());
    }

    @Test
    void collectsOnlyUnsignedDataHashesAndSkipsDisconnectedInputs() {
        RemoteTransactionBuilder builder = new RemoteTransactionBuilder(remoteBap,
                new RemoteTransactionSigner());
        PreSignHashContext pending = new PreSignHashContext();
        LockingScriptBuilder data = mock(LockingScriptBuilder.class);
        Script script = new ScriptBuilder().op(ScriptOpCodes.OP_RETURN).data(new byte[] {1}).build();
        when(data.getLockingScript()).thenReturn(script);
        when(data.getPreSignHashContext()).thenReturn(pending);

        builder.addDataOutput(data);
        assertSame(pending, builder.getAllBsmSignHash(true, false, remoteBap).get(0));
        when(data.isHaveSign()).thenReturn(true);
        builder.addDataOutput(data);
        assertEquals(1, builder.getAllBsmSignHash(true, false, remoteBap).size());
        assertArrayEquals(script.getProgram(), builder.getTargetTransaction().getOutput(0).getScriptBytes());

        byte[] hash = new byte[32];
        hash[0] = 3;
        builder.getTargetTransaction().addInput(Sha256Hash.wrap(hash), 0, new ScriptBuilder().build());
        assertTrue(builder.getTxInputSignHash(false, remoteBap).isEmpty());
    }

    @Test
    void signsTheRequestedSingleAnyoneCanPayHash() {
        RemoteTransactionBuilder builder = new RemoteTransactionBuilder(remoteBap,
                new RemoteTransactionSigner());
        builder.addInput(paymentUtxo(4)).addOutput(Coin.valueOf(777), paymentAddress);
        PreSignHashContext request = builder.getTxInputSignHash(
                SigHash.Flags.SINGLE, true, remoteBap).get(0);
        sign(request, paymentKey);
        ExtendForSignTransaction proposed = new ExtendForSignTransaction(
                TransactionExtend.toTransactionExtend(builder.getTargetTransaction()),
                SigHash.Flags.SINGLE, true, true);

        assertTrue(new RemoteTransactionSigner().signInputs(proposed, null));
        ScriptUtils_legacy.correctlySpends(proposed.partialTx.getInput(0).getScriptSig(),
                proposed.partialTx, 0,
                proposed.partialTx.getInput(0).getConnectedOutput().getScriptPubKey(),
                proposed.partialTx.getInput(0).getConnectedOutput().getValue(),
                ScriptVerifyFlag.GENESIS_SET);
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

        assertEquals(Coin.SATOSHI, builder.build().getOutput(0).getValue());
        assertArrayEquals(OrdScriptBuilder.buildInscription(bap.getOrdAddress(), content,
                "text/plain", null).getProgram(), builder.build().getOutput(0).getScriptBytes());
        assertEquals(1, builder.getAllBsmSignHash(true, false, remoteBap).size());
        assertEquals(RemoteSignType.CURRENT,
                builder.getAllBsmSignHash(true, false, remoteBap).get(0).getRemoteSignType());
    }

    private UTXO paymentUtxo(int firstHashByte) {
        byte[] hash = new byte[32];
        hash[0] = (byte) firstHashByte;
        return new UTXO(Sha256Hash.wrap(hash), 0, Coin.valueOf(20_000),
                0, false, null, paymentAddress.toBase58());
    }

    private void sign(PreSignHashContext request, ECKey key) {
        request.setSigB64(Base64.encode(key.sign(request.getPreSignHash()).encodeToDER()));
        request.setPublicKey(PublicKey.fromBytes(key.getPubKey()));
        PreSignHashUtils.setPreSignHashContext(request);
    }
}
