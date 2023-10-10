package com.metanet4j.sdk.bap;

import cn.hutool.core.codec.Base64;
import com.metanet4j.sdk.PublicKey;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.crypto.Ecies;
import io.bitcoinsv.bitcoinjsv.core.Address;
import io.bitcoinsv.bitcoinjsv.core.ECKeyLite;
import io.bitcoinsv.bitcoinjsv.params.MainNetParams;

import java.nio.charset.Charset;

public interface BapBaseCore {

    /**
     * Get the root private key for signing.
     *
     * @return
     */
    ECKeyLite getRootPrivateKey();

    /**
     * Get the current private key for signing.
     *
     * @return
     */
    ECKeyLite getCurrentPrivateKey();

    /**
     * Get the previous private key for signing, often used for signing the "new ID" operation in the BAP protocol.
     *
     * @return
     */
    ECKeyLite getPreviousPrivateKey();

    /**
     * Get the encryption private key for encryption
     *
     * @return
     */
    ECKeyLite getEncryptKey();

    /**
     * Get the ordinals private key for sending ordinals UTXO.
     *
     * @return
     */
    ECKeyLite getOrdPrivateKey();

    /**
     * Get the payment account private key for making payments
     *
     * @return
     */
    ECKeyLite getPayAccountKey();

    /**
     * Get the public key of a friend for adding as a friend.
     *
     * @return
     */
    PublicKey getFriendPublicKey();


    String getIdentityKey();

    String getAppName();

    default String getRootAddress() {
        return AddressEnhance.fromECkeyLite(getRootPrivateKey()).toBase58();
    }


    default AddressEnhance getPreviouAddress() {
        return AddressEnhance.fromECkeyLite(getPreviousPrivateKey());
    }

    default AddressEnhance getCurrentAddress() {
        return AddressEnhance.fromECkeyLite(getCurrentPrivateKey());
    }

    default Address getOrdAddress() {
        return new Address(MainNetParams.get(), getOrdPrivateKey().getPubKeyHash());
    }

    default byte[] encrypt(byte[] bytes) {
        try {
            return Ecies.encrypt(bytes, ECKeyLite.fromPublicOnly(getEncryptKey().getPubKey()), null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    default String encrypt(String plainText) {
        try {
            return Ecies.encrypt(plainText, ECKeyLite.fromPublicOnly(getEncryptKey().getPubKey()), null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    default String decrypt(String encryptedPlainText) {
        try {
            return new String(Ecies.decrypt(Base64.decode(encryptedPlainText),
                    ECKeyLite.fromPrivate(getEncryptKey().getPrivKeyBytes()),
                    null), Charset.forName("UTF-8"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
