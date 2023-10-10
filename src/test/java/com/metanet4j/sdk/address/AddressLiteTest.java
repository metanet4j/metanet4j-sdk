package com.metanet4j.sdk.address;

import io.bitcoinsv.bitcoinjsv.core.AddressLite;
import io.bitcoinsv.bitcoinjsv.params.RegTestParams;
import org.junit.Test;

public class AddressLiteTest {

    @Test
    public void testBase58(){
        AddressLite address = AddressEnhance.fromBase58(RegTestParams.get(), "mqACwWBepT6dPqrs8vqxE2sBGKjXBSv9Tt");
        System.out.println(address.toBase58());
    }
}
