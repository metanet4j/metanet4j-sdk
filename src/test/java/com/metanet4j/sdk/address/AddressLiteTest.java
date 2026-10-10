package com.metanet4j.sdk.address;

import com.metanet4j.base.bsv.BsvNetwork;
import org.junit.jupiter.api.Test;

public class AddressLiteTest {

    @Test
    public void testBase58(){
        AddressEnhance address = AddressEnhance.fromBase58(BsvNetwork.REGTEST, "mqACwWBepT6dPqrs8vqxE2sBGKjXBSv9Tt");
        System.out.println(address.toBase58());
    }


}
