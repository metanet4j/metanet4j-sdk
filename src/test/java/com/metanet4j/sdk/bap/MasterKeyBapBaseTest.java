package com.metanet4j.sdk.bap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.crypto.MasterPrivateKey;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * `MasterKeyBapBase` 的两个 `protected` 派生入口（§82 清单真实方法）。
 *
 * <p>二者均为 `protected`，故测试须置于**同包**（`com.metanet4j.sdk.bap`）方可直接调用——
 * 这也是 §208 勘查给出的触达方式。本类不改可见性、不加测试专用 API。
 */
class MasterKeyBapBaseTest {

    @Test
    void derivesTheSameKeyThroughBothGetKeyBasePathOverloads() {
        MasterPrivateKey master = MasterPrivateKey.fromSeed(
                List.of(("abandon abandon abandon abandon abandon abandon "
                        + "abandon abandon abandon abandon abandon about").split(" ")), "TREZOR");
        // MasterKeyBapBase 是**抽象类**（其派生入口 protected、供子类复用），故以匿名子类实例化；
        // 这不改变被测行为——两个 getKeyBasePath 都定义在本类内。
        MasterKeyBapBase base = new MasterKeyBapBase(master, BapPath.of(List.of()),
                BapPath.of(List.of()), new DefaultBapBaseConfig()) {
        };

        BapPath path = BapPath.of(List.of(0L, 1L));

        // 重载一：直接以 BapPath 派生
        assertNotNull(base.getKeyBasePath(path), "getKeyBasePath(BapPath) 应返回派生密钥");

        // 重载二：以**路径串**派生——字符串由 BapPath.toString() 自身给出，
        // 避免在此硬编码路径格式（格式若变，本断言随之失效而非误报）。
        assertNotNull(base.getKeyBasePath(path.toString()), "getKeyBasePath(String) 应返回派生密钥");

        // 两者语义应一致：同一路径经不同入口得到同一密钥
        assertEquals(base.getKeyBasePath(path).privKey.toHex(),
                base.getKeyBasePath(path.toString()).privKey.toHex(),
                "两个重载对同一路径应派生出同一密钥");
    }
}
