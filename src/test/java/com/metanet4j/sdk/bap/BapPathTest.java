package com.metanet4j.sdk.bap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metanet4j.bsv.compat.HD;
import com.metanet4j.sdk.crypto.MasterPrivateKey;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link BapPath} 的解析、格式化与派生行为。
 *
 * <p>覆盖 V03 的路径相关判定：硬化／非硬化区分、旧库 {@code H} 写法与新 SDK {@code '} 写法互通、
 * 非法路径拒绝，以及与 BIP32 固定向量的派生一致性。
 */
class BapPathTest {

    private static final List<String> WORDS = List.of("abandon", "abandon", "abandon", "abandon",
            "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "about");

    @Test
    void parsesLegacyHardenedNotation() {
        BapPath path = BapPath.parse("M/424150H/0H/0H/0H/0H/0H");
        assertEquals(6, path.size());
        assertTrue(path.lastHardened());
        assertEquals(0L, path.lastIndex());
        assertEquals("m/424150'/0'/0'/0'/0'/0'", path.toString());
    }

    @Test
    void parsesStandardNotationAndMixedCase() {
        assertEquals(BapPath.parse("M/44H/236H/1H/0/0"), BapPath.parse("m/44'/236'/1'/0/0"));
        BapPath path = BapPath.parse("M/44H/236H/1H/0/0");
        assertEquals(5, path.size());
        assertFalse(path.lastHardened());
        assertEquals(0L, path.lastIndex());
        // 第 4 段（索引 3）为非硬化
        assertFalse((path.indices().get(3) & BapPath.HARDENED_BIT) != 0);
        // 第 3 段（索引 2）为硬化
        assertTrue((path.indices().get(2) & BapPath.HARDENED_BIT) != 0);
    }

    @Test
    void parsesEmptyAndRootPaths() {
        assertTrue(BapPath.parse("").isEmpty());
        assertTrue(BapPath.parse(null).isEmpty());
        assertTrue(BapPath.parse("m").isEmpty());
        assertTrue(BapPath.parse("M").isEmpty());
        assertEquals("m", BapPath.parse("").toString());
    }

    @Test
    void rejectsMalformedSegmentsAndOutOfRangeIndices() {
        assertThrows(IllegalArgumentException.class, () -> BapPath.parse("m/abc"));
        assertThrows(IllegalArgumentException.class, () -> BapPath.parse("m/2147483648"));
        assertThrows(IllegalArgumentException.class, () -> BapPath.parse("m/-1"));
        assertThrows(IllegalArgumentException.class, () -> BapPath.parse("m/1/x2"));
    }

    @Test
    void supportsAppendPrefixAndSegmentQueries() {
        BapPath root = BapPath.parse("M/424150H/0H/0H/0H/0H");
        BapPath child = root.append(1, false);
        assertEquals(6, child.size());
        assertEquals(1L, child.lastIndex());
        assertFalse(child.lastHardened());
        assertEquals(root, child.prefix(5));
        assertEquals("m/424150'/0'/0'/0'/0'/1", child.toString());
        assertThrows(IllegalArgumentException.class, () -> root.prefix(7));
        assertThrows(IllegalStateException.class, () -> BapPath.parse("m").lastIndex());
    }

    @Test
    void appendFollowsCurrentNumberListConvention() {
        // 原实现：current = root[0..n-2] + new ChildNumber(last.num() + 1, last.isHardened())
        BapPath root = BapPath.parse("M/424150H/0H/0H/0H/0H/0H");
        BapPath current = root.prefix(5).append(root.lastIndex() + 1, root.lastHardened());
        assertEquals("m/424150'/0'/0'/0'/0'/1'", current.toString());
    }

    @Test
    void derivesTheSameKeyAsTheStandardVectorPath() {
        // BIP32／BIP39 固定向量：同一路径经 BapPath 与直接用 HD.derive 必须得到同一把密钥
        MasterPrivateKey master = MasterPrivateKey.fromSeed(WORDS, "TREZOR");
        HD root = master.getMasterDeterministicKey();
        BapPath path = BapPath.parse("m/0'/1");
        assertEquals(root.derive("m/0'/1").toString(), path.derive(root).toString());
        // 新 SDK 的 HD 无 equals：比对 xprv 字符串（相邻两行亦同此口径）
        assertEquals(root.derive("m/0").toString(), BapPath.parse("m/0").derive(root).toString());
        // 空路径返回主节点自身
        assertEquals(root.toString(), BapPath.parse("m").derive(root).toString());
        // 单段硬化路径
        assertEquals(root.derive("m/44'").toString(), BapPath.parse("M/44H").derive(root).toString());
    }

    @Test
    void deriveRejectsHardenedDerivationOnPublicNode() {
        MasterPrivateKey master = MasterPrivateKey.fromSeed(WORDS, "TREZOR");
        HD pub = master.getMasterDeterministicKey().toPublic();
        assertThrows(RuntimeException.class, () -> BapPath.parse("m/0'").derive(pub));
        // 非硬化派生在公钥节点上仍然可用
        assertEquals(master.getMasterDeterministicKey().derive("m/0").toPublic().toString(),
                BapPath.parse("m/0").derive(pub).toString());
    }
}
