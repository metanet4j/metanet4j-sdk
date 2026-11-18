package com.metanet4j.sdk.bap;

import cn.hutool.core.codec.Base64;
import com.metanet4j.sdk.PrivateKey;
import com.metanet4j.sdk.PublicKey;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.crypto.Ecies;
import com.metanet4j.sdk.remote.RemoteSignType;
import java.nio.charset.Charset;

public interface BapBaseCore {

  /**
   * Get the root private key for signing.
   *
   * @return 根私钥
   */
  PrivateKey getRootPrivateKey();

  /**
   * Get the current private key for signing.
   *
   * @return 当前私钥
   */
  PrivateKey getCurrentPrivateKey();

  /**
   * Get the previous private key for signing, often used for signing the "new ID" operation in the
   * BAP protocol.
   *
   * @return 上一把私钥
   */
  PrivateKey getPreviousPrivateKey();

  /**
   * Get the encryption private key for encryption
   *
   * @return 加密私钥
   */
  PrivateKey getEncryptKey();

  /**
   * Get the ordinals private key for sending ordinals UTXO.
   *
   * @return ordinals 私钥
   */
  PrivateKey getOrdPrivateKey();

  /**
   * Get the payment account private key for making payments
   *
   * @return 支付账户私钥
   */
  PrivateKey getPayAccountKey();

  /**
   * Get the public key of a friend for adding as a friend.
   *
   * @return 好友公钥
   */
  PublicKey getFriendPublicKey();

  String getIdentityKey();

  String getAppName();

  default String getRootAddress() {
    return AddressEnhance.fromECkeyLite(getRootPrivateKey()).toBase58();
  }

  default AddressEnhance getPreviouAddress() {
    return AddressEnhance.fromECkeyLite(getPreviousPrivateKey());
  }

  default AddressEnhance getCurrentAddress() {
    return AddressEnhance.fromECkeyLite(getCurrentPrivateKey());
  }

  default AddressEnhance getPayAccountAddress() {
    return AddressEnhance.fromECkeyLite(getPayAccountKey());
  }

  default AddressEnhance getOrdAddress() {
    return AddressEnhance.fromECkeyLite(getOrdPrivateKey());
  }

  default String getSignAddress(RemoteSignType remoteSignType) {
    return switch (remoteSignType) {
      case CURRENT -> getCurrentAddress().toBase58();
      case PREVIOUS -> getPreviouAddress().toBase58();
      case ROOT -> getRootAddress();
      case ORD -> getOrdAddress().toBase58();
      case PAYMENT -> getPayAccountAddress().toBase58();
    };
  }

  default byte[] encrypt(byte[] bytes) {
    try {
      return Ecies.encrypt(bytes, getEncryptKey().getKey().toPublicKey(), null);
    } catch (Exception e) {
      throw new IllegalStateException("BAP 字节加密失败", e);
    }
  }

  default String encrypt(String plainText) {
    try {
      return Ecies.encrypt(plainText, getEncryptKey().getKey().toPublicKey(), null);
    } catch (Exception e) {
      throw new IllegalStateException("BAP 文本加密失败", e);
    }
  }

  default String decrypt(String encryptedPlainText) {
    try {
      return new String(
          Ecies.decrypt(Base64.decode(encryptedPlainText), getEncryptKey().getKey(), null),
          Charset.forName("UTF-8"));
    } catch (Exception e) {
      throw new IllegalStateException("BAP 文本解密失败", e);
    }
  }
}
