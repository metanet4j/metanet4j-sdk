package com.metanet4j.sdk;

import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.exception.InvalidKeyException;
import com.metanet4j.base.bsv.BsvNetwork;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

        // :68—69 WIF 解码/校验失败。
        // 【重要修正 §95】此处原用 "not-a-valid-wif!!!"，但**它根本到不了本行**——
        // fromWIF 的**首个检查是长度必须为 51 或 52**，17 字符的串在那里就被拒了，
        // 于是断言虽然通过、`try` 内的这段却一次也没执行（"假覆盖"，与 §92 同型）。
        // 现改为**长度合规（51 字符）、首字符可被识别为主网、但校验和必然不匹配**的串，
        // 以确保异常确实来自 `try` 内的校验和/解码步骤。
        // 注意（实测所得）：**光让校验和错是不够的**——校验和检查在 try 内**直接抛**
        // "WIF 校验和不匹配"，**不经过 :68—69 的 catch**。
        // 要真正走到 catch，必须让 `Utils.fromBase58(...)` **自己抛错**，
        // 而长度又须合规 ⇒ 用 **51 字符、但含非法 base58 字符**的串
        // （base58 字母表不含 0、O、I、l，此处用 '0'）。
        String undecodable = "K" + "0".repeat(50);
        assertEquals(51, undecodable.length(), "构造的串长度须为 51，否则会被更早的长度检查拦下");
        InvalidKeyException decodeFailure = assertThrows(InvalidKeyException.class,
                () -> PrivateKey.fromWIF(undecodable),
                "长度合规但无法 base58 解码的 WIF 应转为 InvalidKeyException");
        // **关键**：断言消息来自 `:69`（"WIF 解码失败"），以**证明异常确实由 `try` 内抛出**。
        // 只断言异常类型是不够的——§225 的旧写法与 §92 都栽在"异常来自别处却以为覆盖了目标"。
        assertTrue(decodeFailure.getMessage().startsWith("WIF 解码失败"),
                "异常应来自 try 内的解码/校验步骤，而非更早的长度检查；实际为：" + decodeFailure.getMessage());
    }


    @Test
    void rejectsWifWithUnknownNetworkAndIllegalPayload() throws InvalidKeyException {
        // 覆盖 PrivateKey.fromWIF 余下两处校验：
        // :75—77 版本字节与目标网络不符、:72—73 载荷长度不足 33 字节。
        // 两条 WIF 都**由 API 自身构造**（toWif / toBase58Check），**不硬编码字面量**——
        // 硬编码会随实现或编码细节变动而失效，而由 API 生成则断言的是**关系**（跨网络应被拒、过短应被拒）。

        // 注：**:75—77「版本字节与网络不符」经 fromWIF(String) 不可达**——
        // 该入口先以 `decodeNetworkType(wif)` **从 WIF 自身推断网络**，
        // 故随后的版本比对**按构造必然成立**（详见 migration-rules §85）。
        // 此处改为断言"测试网 WIF 能被正确识别为其所属网络"，这才是该入口的真实契约。
        PrivateKey testnetKey = new PrivateKey(
                com.metanet4j.bsv.primitives.PrivateKey.fromString("01", 16), true, BsvNetwork.TESTNET3);
        String testnetWif = testnetKey.toWif(BsvNetwork.TESTNET3);
        assertNotNull(testnetWif, "应能导出测试网 WIF");
        assertEquals(BsvNetwork.TESTNET3, PrivateKey.fromWIF(testnetWif).getNetworkType(),
                "fromWIF 应能从 WIF 自身推断出所属网络");

        // :72—73 载荷过短的 base58check 串（仅 2 字节载荷）
        String tooShort = com.metanet4j.bsv.primitives.Utils.toBase58Check(
                java.util.List.of(0.0, 1.0), java.util.List.of(0x80));
        assertThrows(InvalidKeyException.class, () -> PrivateKey.fromWIF(tooShort),
                "载荷不足 33 字节的 WIF 应被拒绝");
    }

}
