package com.metanet4j.sdk.bap;

import cn.hutool.core.util.HexUtil;
import com.metanet4j.sdk.PublicKey;
import com.metanet4j.sdk.address.AddressEnhance;
import io.bitcoinsv.bitcoinjsv.core.ECKey;
import io.bitcoinsv.bitcoinjsv.core.ECKeyLite;
import io.bitcoinsv.bitcoinjsv.temp.KeyBag;
import io.bitcoinsv.bitcoinjsv.temp.RedeemData;

import javax.annotation.Nullable;

/**
 * Specify a private key for signing and payments.
 */
public class SpecifyBapBase extends BapBaseAbstract {

    private ECKeyLite rootPrivateKey;
    private ECKeyLite signPrivateKey;
    private ECKeyLite previousPrivateKey;

    private ECKeyLite encryptPrivateKey;
    private ECKeyLite payAccountKey;
    private ECKeyLite ordPrivateKey;
    private PublicKey friendPublicKey;


    public SpecifyBapBase(ECKeyLite signPrivateKey, ECKeyLite rootPrivateKey, String identityKey, String appName) {
        this.identityKey = identityKey;
        this.appName = appName;
        this.signPrivateKey = signPrivateKey;
        this.rootPrivateKey = rootPrivateKey;

    }

    public SpecifyBapBase(ECKeyLite signPrivateKey, ECKeyLite rootPrivateKey, String identityKey, String appName, ECKeyLite previousPrivateKey) {
        this(signPrivateKey, rootPrivateKey, identityKey, appName);
        this.previousPrivateKey = previousPrivateKey;
    }

    public SpecifyBapBase(ECKeyLite signPrivateKey, ECKeyLite encryptPrivateKey, ECKeyLite rootPrivateKey, String identityKey, String appName) {
        this(signPrivateKey, rootPrivateKey, identityKey, appName);
        this.encryptPrivateKey = encryptPrivateKey;
    }

    public SpecifyBapBase(ECKeyLite signPrivateKey, ECKeyLite payAccountKey, ECKeyLite encryptPrivateKey, ECKeyLite rootPrivateKey, String identityKey, String appName) {
        this(signPrivateKey, encryptPrivateKey, rootPrivateKey, identityKey, appName);
        this.payAccountKey = payAccountKey;
    }

    public SpecifyBapBase(ECKeyLite signPrivateKey, ECKeyLite payAccountKey, ECKeyLite encryptPrivateKey, ECKeyLite rootPrivateKey, String identityKey, ECKeyLite ordPrivateKey, String appName) {
        this(signPrivateKey, payAccountKey, encryptPrivateKey, rootPrivateKey, identityKey, appName);
        this.ordPrivateKey = ordPrivateKey;
    }

    public SpecifyBapBase(ECKeyLite signPrivateKey, ECKeyLite payAccountKey, ECKeyLite encryptPrivateKey, ECKeyLite rootPrivateKey, String identityKey, ECKeyLite ordPrivateKey, PublicKey friendPublicKey,
                          String appName) {
        this(signPrivateKey, payAccountKey, encryptPrivateKey, rootPrivateKey, identityKey, ordPrivateKey, appName);
        this.friendPublicKey = friendPublicKey;
    }

    @Override
    public ECKeyLite getRootPrivateKey() {
        return this.rootPrivateKey;
    }

    @Override
    public ECKeyLite getPreviousPrivateKey() {
        return this.previousPrivateKey;
    }

    @Override
    public ECKeyLite getCurrentPrivateKey() {
        return this.signPrivateKey;
    }

    @Override
    public ECKeyLite getEncryptKey() {

        return this.encryptPrivateKey;
    }

    @Override
    public ECKeyLite getPayAccountKey() {
        return this.payAccountKey;
    }

    @Override
    public ECKeyLite getOrdPrivateKey() {
        return this.ordPrivateKey;
    }

    @Override
    public PublicKey getFriendPublicKey() {
        return this.friendPublicKey;
    }


    public static class SpecifyProviderKeyBag implements KeyBag {

        private SpecifyBapBase specifyBapBase;

        public SpecifyProviderKeyBag(SpecifyBapBase specifyBapBase) {
            this.specifyBapBase = specifyBapBase;
        }

        @Nullable
        @Override
        public ECKey findKeyFromPubHash(byte[] pubkeyHash) {

            //find payAccountKey
            if (AddressEnhance.fromPubKeyHash(pubkeyHash).toBase58()
                    .equals(AddressEnhance.fromPubKeyHash(this.specifyBapBase.getPayAccountKey().getPubKeyHash()).toBase58())) {

                return ECKey.fromPrivate(this.specifyBapBase.getPayAccountKey().getPrivKey());
            }
            //find ordKey
            if (AddressEnhance.fromPubKeyHash(pubkeyHash).toBase58()
                    .equals(AddressEnhance.fromPubKeyHash(this.specifyBapBase.getOrdPrivateKey().getPubKeyHash()).toBase58())) {

                return ECKey.fromPrivate(this.specifyBapBase.getOrdPrivateKey().getPrivKey());
            }
            throw new RuntimeException("not find ecKey by pubkeyHash:" + HexUtil.encodeHexStr(pubkeyHash) + ",address is :" + AddressEnhance.fromPubKeyHash(pubkeyHash).toBase58());

        }

        @Nullable
        @Override
        public ECKey findKeyFromPubKey(byte[] pubkey) {
            return null;
        }

        @Nullable
        @Override
        public RedeemData findRedeemDataFromScriptHash(byte[] scriptHash) {
            return null;
        }
    }
}
