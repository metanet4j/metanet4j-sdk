package com.metanet4j.sdk.utils;

import cn.hutool.core.util.HexUtil;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class UtilsExtendTest {

    @Test
    public void testHash() {
        String test = "test send oridinals 1007-1 from metanet-sdk";
        String hash = UtilsExtend.toHash(test);
        Assertions.assertEquals("5658e532fe8c11e034b504fcbe115b6d706b905c7722eac7b1b4258d0368f726", hash);

        String hash2 = HexUtil.encodeHexStr(UtilsExtend.toHashByte(test));
        Assertions.assertEquals("5658e532fe8c11e034b504fcbe115b6d706b905c7722eac7b1b4258d0368f726", hash2);

    }

    @Test
    void hashesBytesAndExplicitCharacterEncodings() {
        byte[] latin1 = {(byte) 0xe9};
        String expected = "de2e331d891ae267a7009cb45b4e8830f170e0c937288ea2731a1941c7a53b0d";
        Assertions.assertEquals(expected, UtilsExtend.toHash(latin1));
        Assertions.assertEquals(expected, UtilsExtend.toHash("é", StandardCharsets.ISO_8859_1));
        Assertions.assertEquals(expected, HexUtil.encodeHexStr(UtilsExtend.toHashByte(latin1)));
        Assertions.assertEquals(expected,
                HexUtil.encodeHexStr(UtilsExtend.toHashByte("é", StandardCharsets.ISO_8859_1)));
        Assertions.assertEquals("4a99557e4033c3539de2eb65472017cad5f9557f7a0625a09f1c3f6e2ba69c4c",
                UtilsExtend.toHash("é"));
    }

    @Test
    void formatsBitcoinSignedMessageAtVarIntBoundary() {
        byte[] empty = UtilsExtend.formatMessageForSigning(new byte[0]);
        Assertions.assertEquals("18426974636f696e205369676e6564204d6573736167653a0a00",
                HexUtil.encodeHexStr(empty));

        byte[] message = new byte[253];
        Arrays.fill(message, (byte) 0x5a);
        byte[] formatted = UtilsExtend.formatMessageForSigning(message);
        Assertions.assertEquals(1 + 24 + 3 + 253, formatted.length);
        Assertions.assertArrayEquals(new byte[] {(byte) 0xfd, (byte) 0xfd, 0},
                Arrays.copyOfRange(formatted, 25, 28));
        Assertions.assertArrayEquals(message, Arrays.copyOfRange(formatted, 28, formatted.length));
    }
}
