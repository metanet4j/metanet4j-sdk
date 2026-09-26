package com.metanet4j.sdk.bap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.metanet4j.sdk.TestData;
import io.bitcoinsv.bitcoinjsv.crypto.ChildNumber;
import java.util.List;
import org.junit.jupiter.api.Test;

class BapBaseLifecycleTest {

    @Test
    void rotatingSignatureKeysPreservesIdentityAndTracksPreviousKey() {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        String identity = bap.getIdentityKey();
        String rootAddress = bap.getRootAddress();
        String firstAddress = bap.getCurrentAddress().toBase58();
        assertEquals(rootAddress, bap.getPreviouAddress().toBase58());

        String firstPath = bap.getCurrentPath();
        List<ChildNumber> secondPath = bap.generateNextChildNumbers();
        assertEquals(firstPath, bap.getPreviousPath());
        assertEquals(firstAddress, bap.getPreviouAddress().toBase58());
        assertEquals(identity, bap.getIdentityKey());
        assertEquals(rootAddress, bap.getRootAddress());
        assertEquals(2, secondPath.getLast().num());
        assertNotEquals(firstAddress, bap.getCurrentAddress().toBase58());

        String secondAddress = bap.getCurrentAddress().toBase58();
        bap.generateNextChildNumbers();
        assertEquals(3, bap.getCurrentNumberList().getLast().num());
        assertEquals(secondAddress, bap.getPreviouAddress().toBase58());
    }

    @Test
    void reconstructingRotatedIdentityRecoversThePreviousSignatureKey() {
        BapBase rotating = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        String previousAddress = rotating.getCurrentAddress().toBase58();
        rotating.generateNextChildNumbers();

        BapBase reconstructed = new BapBase(TestData.masterPrivateKey,
                List.copyOf(rotating.getRootChildNumberList()),
                List.copyOf(rotating.getCurrentNumberList()), new DefaultBapBaseConfig());
        assertEquals(rotating.getIdentityKey(), reconstructed.getIdentityKey());
        assertEquals(rotating.getCurrentAddress(), reconstructed.getCurrentAddress());
        assertEquals(previousAddress, reconstructed.getPreviouAddress().toBase58());
    }

    @Test
    void signatureAddressLookupReturnsMatchingIdentityOrReportsFailure() {
        BapBase expected = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        BapBase found = BapBase.fromSignatureAddress(TestData.masterPrivateKey,
                expected.getIdentityKey(), expected.getCurrentAddress().toBase58(), 3);
        assertNotNull(found);
        assertEquals(expected.getIdentityKey(), found.getIdentityKey());
        assertEquals(expected.getCurrentAddress(), found.getCurrentAddress());

        assertThrows(IllegalArgumentException.class, () -> BapBase.fromSignatureAddress(
                TestData.masterPrivateKey, expected.getIdentityKey(), expected.getCurrentAddress().toBase58(), 101));
        assertThrows(IllegalArgumentException.class, () -> BapBase.fromSignatureAddress(
                TestData.masterPrivateKey, expected.getIdentityKey(), expected.getCurrentAddress().toBase58(), 0));
        assertThrows(IllegalStateException.class, () -> BapBase.fromSignatureAddress(
                TestData.masterPrivateKey, "unknown-identity", expected.getCurrentAddress().toBase58(), 3));
        BapBase rootAsCurrent = BapBase.fromSignatureAddress(TestData.masterPrivateKey,
                expected.getIdentityKey(), expected.getRootAddress(), 3);
        assertEquals(expected.getRootAddress(), rootAsCurrent.getCurrentAddress().toBase58());
        assertEquals(expected.getRootAddress(), rootAsCurrent.getPreviouAddress().toBase58());
        assertThrows(IllegalStateException.class, () -> BapBase.fromSignatureAddress(
                TestData.masterPrivateKey, expected.getIdentityKey(), "invalid-address", 3));
    }

    @Test
    void signingPathHandlesUnsignedHexChunksAndHardening() {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        assertEquals("m/1'/2147483647'", bap.getSigningPathFromHex("000000017fffffff", true));
        assertEquals("m/1", bap.getSigningPathFromHex("80000000", false));
        assertEquals("m/1'", bap.getSigningPathFromHex("80000000", true));
    }
}
