package com.metanet4j.sdk.address;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.metanet4j.sdk.PrivateKey;
import com.metanet4j.base.bsv.BsvNetwork;
import org.junit.jupiter.api.Test;

public class AddressLiteTest {

    @Test
    public void testBase58(){
        AddressEnhance address = AddressEnhance.fromBase58(BsvNetwork.REGTEST, "mqACwWBepT6dPqrs8vqxE2sBGKjXBSv9Tt");
        System.out.println(address.toBase58());
    }



    @Test
    void delegatesNetworkVersionScriptHashFlagAndHashCodeToBsvAddress() {
        // 覆盖 AddressEnhance 的 6 个委托方法：fromPrivateKey(两种重载)、getNetwork、getVersion、
        // isP2SHAddress、toBsvAddress、hashCode。
        // 用**关系断言**而非魔法数字——这些方法本身只是转发，断言"与底层一致"即其全部语义。
        PrivateKey key = PrivateKey.of(java.math.BigInteger.valueOf(12345));

        AddressEnhance mainnet = AddressEnhance.fromPrivateKey(key);
        assertEquals(BsvNetwork.MAINNET, mainnet.getNetwork(), "默认重载应使用主网");
        assertEquals(mainnet.toBsvAddress().getVersion(), mainnet.getVersion(),
                "getVersion 应与底层 BsvAddress 一致");
        assertEquals(mainnet.toBsvAddress().isP2SHAddress(), mainnet.isP2SHAddress(),
                "isP2SHAddress 应与底层一致");
        assertEquals(mainnet.toBsvAddress().toBase58(), mainnet.toBase58(),
                "toBsvAddress 应返回同一地址");
        assertEquals(mainnet.toBsvAddress().hashCode(), mainnet.hashCode(),
                "hashCode 应与底层一致");

        // 指定网络的重载：传入测试网应得到测试网地址
        AddressEnhance testnet = AddressEnhance.fromPrivateKey(BsvNetwork.TESTNET3, key.transformEcKey());
        assertEquals(BsvNetwork.TESTNET3, testnet.getNetwork(), "指定网络的重载应使用传入的网络");
    }


    @Test
    void constructsScriptHashAddressesAndComparesByUnderlyingAddress() {
        // 覆盖 AddressEnhance 的两处缺口：
        // ① 构造器 `version == network.getP2SHHeader() ? fromScriptHash : fromPubKeyHash` 的 **P2SH 侧**；
        // ② `equals` 的 `other instanceof AddressEnhance value && …` **假侧**。
        byte[] hash = new byte[20];
        java.util.Arrays.fill(hash, (byte) 7);

        // ① P2SH 侧：版本字节取该网络的脚本哈希头
        AddressEnhance p2sh = new AddressEnhance(BsvNetwork.MAINNET,
                BsvNetwork.MAINNET.getP2SHHeader(), hash);
        assertTrue(p2sh.isP2SHAddress(), "以 P2SH 版本构造的地址应识别为脚本哈希地址");

        // 对照：默认构造走 P2PKH 侧
        AddressEnhance p2pkh = new AddressEnhance(BsvNetwork.MAINNET, hash);
        assertFalse(p2pkh.isP2SHAddress(), "默认构造应为公钥哈希地址");

        // ② equals：同底层地址相等；不同地址、不同类、null 均不等
        assertEquals(p2sh, new AddressEnhance(BsvNetwork.MAINNET,
                BsvNetwork.MAINNET.getP2SHHeader(), hash.clone()), "同参数构造应相等");
        assertNotEquals(p2sh, p2pkh, "脚本哈希与公钥哈希地址不应相等");
        assertNotEquals(p2sh, "not an address", "与非同类比较应返回 false 而非抛错");
        assertNotEquals(p2sh, null, "与 null 比较应返回 false");
    }

}
