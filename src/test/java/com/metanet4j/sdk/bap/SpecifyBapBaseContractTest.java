package com.metanet4j.sdk.bap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.metanet4j.sdk.PrivateKey;
import com.metanet4j.sdk.PublicKey;
import io.bitcoinsv.bitcoinjsv.core.ECKeyLite;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class SpecifyBapBaseContractTest {

    @Test
    void constructorOverloadsKeepTheSelectedKeysAndIdentity() {
        ECKeyLite sign = key(2);
        ECKeyLite root = key(3);
        ECKeyLite previous = key(4);
        ECKeyLite encryption = key(5);
        ECKeyLite payment = key(6);
        ECKeyLite ord = key(7);
        PublicKey friend = new PrivateKey(key(8)).getPublicKey();

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
        ECKeyLite payment = key(6);
        ECKeyLite ord = key(7);
        SpecifyBapBase identity = new SpecifyBapBase(key(2), payment, key(5), key(3),
                "identity", ord, "app");
        SpecifyBapBase.SpecifyProviderKeyBag bag = new SpecifyBapBase.SpecifyProviderKeyBag(identity);

        assertEquals(payment.getPrivKey(), bag.findKeyFromPubHash(payment.getPubKeyHash()).getPrivKey());
        assertEquals(ord.getPrivKey(), bag.findKeyFromPubHash(ord.getPubKeyHash()).getPrivKey());
        assertEquals(payment.getPrivKey(), bag.findKeyFromPubKey(payment.getPubKey()).getPrivKey());
        assertThrows(RuntimeException.class, () -> bag.findKeyFromPubHash(new byte[20]));
        assertNull(bag.findRedeemDataFromScriptHash(new byte[20]));
    }

    private static ECKeyLite key(long value) {
        return ECKeyLite.fromPrivate(BigInteger.valueOf(value));
    }
}
