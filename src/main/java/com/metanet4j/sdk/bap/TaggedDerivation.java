package com.metanet4j.sdk.bap;

import cn.hutool.core.util.HexUtil;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import lombok.Data;

import java.nio.charset.StandardCharsets;

public class TaggedDerivation {

    public static void main(String[] args) {

        DerivationTag tag = new DerivationTag("BAP", "1QBoVKLwVpttbDx9BwFrLMTi2zdmLMVKCd");
        System.out.println(getTaggedDerivation(tag));
    }

    public static String getTaggedDerivation(DerivationTag tag) {
        String labelHex = HexUtil.encodeHexStr(Sha256Hash.hash(tag.getLabel().getBytes(StandardCharsets.UTF_8)));
        String idHex = HexUtil.encodeHexStr(Sha256Hash.hash(tag.getId().getBytes(StandardCharsets.UTF_8)));
        long labelNumber = Long.parseLong(labelHex.substring(labelHex.length() - 8), 16) % (long) Math.pow(2, 31);
        long idNumber = Long.parseLong(idHex.substring(idHex.length() - 8), 16) % (long) Math.pow(2, 31);
        //"M/424150H/0H/0H/0H/0H/0H";
        return String.format("M/44H/236H/218H/%d/%d", labelNumber, idNumber);
    }

    @Data
    public static class DerivationTag {
        String label;
        String id;

        public DerivationTag(String label, String id) {
            this.label = label;
            this.id = id;
        }
    }

}
