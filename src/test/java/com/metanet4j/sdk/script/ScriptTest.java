package com.metanet4j.sdk.script;

import com.metanet4j.sdk.utils.ScriptHelper;
import org.junit.Assert;
import org.junit.Test;

public class ScriptTest {


    @Test
    public void testGetAddressByOrdinalScript() {
        String b = "dqkU575zljkD4trd3dqsLSEgWZup6ruIrA==";
        String address = ScriptHelper.getAddressByOrdinalScript(b);
        System.out.println(address);
        Assert.assertEquals("1N8MF7iY6aeqpXpB6LB8ABynSNvVQKURsy", address);
    }

    @Test
    public void testGetAddressByOrdinalScrip2() {
        String b = "dqkU575zljkD4trd3dqsLSEgWZup6ruIrABjA29yZFENdGV4dC9tYXJrZG93bgAmdGVzdCBzZW5kIG9yaWRpbmFscyAzIGZyb20gbWV0YW5ldC1zZGtoaiIxUHVRYTdLNjJNaUtDdHNzU0xLeTFraDU2V1dVN010VVI1A1NFVANhcHALbWV0YW5ldC1zZGsEdHlwZQNvcmQBfAVTSUdNQQNCU00iMTdIZ1dUU3JXTk5qeFdzNDlGSjZ1dHhzZWR4Z3hydDhZYkEbBh0VczEnU/UH1WrgRVK21dJSty1+jnx9lvMSNl2G0sRA6LRdr6dHOWQe78QiwWrCJf5tMocDy2OpeEsA/wyXWAEw==";
        String address = ScriptHelper.getAddressByOrdinalScript(b);
        System.out.println(address);
        Assert.assertEquals("1N8MF7iY6aeqpXpB6LB8ABynSNvVQKURsy", address);
    }
}
