package com.metanet4j.sdk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import cn.hutool.core.util.HexUtil;
import com.metanet4j.base.bsv.BsvHashBytes;
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

    @Test
    void guardsNullKeysMalformedSignaturesAndPublicOnlySigning() {
        // 覆盖 EcKeyLiteExtend 的守卫与错误路径（6 处缺口）：
        // :30 构造器中 `priv == null ? null : …` 的 null 侧、:34—35「两参皆空」的抛错、
        // :85—86 与 :100—101 的两处「签名不合法即返回 false」、:163—164「仅公钥不可签名」的抛错。
        EcKeyLiteExtend withPriv = EcKeyLiteExtend.fromPrivate(SCALAR);
        BsvHashBytes digest = BsvHashBytes.of(new byte[32]);

        // :34—35 私钥与公钥不能同时为空
        assertThrows(IllegalArgumentException.class, () -> new EcKeyLiteExtend(null, null),
                "两参皆空应被拒绝");

        // :30 的 null 侧——只传公钥（仅验签场景）
        EcKeyLiteExtend pubOnly = new EcKeyLiteExtend(null, withPriv.getPublicKey());
        assertNull(pubOnly.getPrivateKey(), "仅传公钥时不应携带私钥");

        // :85—86 两种「签名不合法」：null 与长度非 65
        assertFalse(withPriv.verify(digest, (byte[]) null), "null 签名应返回 false");
        assertFalse(withPriv.verify(digest, new byte[64]), "长度非 65 的签名应返回 false");

        // :100—101 的 null 侧——Base64 签名为 null 时短路返回 false（不做解码）
        assertFalse(withPriv.verify(digest, (String) null), "null 的 Base64 签名应返回 false");

        // :101 的**右侧**——非 null 的 Base64 串应真正解码并交给字节版校验（而非被短路）
        // 用一个长度合法（65 字节）但内容非法的签名，断言解码后被判为不通过
        String base64Of65Bytes = java.util.Base64.getEncoder().encodeToString(new byte[65]);
        assertFalse(withPriv.verify(digest, base64Of65Bytes),
                "非 null 的 Base64 签名应解码后校验并返回 false");

        // :163—164 仅公钥实例不可签名
        assertThrows(IllegalStateException.class, () -> pubOnly.signHash(new byte[32]),
                "仅含公钥的实例签名时应报错而非静默产出无效签名");
    }

}
