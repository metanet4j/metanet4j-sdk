package com.metanet4j.sdk.bap;

import com.metanet4j.sdk.PrivateKey;

import com.metanet4j.sdk.PublicKey;

public abstract class BapBaseAbstract implements BapBaseCore {

    protected String identityKey;
    protected String appName;


    @Override
    public PrivateKey getRootPrivateKey() {
        return null;
    }

    @Override
    public PrivateKey getCurrentPrivateKey() {
        return null;
    }

    @Override
    public PrivateKey getPreviousPrivateKey() {
        return null;
    }

    @Override
    public PrivateKey getEncryptKey() {
        return null;
    }

    @Override
    public PrivateKey getOrdPrivateKey() {
        return null;
    }

    @Override
    public PrivateKey getPayAccountKey() {
        return null;
    }

    @Override
    public PublicKey getFriendPublicKey() {
        return null;
    }


    @Override
    public String getIdentityKey() {
        return this.identityKey;
    }

    @Override
    public String getAppName() {
        return this.appName;
    }
}
