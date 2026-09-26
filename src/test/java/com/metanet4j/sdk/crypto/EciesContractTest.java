package com.metanet4j.sdk.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.hutool.core.codec.Base64;
import io.bitcoinsv.bitcoinjsv.core.ECKeyLite;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class EciesContractTest {

    @Test
    void aesCbcMatchesThePlatformPkcs5Cipher() throws Exception {
        byte[] key = new byte[16];
        byte[] iv = new byte[16];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) i;
            iv[i] = (byte) (i + 16);
        }
        byte[] plain = "sdk encryption".getBytes(StandardCharsets.UTF_8);
        Cipher reference = Cipher.getInstance("AES/CBC/PKCS5Padding");
        reference.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        byte[] expected = reference.doFinal(plain);
        assertArrayEquals(expected, AesCBCUtil.aesEncrypt(plain, key, iv));
        assertArrayEquals(plain, AesCBCUtil.aesDecrypt(expected, key, iv));
        assertThrows(Exception.class, () -> AesCBCUtil.aesEncrypt(plain, new byte[5], iv));
    }

    @Test
    void explicitEphemeralKeyProducesAuthenticatedRoundTrip() throws Exception {
        ECKeyLite recipient = ECKeyLite.fromPrivate(BigInteger.valueOf(3));
        ECKeyLite ephemeral = ECKeyLite.fromPrivate(BigInteger.TWO);
        byte[] plain = "fixed message".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = Ecies.encrypt(plain, recipient, ephemeral);
        assertArrayEquals("BIE1".getBytes(StandardCharsets.US_ASCII), Arrays.copyOf(encrypted, 4));
        assertArrayEquals(ephemeral.getPubKey(), Arrays.copyOfRange(encrypted, 4, 37));
        assertArrayEquals(plain, Ecies.decrypt(encrypted, recipient, null));
        assertArrayEquals(plain,
                Ecies.decrypt(encrypted, recipient, ECKeyLite.fromPublicOnly(ephemeral.getPubKey())));

        String encoded = Ecies.encrypt("fixed message", recipient, ephemeral);
        assertArrayEquals(encrypted, Base64.decode(encoded));
        assertArrayEquals(MessageDigest.getInstance("SHA-512").digest(plain),
                DigestUtilExtend.sha512(plain));
    }

    @Test
    void randomEphemeralKeyAndTamperingAreHandled() throws Exception {
        ECKeyLite recipient = ECKeyLite.fromPrivate(BigInteger.valueOf(3));
        byte[] plain = "random message".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = Ecies.encrypt(plain, recipient, null);
        assertArrayEquals(plain, Ecies.decrypt(encrypted, recipient, null));

        byte[] badMagic = encrypted.clone();
        badMagic[0] ^= 1;
        assertTrue(assertThrows(RuntimeException.class,
                () -> Ecies.decrypt(badMagic, recipient, null)).getMessage().contains("magic"));

        byte[] badMac = encrypted.clone();
        badMac[badMac.length - 1] ^= 1;
        assertTrue(assertThrows(RuntimeException.class,
                () -> Ecies.decrypt(badMac, recipient, null)).getMessage().contains("mac"));
    }
}
