package com.metanet4j.sdk.crypto;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import io.bitcoinsv.bitcoinjsv.core.Utils;
import io.bitcoinsv.bitcoinjsv.crypto.DeterministicKey;
import io.bitcoinsv.bitcoinjsv.crypto.HDKeyDerivation;
import io.bitcoinsv.bitcoinjsv.crypto.MnemonicCode;
import io.bitcoinsv.bitcoinjsv.params.MainNetParams;
import io.bitcoinsv.bitcoinjsv.params.NetworkParameters;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MasterPrivateKey {

    private  DeterministicKey masterDeterministicKey;

    public MasterPrivateKey(DeterministicKey masterDeterministicKey) {
        this.masterDeterministicKey = masterDeterministicKey;
    }

    public static MasterPrivateKey fromXprv(String xprv) {
        DeterministicKey masterPrivateKey = DeterministicKey.deserializeB58(xprv, MainNetParams.get());
        return new MasterPrivateKey(masterPrivateKey);
    }


    public DeterministicKey getMasterDeterministicKey() {
        return masterDeterministicKey;
    }

    public  String toXprv(){
      return   masterDeterministicKey.serializePrivB58(MainNetParams.get());
    }

    public  String toXpub(){
        return  masterDeterministicKey.serializePubB58(MainNetParams.get());
    }



    public static MasterPrivateKey fromSeed() {

        return fromSeed(generateWordList(),"",MainNetParams.get());
    }

    public static MasterPrivateKey fromSeed(String seed) {
        return fromSeed(StrUtil.split(seed," "),"",MainNetParams.get());
    }

    public static MasterPrivateKey fromSeed(List<String> wordList, String salt)  {
        return fromSeed(wordList,salt, MainNetParams.get());
    }

    public static MasterPrivateKey fromSeed(List<String> wordList, String salt, NetworkParameters parameters){
        if (CollectionUtil.isEmpty(wordList)) {
            wordList = generateWordList();
        }
        DeterministicKey masterPrivateKey = HDKeyDerivation
                .createMasterPrivateKey((Utils.HEX.decode(HexUtil.encodeHexStr(MnemonicCode.toSeed(wordList, salt)))));
        return new MasterPrivateKey(masterPrivateKey);

    }

    public static List<String> generateLongWordList() {
       return generateWordList(24);
    }

    public static List<String> generateWordList() {
      return   generateWordList(12);
    }

    public static List<String> generateWordList(int len) {
        List<String> wordsList;
        Set<Integer> indexSet = new HashSet<>();
        while (indexSet.size() != len) {
            indexSet.add(RandomUtil.randomInt(0, 2048 - 1));
        }

        MnemonicCode mnemonicCode = MnemonicCode.INSTANCE;

        wordsList = indexSet.stream().map(i -> mnemonicCode.getWordList().get(i))
                .collect(Collectors.toList());
        return wordsList;
    }

}
