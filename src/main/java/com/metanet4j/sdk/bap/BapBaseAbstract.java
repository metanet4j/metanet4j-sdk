package com.metanet4j.sdk.bap;

import com.metanet4j.sdk.PublicKey;
import io.bitcoinsv.bitcoinjsv.core.ECKeyLite;

public abstract class BapBaseAbstract implements BapBaseCore {

    protected String identityKey;
    protected String appName;


    @Override
    public ECKeyLite getRootPrivateKey() {
        return null;
    }

    @Override
    public ECKeyLite getCurrentPrivateKey() {
        return null;
    }

    @Override
    public ECKeyLite getPreviousPrivateKey() {
        return null;
    }

    @Override
    public ECKeyLite getEncryptKey() {
        return null;
    }

    @Override
    public ECKeyLite getOrdPrivateKey() {
        return null;
    }

    @Override
    public ECKeyLite getPayAccountKey() {
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
