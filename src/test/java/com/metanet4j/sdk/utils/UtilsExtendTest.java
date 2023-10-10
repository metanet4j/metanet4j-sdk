package com.metanet4j.sdk.utils;

import cn.hutool.core.util.HexUtil;
import org.junit.Assert;
import org.junit.Test;

public class UtilsExtendTest {

    @Test
    public void testHash() {
        String test = "test send oridinals 1007-1 from metanet-sdk";
        String hash = UtilsExtend.toHash(test);
        Assert.assertEquals("5658e532fe8c11e034b504fcbe115b6d706b905c7722eac7b1b4258d0368f726", hash);

        String hash2 = HexUtil.encodeHexStr(UtilsExtend.toHashByte(test));
        Assert.assertEquals("5658e532fe8c11e034b504fcbe115b6d706b905c7722eac7b1b4258d0368f726", hash2);

    }
}
