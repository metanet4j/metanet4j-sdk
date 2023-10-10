package com.metanet4j.sdk.utxo.impl.gorillapool.dto.res;

import com.metanet4j.base.model.MAP;
import lombok.Data;

import java.util.List;

@Data
public class Inscription {
    private String txid;
    private int vout;
    private String outpoint;
    private String origin;
    private int height;
    private int idx;
    private String lock;
    private List<Sigma> SIGMA;
    private boolean listing;
    private boolean bsv20;
    private int num;
    private int id;
    private File file;
    private MAP MAP;

    @Data
    public static class Sigma {
        private int vin;
        private boolean valid;
        private String address;
        private String algorithm;
        private String signature;
    }

    @Data
    public static class File {
        private String hash;
        private int size;
        private String type;
    }
}
