package com.metanet4j.sdk.bap;

import com.metanet4j.sdk.PrivateKey;

import cn.hutool.core.util.HexUtil;
import com.metanet4j.sdk.PublicKey;
import com.metanet4j.sdk.address.AddressEnhance;
import io.bitcoinsv.bitcoinjsv.core.ECKey;
import io.bitcoinsv.bitcoinjsv.core.Utils;
import io.bitcoinsv.bitcoinjsv.temp.KeyBag;
import io.bitcoinsv.bitcoinjsv.temp.RedeemData;

import org.jspecify.annotations.Nullable;

/**
 * Specify a private key for signing and payments.
 */
public class SpecifyBapBase extends BapBaseAbstract {

    private PrivateKey rootPrivateKey;
    private PrivateKey signPrivateKey;
    private PrivateKey previousPrivateKey;

    private PrivateKey encryptPrivateKey;
    private PrivateKey payAccountKey;
    private PrivateKey ordPrivateKey;
    private PublicKey friendPublicKey;


    public SpecifyBapBase(PrivateKey signPrivateKey, PrivateKey rootPrivateKey, String identityKey, String appName) {
        this.identityKey = identityKey;
        this.appName = appName;
        this.signPrivateKey = signPrivateKey;
        this.rootPrivateKey = rootPrivateKey;

    }

    public SpecifyBapBase(PrivateKey signPrivateKey, PrivateKey rootPrivateKey, String identityKey, String appName, PrivateKey previousPrivateKey) {
        this(signPrivateKey, rootPrivateKey, identityKey, appName);
        this.previousPrivateKey = previousPrivateKey;
    }

    public SpecifyBapBase(PrivateKey signPrivateKey, PrivateKey encryptPrivateKey, PrivateKey rootPrivateKey, String identityKey, String appName) {
        this(signPrivateKey, rootPrivateKey, identityKey, appName);
        this.encryptPrivateKey = encryptPrivateKey;
    }

    public SpecifyBapBase(PrivateKey signPrivateKey, PrivateKey payAccountKey, PrivateKey encryptPrivateKey, PrivateKey rootPrivateKey, String identityKey, String appName) {
        this(signPrivateKey, encryptPrivateKey, rootPrivateKey, identityKey, appName);
        this.payAccountKey = payAccountKey;
    }

    public SpecifyBapBase(PrivateKey signPrivateKey, PrivateKey payAccountKey, PrivateKey encryptPrivateKey, PrivateKey rootPrivateKey, String identityKey, PrivateKey ordPrivateKey, String appName) {
        this(signPrivateKey, payAccountKey, encryptPrivateKey, rootPrivateKey, identityKey, appName);
        this.ordPrivateKey = ordPrivateKey;
    }

    public SpecifyBapBase(PrivateKey signPrivateKey, PrivateKey payAccountKey, PrivateKey encryptPrivateKey, PrivateKey rootPrivateKey, String identityKey, PrivateKey ordPrivateKey, PublicKey friendPublicKey,
                          String appName) {
        this(signPrivateKey, payAccountKey, encryptPrivateKey, rootPrivateKey, identityKey, ordPrivateKey, appName);
        this.friendPublicKey = friendPublicKey;
    }

    @Override
    public PrivateKey getRootPrivateKey() {
        return this.rootPrivateKey;
    }

    @Override
    public PrivateKey getPreviousPrivateKey() {
        return this.previousPrivateKey;
    }

    @Override
    public PrivateKey getCurrentPrivateKey() {
        return this.signPrivateKey;
    }

    @Override
    public PrivateKey getEncryptKey() {

        return this.encryptPrivateKey;
    }

    @Override
    public PrivateKey getPayAccountKey() {
        return this.payAccountKey;
    }

    @Override
    public PrivateKey getOrdPrivateKey() {
        return this.ordPrivateKey;
    }

    @Override
    public PublicKey getFriendPublicKey() {
        return this.friendPublicKey;
    }


    public static class SpecifyProviderKeyBag implements SigningKeyProvider {

        private SpecifyBapBase specifyBapBase;

        public SpecifyProviderKeyBag(SpecifyBapBase specifyBapBase) {
            this.specifyBapBase = specifyBapBase;
        }

        @Nullable
        @Override
        public PrivateKey findKeyFromPubHash(byte[] pubkeyHash) {

            //find payAccountKey
            if (AddressEnhance.fromPubKeyHash(pubkeyHash).toBase58()
                    .equals(AddressEnhance.fromPublicKey(this.specifyBapBase.getPayAccountKey().getPublicKey()).toBase58())) {

                return this.specifyBapBase.getPayAccountKey();
            }
            //find ordKey
            if (AddressEnhance.fromPubKeyHash(pubkeyHash).toBase58()
                    .equals(AddressEnhance.fromPublicKey(this.specifyBapBase.getOrdPrivateKey().getPublicKey()).toBase58())) {

                return this.specifyBapBase.getOrdPrivateKey();
            }
            throw new RuntimeException("not find ecKey by pubkeyHash:" + HexUtil.encodeHexStr(pubkeyHash) + ",address is :" + AddressEnhance.fromPubKeyHash(pubkeyHash).toBase58());

        }

        @Nullable
        @Override
        public PrivateKey findKeyFromPubKey(byte[] pubkey) {
            return SigningKeyProvider.super.findKeyFromPubKey(pubkey);
        }
    }
}
