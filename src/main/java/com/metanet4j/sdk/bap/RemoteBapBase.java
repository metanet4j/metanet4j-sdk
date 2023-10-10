package com.metanet4j.sdk.bap;

import com.metanet4j.sdk.address.AddressEnhance;
import io.bitcoinsv.bitcoinjsv.core.Address;

/**
 * Necessary information for remote signing.
 */
public class RemoteBapBase extends BapBaseAbstract {

    private String rootAddress;
    private AddressEnhance previouAddress;
    private AddressEnhance currentAddress;
    private Address ordAddress;

    public RemoteBapBase(String identityKey, String rootAddress, String appName) {
        this(identityKey, rootAddress, null, null, null, appName);
    }

    public RemoteBapBase(String identityKey, String rootAddress, AddressEnhance currentAddress, String appName) {
        this(identityKey, rootAddress, null, currentAddress, null, appName);
    }

    public RemoteBapBase(String identityKey, String rootAddress, AddressEnhance previouAddress, AddressEnhance currentAddress, String appName) {
        this(identityKey, rootAddress, previouAddress, currentAddress, null, appName);
    }

    public RemoteBapBase(String identityKey, String rootAddress, AddressEnhance previouAddress, AddressEnhance currentAddress, Address ordAddress, String appName) {
        this.identityKey = identityKey;
        this.rootAddress = rootAddress;
        this.previouAddress = previouAddress;
        this.currentAddress = currentAddress;
        this.ordAddress = ordAddress;
        this.appName = appName;
    }


    @Override
    public String getIdentityKey() {
        return this.identityKey;
    }

    @Override
    public String getAppName() {
        return this.appName;
    }

    @Override
    public String getRootAddress() {
        return this.rootAddress;
    }

    @Override
    public AddressEnhance getPreviouAddress() {

        return this.previouAddress;
    }

    @Override
    public AddressEnhance getCurrentAddress() {
        return this.currentAddress;
    }

    @Override
    public Address getOrdAddress() {
        return this.ordAddress;
    }
}
