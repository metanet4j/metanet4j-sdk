package com.metanet4j.sdk.bap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.metanet4j.sdk.PublicKey;
import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.crypto.Ecies;
import com.metanet4j.sdk.remote.RemoteSignType;
import io.bitcoinsv.bitcoinjsv.core.Address;
import io.bitcoinsv.bitcoinjsv.core.ECKeyLite;
import io.bitcoinsv.bitcoinjsv.params.MainNetParams;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BapBaseCoreContractTest {

    @Test
    void addressesAndEncryptionUseTheSelectedKeys() throws Exception {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        assertEquals(bap.getCurrentAddress().toBase58(), bap.getSignAddress(RemoteSignType.CURRENT));
        assertEquals(bap.getPreviouAddress().toBase58(), bap.getSignAddress(RemoteSignType.PREVIOUS));
        assertEquals(bap.getRootAddress(), bap.getSignAddress(RemoteSignType.ROOT));
        assertEquals(bap.getOrdAddress().toBase58(), bap.getSignAddress(RemoteSignType.ORD));
        assertEquals(bap.getPayAccountAddress().toBase58(), bap.getSignAddress(RemoteSignType.PAYMENT));

        byte[] plain = "encrypted payload".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(plain, Ecies.decrypt(bap.encrypt(plain), bap.getEncryptKey(), null));
        assertEquals("encrypted payload", bap.decrypt(bap.encrypt("encrypted payload")));
    }

    @Test
    void defaultAddressesUseTheirOwnPrivateKeys() {
        BapBaseCore core = fixedKeys();
        assertEquals(AddressEnhance.fromECkeyLite(key(2)).toBase58(), core.getRootAddress());
        assertEquals(AddressEnhance.fromECkeyLite(key(3)), core.getCurrentAddress());
        assertEquals(AddressEnhance.fromECkeyLite(key(4)), core.getPreviouAddress());
        assertEquals(new Address(MainNetParams.get(), key(5).getPubKeyHash()), core.getOrdAddress());
        assertEquals(AddressEnhance.fromECkeyLite(key(6)), core.getPayAccountAddress());
        assertEquals(core.getCurrentAddress().toBase58(), core.getSignAddress(RemoteSignType.CURRENT));
        assertEquals(core.getPreviouAddress().toBase58(), core.getSignAddress(RemoteSignType.PREVIOUS));
        assertEquals(core.getRootAddress(), core.getSignAddress(RemoteSignType.ROOT));
        assertEquals(core.getOrdAddress().toBase58(), core.getSignAddress(RemoteSignType.ORD));
        assertEquals(core.getPayAccountAddress().toBase58(), core.getSignAddress(RemoteSignType.PAYMENT));
    }

    @Test
    void invalidCiphertextAndMissingEncryptionKeyAreReported() {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        assertThrows(IllegalStateException.class, () -> bap.decrypt("not-a-ciphertext"));

        BapBaseCore missing = missingEncryptionKey();
        assertThrows(IllegalStateException.class, () -> missing.encrypt(new byte[] {1}));
        assertThrows(IllegalStateException.class, () -> missing.encrypt("message"));
        assertThrows(IllegalStateException.class, () -> missing.decrypt("not-a-ciphertext"));
    }

    private static BapBaseCore missingEncryptionKey() {
        return new BapBaseCore() {
            @Override public ECKeyLite getRootPrivateKey() { return null; }
            @Override public ECKeyLite getCurrentPrivateKey() { return null; }
            @Override public ECKeyLite getPreviousPrivateKey() { return null; }
            @Override public ECKeyLite getEncryptKey() { return null; }
            @Override public ECKeyLite getOrdPrivateKey() { return null; }
            @Override public ECKeyLite getPayAccountKey() { return null; }
            @Override public PublicKey getFriendPublicKey() { return null; }
            @Override public String getIdentityKey() { return null; }
            @Override public String getAppName() { return null; }
        };
    }

    private static BapBaseCore fixedKeys() {
        return new BapBaseCore() {
            @Override public ECKeyLite getRootPrivateKey() { return key(2); }
            @Override public ECKeyLite getCurrentPrivateKey() { return key(3); }
            @Override public ECKeyLite getPreviousPrivateKey() { return key(4); }
            @Override public ECKeyLite getEncryptKey() { return key(7); }
            @Override public ECKeyLite getOrdPrivateKey() { return key(5); }
            @Override public ECKeyLite getPayAccountKey() { return key(6); }
            @Override public PublicKey getFriendPublicKey() { return null; }
            @Override public String getIdentityKey() { return null; }
            @Override public String getAppName() { return null; }
        };
    }

    private static ECKeyLite key(long value) {
        return ECKeyLite.fromPrivate(BigInteger.valueOf(value));
    }
}
