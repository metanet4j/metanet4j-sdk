package com.metanet4j.sdk.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import com.metanet4j.bsv.compat.Mnemonic;
import org.junit.jupiter.api.Test;

class MasterPrivateKeyContractTest {

    @Test
    void historicalWordSequenceRestoresTheOriginalMasterKey() {
        // 独立预期来自固定旧制品 MnemonicCode.toSeed 与 HDKeyDerivation，见修复证据。
        List<String> words = List.of(("acoustic spirit category curve spare diagram odor torch "
                + "quantum museum result jewel").split(" "));
        assertEquals("xprv9s21ZrQH143K324Kmd8ZMqhdGRwFNPrh27ahAfUmP5YgeBMF3XivZQhScZrqcA8FhzTVEJeWCdnTK71SFmmwerHCqmuofDdDeC5pvQYwykG",
                MasterPrivateKey.fromSeed(words, "").toXprv());
        assertEquals("xprv9s21ZrQH143K25F6z97xP3NQQRV7PC1GQufsXWnkzZzcpxwKxx8vdR1u66gDvw1iWcxmdEo9tGJUYwXyp4EeXMKG8NyqXrJ5Dn1Bxf1Ew7D",
                MasterPrivateKey.fromSeed(words, "TREZOR").toXprv());
        assertEquals("xprv9s21ZrQH143K4Zo8qcwGvWAUByhyJ5UNjqRzxwbp1mmDnYKW68kMvruryonmVNqU3k6XmqZdoEYcbhoPWThye4N7E3G5DTqrmCUhxZ8FudG",
                MasterPrivateKey.fromSeed(words, "é").toXprv());
    }

    @Test
    void standardWordCountsGenerateImportableBip39Phrases() {
        for (int count : List.of(12, 15, 18, 21, 24)) {
            List<String> words = MasterPrivateKey.generateWordList(count);
            assertEquals(count, words.size());
            assertTrue(Mnemonic.isValid(String.join(" ", words)), "新钱包必须包含合法 checksum");
        }
    }

    @Test
    void generatedWordListsRespectCountsAndDictionaryBounds() {
        List<String> words = MasterPrivateKey.generateLongWordList();
        assertEquals(24, words.size());
        // 旧库用 MnemonicCode.INSTANCE.getWordList() 取 BIP39 英文单词表；
        // 新架构下业务侧已提供等价入口 MasterPrivateKey.wordList()（单词表副本），
        // 用它可避免测试再依赖 SDK 的 compat 层。
        assertTrue(MasterPrivateKey.wordList().containsAll(words));
        assertEquals(12, MasterPrivateKey.generateWordList().size());
        for (int count : List.of(1, 13, 30)) {
            List<String> sampled = MasterPrivateKey.generateWordList(count);
            assertEquals(count, new HashSet<>(sampled).size());
            assertTrue(MasterPrivateKey.wordList().containsAll(sampled));
        }
        assertTrue(MasterPrivateKey.generateWordList(0).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> MasterPrivateKey.generateWordList(-1));
        assertThrows(IllegalArgumentException.class, () -> MasterPrivateKey.generateWordList(2049));
    }

    @Test
    void bip39VectorAndSerializationRoundTrip() {
        List<String> words = List.of("abandon", "abandon", "abandon", "abandon", "abandon", "abandon",
                "abandon", "abandon", "abandon", "abandon", "abandon", "about");
        MasterPrivateKey fixed = MasterPrivateKey.fromSeed(words, "TREZOR");
        assertEquals("xprv9s21ZrQH143K3h3fDYiay8mocZ3afhfULfb5GX8kCBdno77K4HiA15Tg23wpbeF1pLfs1c5SPmYHrEpTuuRhxMwvKDwqdKiGJS9XFKzUsAF",
                fixed.toXprv());
        assertNotNull(fixed.getMasterDeterministicKey());
        assertTrue(fixed.toXpub().startsWith("xpub"));
        assertEquals(fixed.toXprv(), MasterPrivateKey.fromXprv(fixed.toXprv()).toXprv());

        MasterPrivateKey noSalt = MasterPrivateKey.fromSeed(words, "");
        assertEquals(noSalt.toXprv(), MasterPrivateKey.fromSeed(String.join(" ", words)).toXprv());
        assertNotEquals(fixed.toXprv(), noSalt.toXprv());
        assertFalse(MasterPrivateKey.fromSeed().toXprv().isEmpty());
        assertFalse(MasterPrivateKey.fromSeed(List.of(), "").toXprv().isEmpty());
    }

    @Test
    void exposesTheMasterPrivateKeyAndItsSerialisedHex() {
        // 覆盖 §81 清单 A 中 MasterPrivateKey 的 getMasterPrivateKey 与 toPrivKeyHex。
        // 入口为 fromSeed(words, passphrase)。这里用 **BIP39 标准测试向量**
        // （abandon ×11 + about），用固定输入验证确定性。
        List<String> words = List.of(("abandon abandon abandon abandon abandon abandon "
                + "abandon abandon abandon abandon abandon about").split(" "));
        MasterPrivateKey master = MasterPrivateKey.fromSeed(words, "TREZOR");

        assertNotNull(master.getMasterPrivateKey(), "应能取回主私钥");
        String hex = master.toPrivKeyHex();
        assertNotNull(hex, "toPrivKeyHex 不应为 null");
        assertEquals(64, hex.length(), "主私钥应为 32 字节、即 64 位 hex");
        assertTrue(hex.matches("[0-9a-f]+"), "应为小写 hex");

        // 同一助记词派生的两个实例应给出相同的主私钥与 hex（确定性）
        MasterPrivateKey again = MasterPrivateKey.fromSeed(words, "TREZOR");
        assertEquals(hex, again.toPrivKeyHex(), "派生应具确定性");
    }

}
