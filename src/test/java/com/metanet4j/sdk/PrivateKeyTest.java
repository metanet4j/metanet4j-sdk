package com.metanet4j.sdk;

import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.exception.InvalidKeyException;
import com.metanet4j.base.bsv.BsvNetwork;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

public class PrivateKeyTest {
    @Test
    public void test(){
        try {
            PrivateKey privateKey = PrivateKey.fromWIF("L3bX9g5pS1R9arHKEEzCNMYUn9xy8AQMk3hf5kQz8o3mAcLd5oqR");
            System.out.println(privateKey.toWif(BsvNetwork.MAINNET));
            String hex = privateKey.getPublicKey().getPubKeyHex();
            System.out.println(hex);
            AddressEnhance address = AddressEnhance.fromPublicKey(BsvNetwork.MAINNET, privateKey.getPublicKey());
            System.out.println(address.toBase58());
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
    }

    @Test
    void coversFactoryOverloadsNetworkTypeCompressionAndEcKeyTransform() {
        // 覆盖 §81 清单 A 中 PrivateKey 的四个方法：of（三个重载）、getNetworkType、isCompressed、transformEcKey。
        // 三个 of 重载都约定「主网 + 压缩」，故同时断言这两项，并验证三者得到**同一密钥**。
        PrivateKey fromScalar = PrivateKey.of(BigInteger.valueOf(98765));
        assertEquals(BsvNetwork.MAINNET, fromScalar.getNetworkType(), "of(BigInteger) 应使用主网");
        assertTrue(fromScalar.isCompressed(), "of(BigInteger) 应产出压缩密钥");

        byte[] bytes = new byte[32];
        bytes[31] = 1;   // 标量 1
        PrivateKey fromBytes = PrivateKey.of(bytes);
        assertEquals(BsvNetwork.MAINNET, fromBytes.getNetworkType(), "of(byte[]) 应使用主网");
        assertTrue(fromBytes.isCompressed(), "of(byte[]) 应产出压缩密钥");
        assertEquals(PrivateKey.of(BigInteger.ONE).transformEcKey().toHex(),
                fromBytes.transformEcKey().toHex(),
                "of(byte[]) 与 of(BigInteger) 对同一标量应得到同一密钥");

        // 第三个重载：由 bsv 原始私钥包装；与 transformEcKey（业务→bsv）互为反向
        com.metanet4j.bsv.primitives.PrivateKey raw = fromScalar.transformEcKey();
        PrivateKey rewrapped = PrivateKey.of(raw);
        assertEquals(BsvNetwork.MAINNET, rewrapped.getNetworkType(), "of(bsv PrivateKey) 应使用主网");
        assertTrue(rewrapped.isCompressed(), "of(bsv PrivateKey) 应产出压缩密钥");
        assertEquals(raw.toHex(), rewrapped.transformEcKey().toHex(),
                "经 transformEcKey 与 of(bsv PrivateKey) 往返后密钥应不变");
    }


    @Test
    void validatesConstructorAndWifInputs() {
        // 覆盖 PrivateKey 的构造器与 WIF 解析校验：
        // :30—31 空私钥、:35 网络为 null、:44—45 空 WIF、:68—69 WIF 解码失败。
        // 这些都是**调用方真会传入的非法输入**，断言它们**报错而非静默产出无效密钥**。

        // :30—31 底层私钥不能为空
        assertThrows(IllegalArgumentException.class,
                () -> new PrivateKey((com.metanet4j.bsv.primitives.PrivateKey) null),
                "底层私钥为空应被拒绝");

        // :35 网络为 null 时默认主网（真实用法：多数调用方只关心密钥本身）
        PrivateKey defaultedNetwork = new PrivateKey(
                com.metanet4j.bsv.primitives.PrivateKey.fromString("01", 16), true, null);
        assertEquals(BsvNetwork.MAINNET, defaultedNetwork.getNetworkType(),
                "未指定网络时应默认主网");

        // :44—45 WIF 不能为空
        assertThrows(InvalidKeyException.class, () -> PrivateKey.fromWIF(null),
                "空 WIF 应被拒绝");

        // :68—69 WIF 解码失败（非 base58 字符）
        assertThrows(InvalidKeyException.class, () -> PrivateKey.fromWIF("not-a-valid-wif!!!"),
                "无法解码的 WIF 应转为 InvalidKeyException");
    }

}
