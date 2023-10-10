package com.metanet4j.sdk;

import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.exception.InvalidKeyException;
import io.bitcoinsv.bitcoinjsv.params.MainNetParams;
import io.bitcoinsv.bitcoinjsv.params.RegTestParams;
import org.junit.Test;

public class PrivateKeyTest {
    @Test
    public void test(){
        try {
            PrivateKey privateKey = PrivateKey.fromWIF("L3bX9g5pS1R9arHKEEzCNMYUn9xy8AQMk3hf5kQz8o3mAcLd5oqR");
            System.out.println(privateKey.toWif(MainNetParams.get()));
            String hex = privateKey.getPublicKey().getPubKeyHex();
            System.out.println(hex);
            AddressEnhance address = AddressEnhance.fromPublicKey(MainNetParams.get(), privateKey.getPublicKey());
            System.out.println(address.toBase58());
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
    }
}
