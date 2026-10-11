package com.metanet4j.sdk.address;

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

}
