package com.metanet4j.sdk.crypto;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.metanet4j.bsv.compat.Bip39WordlistEn;
import com.metanet4j.bsv.compat.HD;
import com.metanet4j.bsv.compat.Mnemonic;
import com.metanet4j.bsv.primitives.PrivateKey;
import com.metanet4j.bsv.primitives.Utils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * BIP39 助记词 + BIP32 主私钥包装。
 *
 * <p>旧实现基于 bitcoinj 的 {@code DeterministicKey}／{@code HDKeyDerivation}／{@code MnemonicCode}；
 * 现改用新 SDK（com.metanet4j.bsv）的 {@code compat.HD} 与 {@code compat.Mnemonic}。
 * 助记词 → seed → 主密钥的推导链、xprv／xpub 序列化与单词表语义保持不变（V03 行为验收）。
 */
public class MasterPrivateKey {

    private final HD masterDeterministicKey;

    public MasterPrivateKey(HD masterDeterministicKey) {
        this.masterDeterministicKey = masterDeterministicKey;
    }

    /** 解析 xprv。 */
    public static MasterPrivateKey fromXprv(String xprv) {
        return new MasterPrivateKey(HD.fromString(xprv));
    }

    /** 底层 BIP32 节点（新 SDK 的 {@code compat.HD}）。 */
    public HD getMasterDeterministicKey() {
        return masterDeterministicKey;
    }

    /** 底层主私钥。 */
    public PrivateKey getMasterPrivateKey() {
        return masterDeterministicKey.privKey;
    }

    /** 序列化为 xprv。 */
    public String toXprv() {
        return masterDeterministicKey.toString();
    }

    /** 序列化为 xpub。 */
    public String toXpub() {
        return masterDeterministicKey.toPublic().toString();
    }

    public static MasterPrivateKey fromSeed() {
        // 无参入口即「生成随机钱包」：必须用能产出**合法** BIP39 助记词（含校验和）的
        // Mnemonic.fromRandom()，而不是 generateWordList()（后者只随机抽词、无校验和，
        // 交给 Mnemonic.fromString 必然抛 "does not pass the check"）。
        return new MasterPrivateKey(HD.fromSeed(Mnemonic.fromRandom().toSeed("")));
    }

    public static MasterPrivateKey fromSeed(String seed) {
        return fromSeed(StrUtil.split(seed, " "), "");
    }

    public static MasterPrivateKey fromSeed(List<String> wordList, String salt) {
        if (CollectionUtil.isEmpty(wordList)) {
            // 空列表按约定「生成随机钱包」，同样需要合法助记词（见 fromSeed() 的说明）。
            return new MasterPrivateKey(HD.fromSeed(Mnemonic.fromRandom().toSeed(salt)));
        }
        List<Integer> seed = Mnemonic.fromString(String.join(Bip39WordlistEn.SPACE, wordList))
                .toSeed(salt);
        return new MasterPrivateKey(HD.fromSeed(seed));
    }

    public static List<String> generateLongWordList() {
        return generateWordList(24);
    }

    public static List<String> generateWordList() {
        return generateWordList(12);
    }

    /**
     * 从 BIP39 英文单词表随机抽取指定数量的不重复单词。
     *
     * @throws IllegalArgumentException 数量越界（小于 0 或超过单词表容量）
     */
    public static List<String> generateWordList(int len) {
        List<String> dictionary = Bip39WordlistEn.VALUE;
        if (len < 0 || len > dictionary.size()) {
            throw new IllegalArgumentException("word count out of range: " + len);
        }
        Set<Integer> indexSet = new HashSet<>();
        while (indexSet.size() != len) {
            indexSet.add(RandomUtil.randomInt(0, dictionary.size()));
        }
        return indexSet.stream().map(dictionary::get).collect(Collectors.toList());
    }

    /** 单词表副本（供调用方校验助记词合法性）。 */
    public static List<String> wordList() {
        return new ArrayList<>(Bip39WordlistEn.VALUE);
    }

    /** 主私钥 hex，便于与既有断言比较。 */
    public String toPrivKeyHex() {
        return Utils.toHex(masterDeterministicKey.privKey.toArray("be", 32));
    }
}
