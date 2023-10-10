package com.metanet4j.sdk;

import cn.hutool.core.io.FileUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Charsets;
import com.metanet4j.base.util.JacksonUtil;
import com.metanet4j.sdk.crypto.MasterPrivateKey;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;

public class TestData {

    // prd test
    public static String xprv =
            "your xprv";


    public static MasterPrivateKey masterPrivateKey = MasterPrivateKey.fromXprv(TestData.xprv);



    public static String utxoAddress = "1DBzoExEs6StRztgudb82UviC1Wr12DpQk";

    public List<LinkedHashMap> parseRoot() {
        return JacksonUtil
                .string2Obj(FileUtil.readString("bap_root.json", Charsets.UTF_8),
                        new TypeReference<List<LinkedHashMap>>(){});
    }

    public List<LinkedHashMap> parseId() {
        return JacksonUtil
                .string2Obj(FileUtil.readString("bap_id.json", Charsets.UTF_8),
                        new TypeReference<List<LinkedHashMap>>(){});
    }

    public List<LinkedHashMap> parseBob() {
        return JacksonUtil
                .string2Obj(FileUtil.readString("bsocial_bob.json", Charsets.UTF_8),
                        new TypeReference<List<LinkedHashMap>>(){});
    }

    @Test
    public void testParse(){
        List<LinkedHashMap> parseRoot = parseRoot();
        System.out.println(parseRoot);

        List<LinkedHashMap> parseId = parseId();
        System.out.println(parseId);

        List<LinkedHashMap> parseBob = parseBob();
        System.out.println(parseBob);
    }

    @Test
    public void testContentType(){

        String mimeType = FileUtil.getMimeType("bsv1.jpeg");
        System.out.println(mimeType);
        System.out.println(mimeType);
    }


}
