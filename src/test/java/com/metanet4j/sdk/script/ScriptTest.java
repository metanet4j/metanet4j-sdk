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
}
