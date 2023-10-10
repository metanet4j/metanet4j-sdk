package com.metanet4j.sdk.crypto;

import java.security.Security;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class AesCBCUtil {
    private static final String AES_CBC_PKCS7 = "AES/CBC/PKCS7Padding";

    /**
     * encrypt
     *
     * @param srcData
     * @param key
     * @param iv
     * @return
     * @throws Exception
     */
    public static byte[] aesEncrypt(byte[] srcData, byte[] key, byte[] iv) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        Security.addProvider(new BouncyCastleProvider());
        Cipher cipher = Cipher.getInstance(AES_CBC_PKCS7, "BC");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(iv));
        byte[] encrypt = cipher.doFinal(srcData);
        return encrypt;
    }

    /**
     * decrypt
     *
     * @param encData
     * @param key
     * @param iv
     * @return
     * @throws Exception
     */
    public static byte[] aesDecrypt(byte[] encData, byte[] key, byte[] iv) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        Security.addProvider(new BouncyCastleProvider());
        Cipher cipher = Cipher.getInstance(AES_CBC_PKCS7, "BC");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
        byte[] decrypt = cipher.doFinal(encData);
        return decrypt;
    }
}

