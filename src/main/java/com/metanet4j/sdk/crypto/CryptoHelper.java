package com.metanet4j.sdk.crypto;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.RandomUtil;
import com.metanet4j.base.bsv.BsvNetwork;
import com.metanet4j.bsv.compat.Bip39WordlistEn;
import com.metanet4j.bsv.compat.HD;
import com.metanet4j.bsv.compat.Mnemonic;
import com.metanet4j.sdk.PrivateKey;
import com.metanet4j.sdk.PublicKey;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.bap.BapPath;
import com.metanet4j.sdk.exception.InvalidKeyException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;

/**
 * 根密钥与子密钥生成（已废弃，保留兼容入口）。
 *
 * <p>旧实现基于 bitcoinj 的 {@code DeterministicKey}／{@code HDKeyDerivation}／{@code MnemonicCode}；
 * 现改用新 SDK（com.metanet4j.bsv）的 {@code compat.HD}／{@code compat.Mnemonic} 与业务路径类型
 * {@link BapPath}。派生路径 {@code m/44'/236'/0'/0/0} 与旧实现逐段一致。
 */
@Deprecated
public final class CryptoHelper {

    /** 根密钥派生路径（对应旧实现 44H/236H/0H/0/0）。 */
    private static final String ROOT_DERIVATION_PATH = "m/44'/236'/0'/0/0";

    private CryptoHelper() {
    }

    public static CryptoBase generateRootCryptoBase(List<String> wordList, String salt,
                                                    BsvNetwork parameters) throws InvalidKeyException {
        if (CollectionUtil.isEmpty(wordList)) {
            wordList = generateWordList();
        }
        HD master = HD.fromSeed(Mnemonic.fromString(
                String.join(Bip39WordlistEn.SPACE, wordList)).toSeed(salt));
        HD deterministicKey = BapPath.parse(ROOT_DERIVATION_PATH).derive(master);
        return getCryptoBase(parameters, deterministicKey);
    }

    public static CryptoBase generateRootCryptoBase(List<String> wordList, String salt)
            throws InvalidKeyException {
        return generateRootCryptoBase(wordList, salt, BsvNetwork.MAINNET);
    }

    public static CryptoBase generateRootCryptoBase() throws InvalidKeyException {
        return generateRootCryptoBase(generateWordList(), "", BsvNetwork.MAINNET);
    }

    public static CryptoBase generateChlidCryptoBase(HD parentDeterministicKey, long childIndex,
                                                     BsvNetwork parameters) throws InvalidKeyException {
        return getCryptoBase(parameters, parentDeterministicKey.deriveChild(childIndex));
    }

    public static CryptoBase generateChlidCryptoBase(HD parentDeterministicKey, long childIndex)
            throws InvalidKeyException {
        return generateChlidCryptoBase(parentDeterministicKey, childIndex, BsvNetwork.MAINNET);
    }

    private static List<String> generateWordList() {
        List<String> dictionary = Bip39WordlistEn.VALUE;
        Set<Integer> indexSet = new HashSet<>();
        while (indexSet.size() != 12) {
            indexSet.add(RandomUtil.randomInt(0, dictionary.size() - 1));
        }
        return indexSet.stream().map(dictionary::get).collect(Collectors.toList());
    }

    private static CryptoBase getCryptoBase(BsvNetwork parameters, HD deterministicKey)
            throws InvalidKeyException {
        CryptoBase cryptoBase = new CryptoBase();
        PrivateKey privateKey = new PrivateKey(deterministicKey.privKey, true, parameters);
        PublicKey publicKey = PublicKey.of(deterministicKey.privKey.toPublicKey());
        AddressEnhance legacyAddress = AddressEnhance.fromPublicKey(parameters,
                deterministicKey.privKey.toPublicKey());
        cryptoBase.setDeterministicKey(deterministicKey);
        cryptoBase.setPrivateKey(privateKey);
        cryptoBase.setPrivateKeyToWif(privateKey.toWif(parameters));
        cryptoBase.setPublicKey(publicKey);
        cryptoBase.setPublicKeyHex(publicKey.getPubKeyHex());
        cryptoBase.setLegacyAddress(legacyAddress);
        cryptoBase.setAddressBase58(legacyAddress.toBase58());
        return cryptoBase;
    }

    /** 根／子密钥载体；{@code deterministicKey} 为新 SDK 的 BIP32 节点。 */
    @Data
    public static class CryptoBase {
        private HD deterministicKey;
        private PrivateKey privateKey;
        private String privateKeyToWif;
        private PublicKey publicKey;
        private String publicKeyHex;
        private AddressEnhance legacyAddress;
        private String addressBase58;
    }
}
