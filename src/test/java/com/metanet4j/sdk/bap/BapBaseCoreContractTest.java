package com.metanet4j.sdk.bap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.metanet4j.sdk.PrivateKey;
import com.metanet4j.sdk.PublicKey;
import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.crypto.Ecies;
import com.metanet4j.sdk.remote.RemoteSignType;
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
        assertArrayEquals(plain, Ecies.decrypt(bap.encrypt(plain), bap.getEncryptKey().getKey(), null));
        assertEquals("encrypted payload", bap.decrypt(bap.encrypt("encrypted payload")));
    }

    @Test
    void defaultAddressesUseTheirOwnPrivateKeys() {
        BapBaseCore core = fixedKeys();
        assertEquals(AddressEnhance.fromECkeyLite(key(2)).toBase58(), core.getRootAddress());
        assertEquals(AddressEnhance.fromECkeyLite(key(3)), core.getCurrentAddress());
        assertEquals(AddressEnhance.fromECkeyLite(key(4)), core.getPreviouAddress());
        assertEquals(AddressEnhance.fromPrivateKey(key(5)), core.getOrdAddress());
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
            @Override public PrivateKey getRootPrivateKey() { return null; }
            @Override public PrivateKey getCurrentPrivateKey() { return null; }
            @Override public PrivateKey getPreviousPrivateKey() { return null; }
            @Override public PrivateKey getEncryptKey() { return null; }
            @Override public PrivateKey getOrdPrivateKey() { return null; }
            @Override public PrivateKey getPayAccountKey() { return null; }
            @Override public PublicKey getFriendPublicKey() { return null; }
            @Override public String getIdentityKey() { return null; }
            @Override public String getAppName() { return null; }
        };
    }

    private static BapBaseCore fixedKeys() {
        return new BapBaseCore() {
            @Override public PrivateKey getRootPrivateKey() { return key(2); }
            @Override public PrivateKey getCurrentPrivateKey() { return key(3); }
            @Override public PrivateKey getPreviousPrivateKey() { return key(4); }
            @Override public PrivateKey getEncryptKey() { return key(7); }
            @Override public PrivateKey getOrdPrivateKey() { return key(5); }
            @Override public PrivateKey getPayAccountKey() { return key(6); }
            @Override public PublicKey getFriendPublicKey() { return null; }
            @Override public String getIdentityKey() { return null; }
            @Override public String getAppName() { return null; }
        };
    }

    /** 由标量构造业务私钥（旧 ECKeyLite.fromPrivate 的等价入口）。 */
    private static PrivateKey key(long value) {
        return PrivateKey.of(BigInteger.valueOf(value));
    }
}
