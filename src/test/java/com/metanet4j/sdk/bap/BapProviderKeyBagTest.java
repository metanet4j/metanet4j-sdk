package com.metanet4j.sdk.bap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.metanet4j.sdk.TestData;
import org.junit.jupiter.api.Test;

class BapProviderKeyBagTest {

    @Test
    void findsRootAndCurrentSignatureKeysByPublicKeyHash() {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        BapBase.BapProviderKeyBag bag = new BapBase.BapProviderKeyBag(TestData.masterPrivateKey, 2);

        assertEquals(bap.getRootPrivateKey().getKey().toHex(),
                bag.findKeyFromPubHash(bap.getRootPrivateKey().getPublicKey().getPubKeyHash()).getKey().toHex(), "新 SDK 私钥类型无 equals，比对 32 字节 hex");
        assertEquals(bap.getCurrentPrivateKey().getKey().toHex(),
                bag.findKeyFromPubHash(bap.getCurrentPrivateKey().getPublicKey().getPubKeyHash()).getKey().toHex(), "新 SDK 私钥类型无 equals，比对 32 字节 hex");
    }

    @Test
    void findsPaymentAndOrdKeysOutsideTheSignatureSearchRange() {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        BapBase.BapProviderKeyBag bag = new BapBase.BapProviderKeyBag(
                TestData.masterPrivateKey, 0, new DefaultBapBaseConfig());

        assertEquals(bap.getPayAccountKey().getKey().toHex(),
                bag.findKeyFromPubHash(bap.getPayAccountKey().getPublicKey().getPubKeyHash()).getKey().toHex(), "新 SDK 私钥类型无 equals，比对 32 字节 hex");
        assertEquals(bap.getOrdPrivateKey().getKey().toHex(),
                bag.findKeyFromPubHash(bap.getOrdPrivateKey().getPublicKey().getPubKeyHash()).getKey().toHex(), "新 SDK 私钥类型无 equals，比对 32 字节 hex");
        assertThrows(RuntimeException.class, () -> bag.findKeyFromPubHash(new byte[20]));
        assertThrows(IllegalArgumentException.class, () -> new BapBase.BapProviderKeyBag(TestData.masterPrivateKey, 1001));
    }

    @Test
    void bagBuiltFromIdentityResolvesPublicKeysButNotScriptHashes() {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        BapBase.BapProviderKeyBag bag = new BapBase.BapProviderKeyBag(bap);

        assertEquals(bap.getCurrentPrivateKey().getKey().toHex(),
                bag.findKeyFromPubHash(bap.getCurrentPrivateKey().getPublicKey().getPubKeyHash()).getKey().toHex(), "新 SDK 私钥类型无 equals，比对 32 字节 hex");
        assertEquals(bap.getCurrentPrivateKey().getKey().toHex(),
                bag.findKeyFromPubKey(bap.getCurrentPrivateKey().getPubKey()).getKey().toHex(), "新 SDK 私钥类型无 equals，比对 32 字节 hex");
    }
}
