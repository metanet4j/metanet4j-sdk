package com.metanet4j.sdk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import cn.hutool.core.util.HexUtil;
import com.metanet4j.bsv.primitives.PublicKey;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * `EcKeyLiteExtend` 的公开访问器契约（§81 清单 A 第二批）。
 *
 * <p>这些方法都是对内部 {@code PublicKey} 的简单转发，故用**关系断言**而非魔法字面量：
 * 断言"与底层 PublicKey 的同一编码一致"即其全部语义，
 * 避免在此层重复写死公钥字节（底层编码一改就会连带失败）。
 */
class EcKeyLiteExtendTest {

    private static final BigInteger SCALAR = BigInteger.valueOf(4242);

    @Test
    void exposesDerHashAndHexOfTheUnderlyingPublicKey() {
        // 注意类型边界（本轮实测确认）：
        //   · fromPrivateKey 接受的是 **bsv 原始 PrivateKey**，不是业务包装类型；
        //   · 业务 PrivateKey.getPublicKey() 返回**业务包装 PublicKey**；
        //   · 而 fromPublicOnly 接受的是 **bsv 原始 PublicKey**。
        // 故这里统一经 fromPrivate(BigInteger) 取原始公钥，避免跨类型的无谓换算。
        EcKeyLiteExtend withPriv = EcKeyLiteExtend.fromPrivate(SCALAR);
        PublicKey pub = withPriv.getPublicKey();
        EcKeyLiteExtend pubOnly = EcKeyLiteExtend.fromPublicOnly(pub);

        assertArrayEquals(toBytes(pub.toDER()), pubOnly.getPubKey(),
                "getPubKey 应返回底层公钥的 DER 编码");
        assertArrayEquals(toBytes(pub.toHash()), pubOnly.getPubKeyHash(),
                "getPubKeyHash 应返回底层公钥的 hash160");
        assertEquals(HexUtil.encodeHexStr(toBytes(pub.toDER())), pubOnly.getPublicKeyAsHex(),
                "getPublicKeyAsHex 应为 DER 编码的 hex");
        assertEquals(pub, pubOnly.getPublicKey(), "getPublicKey 应原样返回底层公钥");
    }

    @Test
    void distinguishesPrivateBearingInstancesFromPublicOnlyOnes() {
        // 覆盖 getPrivateKey 的两侧：仅公钥构造时为 null，由私钥构造时非 null
        assertNull(EcKeyLiteExtend.fromPublicOnly(
                        EcKeyLiteExtend.fromPrivate(SCALAR).getPublicKey()).getPrivateKey(),
                "仅公钥构造的实例不应携带私钥");
        assertNotNull(EcKeyLiteExtend.fromPrivate(SCALAR).getPrivateKey(),
                "由私钥构造的实例应能取回私钥");
    }

    /**
     * 新 SDK 的同一 {@code PublicKey} 上，{@code toDER()} 返回 {@code List<Integer>}
     * 而 {@code toHash()} 返回 {@code List<Double>}——**两者元素类型并不一致**（本轮实测确认）。
     * 因泛型擦除无法按元素类型重载，故统一收 {@code List<? extends Number>}。
     */
    private static byte[] toBytes(List<? extends Number> values) {
        byte[] out = new byte[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) (values.get(i).intValue() & 0xFF);
        }
        return out;
    }
}
