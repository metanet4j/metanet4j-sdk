package com.metanet4j.sdk.address;

import com.metanet4j.sdk.PrivateKey;
import com.metanet4j.sdk.PublicKey;
import io.bitcoinsv.bitcoinjsv.core.*;
import io.bitcoinsv.bitcoinjsv.exception.AddressFormatException;
import io.bitcoinsv.bitcoinjsv.exception.WrongNetworkException;
import io.bitcoinsv.bitcoinjsv.params.MainNetParams;
import io.bitcoinsv.bitcoinjsv.params.NetworkParameters;


public class AddressEnhance extends AddressLite {

    public AddressEnhance(Addressable address) {
        super(address);
    }

    public AddressEnhance(NetworkParameters params, byte[] hash160) {
        super(params, hash160);
    }

    public AddressEnhance(byte[] hash160) {
        super(MainNetParams.get(), hash160);
    }

    public AddressEnhance(NetworkParameters params, int version, byte[] hash160) throws WrongNetworkException {
        super(params, version, hash160);
    }

    public static AddressEnhance fromPubKeyHash(byte[] hash160) throws AddressFormatException {
        return fromPubKeyHash(MainNetParams.get(), hash160);
    }

    public static AddressEnhance fromPubKeyHash(NetworkParameters params, byte[] hash160) throws AddressFormatException {
        return new AddressEnhance(params,params.getAddressHeader(), hash160);
    }

    public static AddressEnhance fromPrivateKey(PrivateKey privateKey) {
        return fromPublicKey(MainNetParams.get(),privateKey.getPublicKey());
    }

    public static AddressEnhance fromPublicKey(PublicKey key) {
        return fromPublicKey(MainNetParams.get(), key);
    }

    public static AddressEnhance fromPublicKey(NetworkParameters params, PublicKey key) {
        return fromPubKeyHash(params, key.getPubKeyHash());
    }

    public static AddressEnhance fromECkeyLite(ECKeyLite ecKeyLite) {
        return fromPubKeyHash(MainNetParams.get(), ecKeyLite.getPubKeyHash());
    }

    public static AddressEnhance fromECkey(ECKey ecKey) {
        return fromPubKeyHash(MainNetParams.get(), ecKey.getPubKeyHash());
    }

    public Address transformAddress() {
        return new Address(this.getParams(), this.getVersion(), this.getHash160());
    }

}
