package com.metanet4j.sdk.bap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metanet4j.bsv.compat.HD;
import com.metanet4j.sdk.crypto.MasterPrivateKey;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    @Test
    void appendsIndicesAndPathsAndKeepsHashCodeConsistentWithEquals() {
        // 覆盖 §81 清单 A 中 BapPath 的 append(long)、append(BapPath)、hashCode。
        BapPath base = BapPath.of(List.of(0L, 1L));

        // append(long)：末段追加一个索引
        BapPath oneMore = base.append(2L);
        assertEquals(3, oneMore.size(), "append(long) 应使路径长度加一");
        assertEquals(2L, oneMore.lastIndex(), "追加的索引应成为末段");

        // append(BapPath)：整段拼接
        BapPath other = BapPath.of(List.of(3L, 4L));
        BapPath joined = base.append(other);
        assertEquals(base.size() + other.size(), joined.size(), "append(BapPath) 应拼接两段长度");
        assertEquals(other.lastIndex(), joined.lastIndex(), "拼接后末段应来自后者");

        // hashCode 与 equals 一致：等值对象哈希相同，不同对象（通常）不同
        assertEquals(BapPath.of(List.of(0L, 1L)), base, "同索引列表应相等");
        assertEquals(BapPath.of(List.of(0L, 1L)).hashCode(), base.hashCode(), "等值对象 hashCode 应相同");
        assertNotEquals(oneMore.hashCode(), base.hashCode(), "追加后的路径 hashCode 应不同");
    }


    @Test
    void parsesOptionalPrefixHardenedSuffixesAndValidatesBounds() {
        // 覆盖 BapPath 的 5 处缺口：
        // :38 `startsWith("m") || startsWith("M")` 的**假侧**、:47 三种硬化后缀、
        // :91 `prefix` 的越界检查、:107—108 `lastHardened` 对空路径的抛错、:182 `equals` 的假侧。

        // :38 假侧——**不带 m／M 前缀**的路径应与其带前缀形式等价
        assertEquals(BapPath.parse("m/0/1"), BapPath.parse("0/1"),
                "m 前缀应可省略");
        assertEquals(BapPath.parse("M/0/1"), BapPath.parse("0/1"),
                "M 前缀亦应可省略");

        // :47 三种硬化后缀写法应等价
        BapPath hardened = BapPath.parse("m/0'");
        assertEquals(hardened, BapPath.parse("m/0h"), "h 后缀应等同于撇号后缀");
        assertEquals(hardened, BapPath.parse("m/0H"), "H 后缀亦应等同");
        assertTrue(hardened.lastHardened(), "带硬化后缀的末段应为硬化");
        assertFalse(BapPath.parse("m/0").lastHardened(), "无后缀的末段不应为硬化");

        // :91 prefix 的越界检查（下界与上界各一次）
        BapPath path = BapPath.parse("m/0/1/2");
        assertEquals(2, path.prefix(2).size(), "合法前缀应可用");
        assertThrows(IllegalArgumentException.class, () -> path.prefix(-1), "负长度应被拒绝");
        assertThrows(IllegalArgumentException.class, () -> path.prefix(path.size() + 1),
                "超出段数应被拒绝");

        // :107—108 空路径没有末段
        assertThrows(IllegalStateException.class, () -> BapPath.of(List.of()).lastHardened(),
                "空路径取末段硬化态应抛错");

        // :182 equals 的假侧——与不同类型的对象比较应为 false（而非抛 ClassCastException）
        // :182 的 (instanceof 真, equals 假) 组合——同为 BapPath 但索引不同
        assertNotEquals(hardened, BapPath.parse("m/1'"), "同类型但索引不同应不相等");
        assertNotEquals(BapPath.parse("m/0"), BapPath.parse("m/0'"), "硬化位不同应不相等");
        assertNotEquals(hardened, "m/0'", "与字符串比较应返回 false");
        assertNotEquals(hardened, null, "与 null 比较应返回 false");
    }

}
