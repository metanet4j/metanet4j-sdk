package com.metanet4j.sdk.crypto;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import com.google.common.base.Charsets;
import com.metanet4j.bsv.primitives.PrivateKey;
import com.metanet4j.bsv.primitives.PublicKey;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * ECIES（Bitcore 风格 BIE1 报文）：ECDH 共享密钥 → SHA-512 → AES-CBC + HMAC-SHA256。
 *
 * <p>旧实现以 bitcoinj 的 {@code ECKeyLite} 承载密钥；现改用新 SDK（com.metanet4j.bsv）的
 * {@link PrivateKey}／{@link PublicKey}，报文结构与密钥派生顺序（payload + mac）保持不变，
 * 既有密文可继续解密（V06 行为验收）。
 */
@UtilityClass
public class Ecies {

    /** 报文魔数。 */
    private static final byte[] MAGIC = "BIE1".getBytes(Charsets.UTF_8);

    /**
     * 加密。
     *
     * @param bytes  明文
     * @param pubKey 接收方公钥
     * @param privKey 临时私钥；为 {@code null} 时随机生成（与旧实现一致）
     */
    public static byte[] encrypt(byte[] bytes, PublicKey pubKey, @Nullable PrivateKey privKey)
            throws Exception {
        PrivateKey ephemeralKey = privKey == null ? PrivateKey.fromRandom() : privKey;
        byte[] ecdhKey = ecdh(ephemeralKey, pubKey);

        byte[] iv = ArrayUtil.sub(ecdhKey, 0, 16);
        byte[] keye = ArrayUtil.sub(ecdhKey, 16, 32);
        byte[] keym = ArrayUtil.sub(ecdhKey, 32, 64);
        byte[] cryptedBytes = AesCBCUtil.aesEncrypt(bytes, keye, iv);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.writeBytes(MAGIC);
        outputStream.writeBytes(toBytes(ephemeralKey.toPublicKey().toDER()));
        outputStream.writeBytes(cryptedBytes);

        HMac hMac = new HMac(HmacAlgorithm.HmacSHA256, keym);
        byte[] hmacBytes = hMac.digest(outputStream.toByteArray());

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.writeBytes(outputStream.toByteArray());
        result.writeBytes(hmacBytes);
        return result.toByteArray();
    }

    /** 加密并 Base64 编码。 */
    public static String encrypt(String plainText, PublicKey pubKey, @Nullable PrivateKey privKey)
            throws Exception {
        return Base64.encode(encrypt(plainText.getBytes(Charsets.UTF_8), pubKey, privKey));
    }

    /**
     * 解密。
     *
     * @param buf       完整 BIE1 报文（魔数 + 临时公钥 + 密文 + MAC）
     * @param privKey   接收方私钥
     * @param publicKey 临时公钥；为 {@code null} 时从报文头部解析
     */
    public static byte[] decrypt(byte[] buf, PrivateKey privKey, @Nullable PublicKey publicKey)
            throws Exception {
        byte[] magic = ArrayUtil.sub(buf, 0, 4);
        if (!new String(magic, Charset.forName("UTF-8")).equals("BIE1")) {
            throw new RuntimeException("magic is not match");
        }
        PublicKey ephemeralPubkey = publicKey == null
                ? PublicKey.fromDER(box(ArrayUtil.sub(buf, 4, 37)))
                : publicKey;

        byte[] ciphertext = ArrayUtil.sub(buf, 37, buf.length - 32);
        byte[] mac = ArrayUtil.sub(buf, buf.length - 32, buf.length);

        byte[] ecdhKey = ecdh(privKey, ephemeralPubkey);
        byte[] iv = ArrayUtil.sub(ecdhKey, 0, 16);
        byte[] keye = ArrayUtil.sub(ecdhKey, 16, 32);
        byte[] keym = ArrayUtil.sub(ecdhKey, 32, 64);

        byte[] crypted = ArrayUtil.sub(buf, 0, buf.length - 32);
        HMac hMac = new HMac(HmacAlgorithm.HmacSHA256, keym);
        if (!ArrayUtil.equals(hMac.digest(crypted), mac)) {
            throw new RuntimeException("mac check error");
        }
        return AesCBCUtil.aesDecrypt(ciphertext, keye, iv);
    }

    /** 解密并 Base64 兼容入口（明文按 UTF-8 返回）。 */
    public static String decrypt(String base64Payload, PrivateKey privKey) throws Exception {
        return new String(decrypt(Base64.decode(base64Payload), privKey, null), Charsets.UTF_8);
    }

    private static byte[] ecdh(PrivateKey privKey, PublicKey pubKey) {
        byte[] shared = toBytes(privKey.deriveSharedSecret(pubKey).encode(true));
        return DigestUtilExtend.sha512(shared);
    }

    private static List<Double> box(byte[] data) {
        Double[] values = new Double[data.length];
        for (int i = 0; i < data.length; i++) {
            values[i] = (double) (data[i] & 0xFF);
        }
        return List.of(values);
    }

    private static byte[] toBytes(List<?> values) {
        byte[] bytes = new byte[values.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ((Number) values.get(i)).intValue();
        }
        return bytes;
    }
}
