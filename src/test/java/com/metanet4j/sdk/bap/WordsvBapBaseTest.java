package com.metanet4j.sdk.bap;

import cn.hutool.core.codec.Base64;
import com.metanet4j.sdk.WordsvTestData;
import com.metanet4j.sdk.crypto.Ecies;
import org.junit.Test;

import java.nio.charset.Charset;

public class WordsvBapBaseTest {


    @Test
    public void testServerBapBase() {
        BapBase bapBase = BapBase.fromOnlyMasterPrivateKey(WordsvTestData.serverMasterPrivateKey);


    }

    @Test
    public void testClientBapBase() {
        BapBase serverBapBase = BapBase.fromOnlyMasterPrivateKey(WordsvTestData.serverMasterPrivateKey);
        BapBase aliceBapBase = BapBase.fromOnlyMasterPrivateKey(WordsvTestData.aliceMasterPrivateKey);

        try {
            String encrypt = Ecies.encrypt(WordsvTestData.aliceXprv, serverBapBase.getEncryptKey(), null);
            System.out.println(encrypt);
            byte[] decrypt = Ecies.decrypt(Base64.decode(encrypt), serverBapBase.getEncryptKey(), null);
            String s = new String(decrypt, Charset.forName("UTF-8"));
            System.out.println(s);
            System.out.println(serverBapBase.decrypt(encrypt));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
