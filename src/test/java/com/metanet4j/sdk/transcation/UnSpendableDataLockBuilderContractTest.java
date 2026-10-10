package com.metanet4j.sdk.transcation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metanet4j.base.aip.AipHelper;
import com.metanet4j.sdk.EcKeyLiteExtend;
import com.metanet4j.sdk.SignType;
import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.bap.BapBase;
import com.metanet4j.sdk.context.PreSignHashContext;
import com.metanet4j.sdk.remote.PreSignHashUtils;
import com.metanet4j.sdk.remote.RemoteSignType;
import com.metanet4j.bsv.script.ScriptChunk;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class UnSpendableDataLockBuilderContractTest {

    private final BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);

    @AfterEach
    void clearSignatures() {
        PreSignHashUtils.clear();
    }

    @Test
    void remoteRequestIsStableAndTheSameBuilderAcceptsItsSignature() {
        BapDataLockBuilder builder = new BapDataLockBuilder(bap, true, null).buildRoot();
        byte[] unsignedScript = builder.getLockingScript().toUint8Array();

        PreSignHashContext request = builder.getPreSignHashContext();
        assertEquals(request.getPreSignHash(), builder.getPreSignHashContext().getPreSignHash());
        assertArrayEquals(unsignedScript, builder.getLockingScript().toUint8Array());
        assertFalse(builder.isHaveSign());
        assertEquals(RemoteSignType.ROOT, request.getRemoteSignType());
        assertEquals(bap.getRootAddress(), request.getSignAddress());

        request.setSigB64(EcKeyLiteExtend.fromPrivate(bap.getRootPrivateKey().getPrivKey())
                .signHash(request.getPreSignHash()));
        PreSignHashUtils.setPreSignHashContext(request);
        assertSame(builder, builder.sign());
        assertTrue(builder.isHaveSign());
        assertEquals(11, builder.getLockingScript().getChunks().size());
        byte[] message = ("j1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdMTID"
                + bap.getIdentityKey() + bap.getRootAddress() + "|").getBytes(StandardCharsets.UTF_8);
        assertValidAip(builder, bap.getRootAddress(), message);
    }

    @Test
    void localSignaturesVerifyTheOriginalBinaryPayloadForEveryBapKey() {
        for (SignType type : List.of(SignType.ROOT, SignType.PREVIOUS, SignType.CURRENT)) {
            BapDataLockBuilder builder = new BapDataLockBuilder(binaryPayload(), bap, type);
            String address = switch (type) {
                case ROOT -> bap.getRootAddress();
                case PREVIOUS -> bap.getPreviouAddress().toBase58();
                default -> bap.getCurrentAddress().toBase58();
            };
            builder.sign();
            assertArrayEquals(new byte[] {0, (byte) 0x80, (byte) 0xff, 0x7c},
                    com.metanet4j.sdk.utils.ScriptHelper.getPushData(builder.getLockingScript().getChunks().get(2)));
            assertValidAip(builder, address, new byte[] {0x6a, 0, (byte) 0x80, (byte) 0xff, 0x7c, 0x7c});
        }
    }

    @Test
    void remoteSignatureVerifiesTheOriginalBinaryPayload() {
        BapDataLockBuilder builder = new BapDataLockBuilder(binaryPayload(), bap, true, RemoteSignType.CURRENT);
        PreSignHashContext request = builder.getPreSignHashContext();
        request.setSigB64(EcKeyLiteExtend.fromPrivate(bap.getCurrentPrivateKey().getPrivKey())
                .signHash(request.getPreSignHash()));
        PreSignHashUtils.setPreSignHashContext(request);
        builder.sign();
        assertValidAip(builder, bap.getCurrentAddress().toBase58(),
                new byte[] {0x6a, 0, (byte) 0x80, (byte) 0xff, 0x7c, 0x7c});
    }

    @Test
    void missingRemoteSignatureLeavesTheBuilderUnsignedAndRetryable() {
        BapDataLockBuilder builder = new BapDataLockBuilder(bap, true, null).buildAlias("profile");
        byte[] unsignedScript = builder.getLockingScript().toUint8Array();
        PreSignHashContext request = builder.getPreSignHashContext();
        assertThrows(RuntimeException.class, builder::sign);
        assertFalse(builder.isHaveSign());
        assertArrayEquals(unsignedScript, builder.getLockingScript().toUint8Array());
        request.setSigB64(EcKeyLiteExtend.fromPrivate(bap.getCurrentPrivateKey().getPrivKey())
                .signHash(request.getPreSignHash()));
        PreSignHashUtils.setPreSignHashContext(request);
        builder.sign();
        assertTrue(builder.isHaveSign());
        assertEquals(11, builder.getLockingScript().getChunks().size());
        assertValidAip(builder, bap.getCurrentAddress().toBase58(),
                ("j1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdMTALIAS" + bap.getIdentityKey() + "profile|")
                        .getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void remoteSignatureMustComeFromTheRequestedBapAddress() {
        BapDataLockBuilder builder = new BapDataLockBuilder(bap, true, RemoteSignType.CURRENT).buildAlias("profile");
        byte[] unsigned = builder.getLockingScript().toUint8Array();
        PreSignHashContext request = builder.getPreSignHashContext();
        request.setSignAddress(bap.getRootAddress());
        request.setSigB64(EcKeyLiteExtend.fromPrivate(bap.getRootPrivateKey().getPrivKey())
                .signHash(request.getPreSignHash()));
        PreSignHashUtils.setPreSignHashContext(request);
        assertThrows(IllegalArgumentException.class, builder::sign);
        assertFalse(builder.isHaveSign());
        assertArrayEquals(unsigned, builder.getLockingScript().toUint8Array());

        PreSignHashUtils.clear();
        request.setSignAddress(bap.getCurrentAddress().toBase58());
        request.setSigB64(EcKeyLiteExtend.fromPrivate(bap.getCurrentPrivateKey().getPrivKey())
                .signHash(request.getPreSignHash()));
        PreSignHashUtils.setPreSignHashContext(request);
        builder.sign();
        assertValidAip(builder, bap.getCurrentAddress().toBase58(),
                ("j1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdMTALIAS" + bap.getIdentityKey() + "profile|")
                        .getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void malformedRemoteSignaturesDoNotChangeTheUnsignedScript() {
        BapDataLockBuilder builder = new BapDataLockBuilder(bap, true, RemoteSignType.ROOT).buildRoot();
        byte[] unsigned = builder.getLockingScript().toUint8Array();
        PreSignHashContext request = builder.getPreSignHashContext();
        for (String invalid : List.of("AA==", "???")) {
            PreSignHashUtils.clear();
            request.setSigB64(invalid);
            PreSignHashUtils.setPreSignHashContext(request);
            assertThrows(RuntimeException.class, builder::sign);
            assertFalse(builder.isHaveSign());
            assertArrayEquals(unsigned, builder.getLockingScript().toUint8Array());
        }
    }

    @Test
    void nullLocalSignTypeUsesTheSameCurrentKeyAsTheDefaultConstructor() {
        BapDataLockBuilder defaultBuilder = new BapDataLockBuilder(binaryPayload(), bap).sign();
        BapDataLockBuilder nullTypeBuilder = new BapDataLockBuilder(binaryPayload(), bap, (SignType) null).sign();
        assertEquals(8, nullTypeBuilder.getLockingScript().getChunks().size());
        assertArrayEquals(defaultBuilder.getLockingScript().toUint8Array(), nullTypeBuilder.getLockingScript().toUint8Array());
        assertValidAip(nullTypeBuilder, bap.getCurrentAddress().toBase58(),
                new byte[] {0x6a, 0, (byte) 0x80, (byte) 0xff, 0x7c, 0x7c});
    }

    @Test
    void remoteEnumCannotSilentlyProduceAnIncompleteLocalSignature() {
        BapDataLockBuilder builder = new BapDataLockBuilder(binaryPayload(), bap, SignType.REMOTE);
        byte[] unsigned = builder.getLockingScript().toUint8Array();
        assertThrows(IllegalArgumentException.class, builder::sign);
        assertFalse(builder.isHaveSign());
        assertArrayEquals(unsigned, builder.getLockingScript().toUint8Array());
    }

    @Test
    void rebuildingBapDataClearsThePreviousSignedState() {
        BapDataLockBuilder builder = new BapDataLockBuilder(bap).buildRoot().sign();
        builder.buildAlias("profile");
        assertFalse(builder.isHaveSign());
        assertEquals(6, builder.getLockingScript().getChunks().size());
        builder.sign();
        assertTrue(builder.isHaveSign());
        assertValidAip(builder, bap.getCurrentAddress().toBase58(),
                ("j1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdMTALIAS" + bap.getIdentityKey() + "profile|")
                        .getBytes(StandardCharsets.UTF_8));
        builder.buildId();
        assertFalse(builder.isHaveSign());
        builder.sign();
        builder.buildRoot();
        assertFalse(builder.isHaveSign());
    }

    @Test
    void inferredRemoteKeyTracksTheOperationWhenTheBuilderIsReused() {
        BapDataLockBuilder builder = new BapDataLockBuilder(bap, true, null);
        assertEquals(RemoteSignType.ROOT, builder.buildRoot().getRemoteSignType());
        assertEquals(RemoteSignType.PREVIOUS, builder.buildId().getRemoteSignType());
        assertEquals(RemoteSignType.CURRENT, builder.buildAlias("profile").getRemoteSignType());
        assertEquals(bap.getCurrentAddress().toBase58(), builder.getPreSignHashContext().getSignAddress());
    }

    @Test
    void explicitRemoteKeyIsPreservedAcrossBapOperations() {
        BapDataLockBuilder builder = new BapDataLockBuilder(bap, true, RemoteSignType.ROOT);
        assertEquals(RemoteSignType.ROOT, builder.buildId().getRemoteSignType());
        assertEquals(RemoteSignType.ROOT, builder.buildAlias("profile").getRemoteSignType());
        assertEquals(bap.getRootAddress(), builder.getPreSignHashContext().getSignAddress());
    }

    private static List<ByteBuffer> binaryPayload() {
        return new ArrayList<>(List.of(ByteBuffer.wrap(new byte[] {0, (byte) 0x80, (byte) 0xff, 0x7c})));
    }

    private static void assertValidAip(BapDataLockBuilder builder, String address, byte[] message) {
        List<ScriptChunk> chunks = builder.getLockingScript().getChunks();
        int start = chunks.size() - 5;
        assertEquals("|", text(chunks.get(start)));
        assertEquals("15PciHG22SNLQJXMoSUaWVi7WSqc7hCfva", text(chunks.get(start + 1)));
        assertEquals("BITCOIN_ECDSA", text(chunks.get(start + 2)));
        assertEquals(address, text(chunks.get(start + 3)));
        String signature = Base64.getEncoder().encodeToString(
                com.metanet4j.sdk.utils.ScriptHelper.getPushData(chunks.get(start + 4)));
        assertTrue(AipHelper.verifySign(address, signature, message));
        byte[] changed = message.clone();
        changed[1] ^= 1;
        assertFalse(AipHelper.verifySign(address, signature, changed));
    }

    private static String text(ScriptChunk chunk) {
        byte[] data = com.metanet4j.sdk.utils.ScriptHelper.getPushData(chunk);
        return data == null ? "" : new String(data, StandardCharsets.UTF_8);
    }
}
