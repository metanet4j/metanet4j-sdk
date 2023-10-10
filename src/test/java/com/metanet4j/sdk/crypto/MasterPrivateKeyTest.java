package com.metanet4j.sdk.crypto;

import cn.hutool.core.util.StrUtil;
import org.junit.Test;

import java.util.List;

public class MasterPrivateKeyTest {

    @Test
    public void testGenerateWordList(){
        List<String> wordList = MasterPrivateKey.generateLongWordList();
        System.out.println(StrUtil.join(" ",wordList));
    }

    @Test
    public void testFromWordList(){
        MasterPrivateKey masterPrivateKey = MasterPrivateKey.fromSeed();
        System.out.println(masterPrivateKey.toXprv());

    }

}
