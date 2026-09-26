package com.metanet4j.sdk.sigma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metanet4j.sdk.EcKeyLiteExtend;
import com.metanet4j.sdk.SignType;
import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.bap.BapBase;
import com.metanet4j.sdk.context.PreSignHashContext;
import com.metanet4j.sdk.remote.PreSignHashUtils;
import com.metanet4j.sdk.remote.RemoteSignType;
import io.bitcoinsv.bitcoinjsv.core.Coin;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.params.Net;
import io.bitcoinsv.bitcoinjsv.script.ScriptBuilder;
import io.bitcoinsv.bitcoinjsv.script.ScriptOpCodes;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SigmaSignContractTest {

    @Test
    void signsAndVerifiesForEachLocalKeySelection() {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        Map<SignType, String> expectedAddresses = Map.of(
                SignType.CURRENT, bap.getCurrentAddress().toBase58(),
                SignType.PREVIOUS, bap.getPreviouAddress().toBase58(),
                SignType.ROOT, bap.getRootAddress(),
                SignType.REMOTE, AddressEnhance.fromECkeyLite(bap.getOrdPrivateKey()).toBase58());
        assertEquals(expectedAddresses.get(SignType.CURRENT), bap.getSignAddress(RemoteSignType.CURRENT));
        assertEquals(expectedAddresses.get(SignType.PREVIOUS), bap.getSignAddress(RemoteSignType.PREVIOUS));
        assertEquals(expectedAddresses.get(SignType.ROOT), bap.getSignAddress(RemoteSignType.ROOT));
        assertEquals(expectedAddresses.get(SignType.REMOTE), bap.getSignAddress(RemoteSignType.ORD));
        assertEquals(bap.getPayAccountAddress().toBase58(), bap.getSignAddress(RemoteSignType.PAYMENT));

        for (SignType type : SignType.values()) {
            Sigma sigma = new Sigma(bap, transaction(), type);
            Sha256Hash messageHash = sigma.getMessageHash();
            SignResponse response = sigma.sign();
            assertEquals(expectedAddresses.get(type), sigma.getSig().getAddress());
            assertEquals(messageHash, sigma.getMessageHash());
            assertEquals(1, sigma.getSigInstanceCount());
            assertEquals(sigma.getSig().getSignature(), response.getSignature());
            assertTrue(sigma.verify());
        }

        assertEquals(SignType.CURRENT, new Sigma(bap, transaction(), (SignType) null).getSignType());
        Sigma paymentRequest = new Sigma(bap, transaction(), true, RemoteSignType.PAYMENT);
        assertEquals(RemoteSignType.PAYMENT, paymentRequest.getPreSignHashContext().getRemoteSignType());
        assertEquals(bap.getPayAccountAddress().toBase58(),
                paymentRequest.getPreSignHashContext().getSignAddress());

        Sigma targetVin = new Sigma(bap, transaction(), 0, 0, -1);
        targetVin.sign();
        assertEquals(0, targetVin.getSig().getVin());
        assertTrue(targetVin.verify());
    }

    @Test
    void remoteSigningUsesMatchingPreSignHashAndClearsThreadContext() {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        Sigma sigma = new Sigma(bap, transaction(), true, null);
        assertEquals(RemoteSignType.CURRENT, sigma.getRemoteSignType());
        PreSignHashContext request = sigma.getPreSignHashContext();
        assertEquals(bap.getCurrentAddress().toBase58(), request.getSignAddress());
        assertEquals(RemoteSignType.CURRENT, request.getRemoteSignType());
        assertThrows(RuntimeException.class,
                () -> PreSignHashUtils.getSignedHashContext(request.getPreSignHash()));

        request.setSigB64(EcKeyLiteExtend.fromPrivate(bap.getCurrentPrivateKey().getPrivKey())
                .signMessage(sigma.getMessageHash().getBytes()));
        PreSignHashUtils.setPreSignHashContext(request);
        try {
            SignResponse response = sigma.sign();
            assertEquals(request.getSigB64(), response.getSignature());
            assertTrue(sigma.verify());
        } finally {
            PreSignHashUtils.clear();
        }
        assertThrows(RuntimeException.class,
                () -> PreSignHashUtils.getSignedHashContext(request.getPreSignHash()));
    }

    @Test
    void resigningReplacesTheSelectedInstanceWithoutDroppingOutputData() {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        Sigma sigma = new Sigma(bap, transaction(), SignType.CURRENT);
        sigma.sign();
        int firstPosition = sigma.getSigInstancePosition();
        sigma.sign();
        assertEquals(1, sigma.getSigInstanceCount());
        assertEquals(firstPosition, sigma.getSigInstancePosition());
        assertTrue(sigma.verify());

        sigma.setSigmaInstance(1);
        sigma.setHashes();
        sigma.sign();
        assertEquals(2, sigma.getSigInstanceCount());
        assertTrue(sigma.getSigInstancePosition() > firstPosition);
        assertTrue(sigma.verify());

        Sigma second = new Sigma(bap, sigma.getTransaction(), 0, 1, 0);
        assertEquals(sigma.getSig().getSignature(), second.getSig().getSignature());
        assertTrue(second.verify());

        sigma.setSigmaInstance(3);
        sigma.setHashes();
        assertEquals(-1, sigma.getSigInstancePosition());
        assertThrows(IllegalArgumentException.class, sigma::sign);
    }

    @Test
    void existingOpReturnUsesPipeSeparatorAndKeepsPriorData() {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        Transaction tx = new Transaction(Net.MAINNET);
        tx.addInput(Sha256Hash.wrap(new byte[32]), 0, new ScriptBuilder().build());
        tx.addOutput(Coin.valueOf(100), new ScriptBuilder()
                .op(ScriptOpCodes.OP_RETURN)
                .data("payload".getBytes(StandardCharsets.US_ASCII)).build());
        Sigma sigma = new Sigma(bap, tx, SignType.CURRENT);
        sigma.sign();
        assertEquals("|", new String(tx.getOutput(0).getScriptPubKey().getChunks().get(2).data(),
                StandardCharsets.US_ASCII));
        assertTrue(sigma.verify());
    }

    private static Transaction transaction() {
        Transaction tx = new Transaction(Net.MAINNET);
        tx.addInput(Sha256Hash.wrap(new byte[32]), 0, new ScriptBuilder().build());
        tx.addOutput(Coin.valueOf(100), new ScriptBuilder()
                .data("payload".getBytes(StandardCharsets.US_ASCII)).build());
        return tx;
    }
}
