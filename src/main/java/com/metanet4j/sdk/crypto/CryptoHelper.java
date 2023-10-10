package com.metanet4j.sdk.crypto;


import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.RandomUtil;
import com.google.common.collect.Lists;
import com.metanet4j.sdk.PrivateKey;
import com.metanet4j.sdk.PublicKey;
import com.metanet4j.sdk.address.AddressEnhance;
import io.bitcoinsv.bitcoinjsv.core.Utils;
import io.bitcoinsv.bitcoinjsv.crypto.*;
import io.bitcoinsv.bitcoinjsv.params.MainNetParams;
import io.bitcoinsv.bitcoinjsv.params.NetworkParameters;
import com.metanet4j.sdk.exception.InvalidKeyException;
import lombok.Data;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Deprecated
public final class CryptoHelper {


    private CryptoHelper(){}




    public static CryptoBase generateRootCryptoBase(List<String> wordList, String salt,NetworkParameters parameters) throws InvalidKeyException {

        if (CollectionUtil.isEmpty(wordList)) {
            wordList = generateWordList();
        }
        DeterministicKey masterPrivateKey = HDKeyDerivation.createMasterPrivateKey((Utils.HEX.decode(HexUtil.encodeHexStr(MnemonicCode.toSeed(wordList, salt)))));
        DeterministicHierarchy dh = new DeterministicHierarchy(masterPrivateKey);
        DeterministicKey deterministicKey = dh
                .deriveChild(Lists.newArrayList(
                        new ChildNumber(44, true),
                        new ChildNumber(236, true),
                        new ChildNumber(0, true),
                        new ChildNumber(0, false)
                        ), false, true,
                        new ChildNumber(0, false));
       return getCryptoBase(parameters, deterministicKey);
    }




    public static CryptoBase generateRootCryptoBase(List<String> wordList, String salt) throws InvalidKeyException {
        return generateRootCryptoBase(wordList,salt,MainNetParams.get());
    }

    public static CryptoBase generateRootCryptoBase() throws InvalidKeyException {
        return generateRootCryptoBase(generateWordList(),"",MainNetParams.get());
    }

    public static CryptoBase generateChlidCryptoBase(DeterministicKey parentDeterministicKey,ChildNumber childNumber,NetworkParameters parameters) throws InvalidKeyException {
        DeterministicKey deterministicKey = HDKeyDerivation.deriveChildKey(parentDeterministicKey,childNumber);
        return getCryptoBase(parameters, deterministicKey);
    }

    public static CryptoBase generateChlidCryptoBase(DeterministicKey parentDeterministicKey,ChildNumber childNumber) throws InvalidKeyException {
        return generateChlidCryptoBase(parentDeterministicKey,childNumber, MainNetParams.get());
    }

    private static List<String> generateWordList() {
        List<String> wordsList;
        Set<Integer> indexSet = new HashSet<>();
        while (indexSet.size() != 12) {
            indexSet.add(RandomUtil.randomInt(0, 2048 - 1));
        }

        MnemonicCode mnemonicCode = MnemonicCode.INSTANCE;

        wordsList = indexSet.stream().map(i -> mnemonicCode.getWordList().get(i))
                .collect(Collectors.toList());
        return wordsList;
    }

    private static CryptoBase getCryptoBase(NetworkParameters parameters, DeterministicKey deterministicKey) throws InvalidKeyException {
        CryptoBase cryptoBase = new CryptoBase();
        PrivateKey privateKey = PrivateKey.fromWIF(deterministicKey.getPrivateKeyAsWiF(parameters));
        PublicKey publicKey = PublicKey.fromHex(deterministicKey.getPublicKeyAsHex());
        AddressEnhance legacyAddress = AddressEnhance.fromPubKeyHash(parameters, deterministicKey.getPubKeyHash());
        cryptoBase.setDeterministicKey(deterministicKey);
        cryptoBase.setPrivateKey(privateKey);
        cryptoBase.setPrivateKeyToWif(privateKey.toWif(parameters));
        cryptoBase.setPublicKey(publicKey);
        cryptoBase.setPublicKeyHex(publicKey.getPubKeyHex());
        cryptoBase.setLegacyAddress(legacyAddress);
        cryptoBase.setAddressBase58(legacyAddress.toBase58());
        return cryptoBase ;
    }

    @Data
   public static class CryptoBase {
        private DeterministicKey deterministicKey;
        private PrivateKey privateKey;
        private String privateKeyToWif;
        private PublicKey publicKey;
        private String publicKeyHex;
        private AddressEnhance legacyAddress;
        private String addressBase58;
    }

}
