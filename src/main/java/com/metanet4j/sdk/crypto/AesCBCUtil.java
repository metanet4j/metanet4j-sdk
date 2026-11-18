package com.metanet4j.sdk.crypto;

import java.security.Security;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.experimental.UtilityClass;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

@UtilityClass
public class AesCBCUtil {
  private static final String AES_CBC_PKCS7 = "AES/CBC/PKCS7Padding";

  /**
   * AES/CBC/PKCS7 加密。
   *
   * @param srcData 待加密的明文
   * @param key AES 密钥（16/24/32 字节）
   * @param iv 16 字节初始化向量
   * @return 密文
   * @throws Exception 加密失败时抛出
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
   * AES/CBC/PKCS7 解密。
   *
   * @param encData 待解密的密文
   * @param key AES 密钥（16/24/32 字节）
   * @param iv 16 字节初始化向量
   * @return 明文
   * @throws Exception 解密失败时抛出
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
