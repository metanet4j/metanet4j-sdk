package com.metanet4j.sdk.bap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.metanet4j.sdk.PrivateKey;
import com.metanet4j.sdk.PublicKey;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class SpecifyBapBaseContractTest {

    @Test
    void constructorOverloadsKeepTheSelectedKeysAndIdentity() {
        PrivateKey sign = key(2);
        PrivateKey root = key(3);
        PrivateKey previous = key(4);
        PrivateKey encryption = key(5);
        PrivateKey payment = key(6);
        PrivateKey ord = key(7);
        PublicKey friend = key(8).getPublicKey();

        SpecifyBapBase basic = new SpecifyBapBase(sign, root, "identity", "app");
        assertEquals(sign, basic.getCurrentPrivateKey());
        assertEquals(root, basic.getRootPrivateKey());
        assertEquals("identity", basic.getIdentityKey());
        assertEquals("app", basic.getAppName());
        assertNull(basic.getPreviousPrivateKey());
        assertNull(basic.getEncryptKey());
        assertNull(basic.getPayAccountKey());
        assertNull(basic.getOrdPrivateKey());
        assertNull(basic.getFriendPublicKey());

        SpecifyBapBase withPrevious = new SpecifyBapBase(sign, root, "identity", "app", previous);
        assertEquals(previous, withPrevious.getPreviousPrivateKey());
        SpecifyBapBase withEncryption = new SpecifyBapBase(sign, encryption, root, "identity", "app");
        assertEquals(encryption, withEncryption.getEncryptKey());
        SpecifyBapBase withPayment = new SpecifyBapBase(sign, payment, encryption, root, "identity", "app");
        assertEquals(payment, withPayment.getPayAccountKey());
        SpecifyBapBase withOrd = new SpecifyBapBase(sign, payment, encryption, root, "identity", ord, "app");
        assertEquals(ord, withOrd.getOrdPrivateKey());
        SpecifyBapBase withFriend = new SpecifyBapBase(sign, payment, encryption, root,
                "identity", ord, friend, "app");
        assertEquals(friend, withFriend.getFriendPublicKey());
    }

    @Test
    void keyBagFindsPaymentAndOrdByHashOrPublicKey() {
        PrivateKey payment = key(6);
        PrivateKey ord = key(7);
        SpecifyBapBase identity = new SpecifyBapBase(key(2), payment, key(5), key(3),
                "identity", ord, "app");
        SpecifyBapBase.SpecifyProviderKeyBag bag = new SpecifyBapBase.SpecifyProviderKeyBag(identity);

        assertEquals(payment.getPrivKey(), bag.findKeyFromPubHash(payment.getPublicKey().getPubKeyHash()).getPrivKey());
        assertEquals(ord.getPrivKey(), bag.findKeyFromPubHash(ord.getPublicKey().getPubKeyHash()).getPrivKey());
        assertEquals(payment.getPrivKey(), bag.findKeyFromPubKey(payment.getPubKey()).getPrivKey());
        assertThrows(RuntimeException.class, () -> bag.findKeyFromPubHash(new byte[20]));
    }

    /** 由标量构造新 SDK 私钥（旧 ECKeyLite.fromPrivate 的等价入口）。 */
    private static PrivateKey key(long value) {
        return PrivateKey.of(BigInteger.valueOf(value));
    }
}
