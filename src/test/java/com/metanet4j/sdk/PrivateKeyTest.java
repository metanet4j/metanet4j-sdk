package com.metanet4j.sdk;

import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.exception.InvalidKeyException;
import com.metanet4j.base.bsv.BsvNetwork;
import org.junit.jupiter.api.Test;

public class PrivateKeyTest {
    @Test
    public void test(){
        try {
            PrivateKey privateKey = PrivateKey.fromWIF("L3bX9g5pS1R9arHKEEzCNMYUn9xy8AQMk3hf5kQz8o3mAcLd5oqR");
            System.out.println(privateKey.toWif(BsvNetwork.MAINNET));
            String hex = privateKey.getPublicKey().getPubKeyHex();
            System.out.println(hex);
            AddressEnhance address = AddressEnhance.fromPublicKey(BsvNetwork.MAINNET, privateKey.getPublicKey());
            System.out.println(address.toBase58());
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
    }
}
