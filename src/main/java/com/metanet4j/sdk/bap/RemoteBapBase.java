package com.metanet4j.sdk.bap;

import com.metanet4j.base.bap.BapHelper;
import com.metanet4j.sdk.address.AddressEnhance;
import io.bitcoinsv.bitcoinjsv.core.Address;

/**
 * Necessary information for remote signing.
 */
public class RemoteBapBase extends BapBaseAbstract {

    private String rootAddress;
    private AddressEnhance previouAddress;
    private AddressEnhance currentAddress;
    private AddressEnhance payAccountAddress;
    private Address ordAddress;

    public RemoteBapBase() {
    }

    public RemoteBapBase(String rootAddress, AddressEnhance previouAddress, AddressEnhance currentAddress, AddressEnhance payAccountAddress, Address ordAddress, String appName) {
        this.rootAddress = rootAddress;
        this.identityKey = BapHelper.getIdentityKey(this.rootAddress);
        this.previouAddress = previouAddress;
        this.currentAddress = currentAddress;
        this.payAccountAddress = payAccountAddress;
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

    @Override
    public AddressEnhance getPayAccountAddress() {
        return this.payAccountAddress;
    }
}
