package com.metanet4j.sdk.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.metanet4j.bsv.script.OP;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.sdk.PrivateKey;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/**
 * {@link RedeemDataExtend} 的取值契约。
 *
 * <p><b>设计变更（计划 §3）</b>：旧实现持有 {@code List<ECKey> keys}、按
 * {@code ECKey.PUBKEY_COMPARATOR} 排序并可从旧库 {@code RedeemData} 适配而来；
 * 新实现是**值类型**——只持有一把业务私钥与可选的 P2SH 赎回脚本，
 * 因为「按公钥哈希在 KeyBag 中反查密钥」的能力已随旧抽象取消，改为调用方经
 * {@code SigningKeyProvider} 显式提供。
 *
 * <p>随之取消的覆盖（已在 migration-rules §25.2 登记）：密钥排序、列表防御性复制、
 * 旧 {@code RedeemData} 适配、"从多个密钥中挑选有私钥者"。这些行为在新架构下不再存在，
 * 不是迁移遗漏。
 */
class RedeemDataExtendContractTest {

    @Test
    void holdsTheKeyAndTheRedeemScript() {
        PrivateKey key = PrivateKey.of(BigInteger.TWO);
        Script script = new Script().writeOpCode(OP.OP_1);

        RedeemDataExtend data = RedeemDataExtend.of(key, script);

        assertSame(key, data.getFullKey());
        assertSame(script, data.redeemScript);
        assertArrayEquals(key.getPubKey(), data.getPubKeyBytes(), "公钥字节应取所持私钥的压缩公钥");
        assertEquals(key.getKey().toHex(), data.getKeyHex());
    }

    @Test
    void absentKeyIsRepresentedAsNull() {
        RedeemDataExtend data = RedeemDataExtend.of(null, new Script());

        assertNull(data.getFullKey(), "无私钥时返回 null 而非抛错");
        assertNull(data.getPubKeyBytes());
        assertNull(data.getKeyHex());
    }

    @Test
    void redeemScriptMayBeAbsentForNonP2shInputs() {
        PrivateKey key = PrivateKey.of(BigInteger.valueOf(3));

        RedeemDataExtend data = new RedeemDataExtend(key, null);

        assertSame(key, data.key);
        assertNull(data.redeemScript, "非 P2SH 输入的赎回脚本为空");
        assertSame(data.key, data.getFullKey());
    }
}
