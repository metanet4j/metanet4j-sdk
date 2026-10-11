package com.metanet4j.sdk.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.hutool.core.codec.Base64;
import com.metanet4j.sdk.PublicKey;
import com.metanet4j.sdk.PrivateKey;
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
        PrivateKey recipient = PrivateKey.of(BigInteger.valueOf(3));
        PrivateKey ephemeral = PrivateKey.of(BigInteger.TWO);
        byte[] plain = "fixed message".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = Ecies.encrypt(plain, recipient.getPublicKey().getPublicKey(), ephemeral.getKey());
        assertArrayEquals("BIE1".getBytes(StandardCharsets.US_ASCII), Arrays.copyOf(encrypted, 4));
        assertArrayEquals(ephemeral.getPubKey(), Arrays.copyOfRange(encrypted, 4, 37));
        assertArrayEquals(plain, Ecies.decrypt(encrypted, recipient.getKey(), null));
        assertArrayEquals(plain,
                Ecies.decrypt(encrypted, recipient.getKey(), PublicKey.fromBytes(ephemeral.getPubKey()).getPublicKey()));

        String encoded = Ecies.encrypt("fixed message", recipient.getPublicKey().getPublicKey(), ephemeral.getKey());
        assertArrayEquals(encrypted, Base64.decode(encoded));
        assertArrayEquals(MessageDigest.getInstance("SHA-512").digest(plain),
                DigestUtilExtend.sha512(plain));
    }

    @Test
    void roundTripsTheBase64StringOverloads() throws Exception {
        // 覆盖 §82 清单中的 Ecies.decrypt(String, PrivateKey) —— **两参的 Base64 字符串重载**。
        // 既有用例用的是三参的 byte[] 重载（decrypt(byte[], PrivateKey, PublicKey)），
        // 故该重载虽在真实流程中被使用、却一直未被覆盖（§81.2 所述"有调用点却未覆盖"的典型）。
        PrivateKey recipient = PrivateKey.of(BigInteger.valueOf(5));
        PrivateKey ephemeral = PrivateKey.of(BigInteger.valueOf(7));
        String plain = "base64 round trip";

        String encoded = Ecies.encrypt(plain, recipient.getPublicKey().getPublicKey(), ephemeral.getKey());
        assertEquals(plain, Ecies.decrypt(encoded, recipient.getKey()),
                "字符串重载的 encrypt 与 decrypt 应可往返");

        // 与 byte[] 重载产出同一密文（两者只是编解码包装，不应产生差异）
        assertArrayEquals(Base64.decode(encoded),
                Ecies.encrypt(plain.getBytes(StandardCharsets.UTF_8),
                        recipient.getPublicKey().getPublicKey(), ephemeral.getKey()),
                "字符串与字节重载应产出相同密文");
    }

    @Test
    void randomEphemeralKeyAndTamperingAreHandled() throws Exception {
        PrivateKey recipient = PrivateKey.of(BigInteger.valueOf(3));
        byte[] plain = "random message".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = Ecies.encrypt(plain, recipient.getPublicKey().getPublicKey(), null);
        assertArrayEquals(plain, Ecies.decrypt(encrypted, recipient.getKey(), null));

        byte[] badMagic = encrypted.clone();
        badMagic[0] ^= 1;
        assertTrue(assertThrows(RuntimeException.class,
                () -> Ecies.decrypt(badMagic, recipient.getKey(), null)).getMessage().contains("magic"));

        byte[] badMac = encrypted.clone();
        badMac[badMac.length - 1] ^= 1;
        assertTrue(assertThrows(RuntimeException.class,
                () -> Ecies.decrypt(badMac, recipient.getKey(), null)).getMessage().contains("mac"));
    }
}
