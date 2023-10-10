package com.metanet4j.sdk.utxo.impl.gorillapool.dto.res;

import lombok.Data;

@Data
public class Txo {
    private String txid;
    private Integer vout;
    private Integer satoshis;
    private Integer accSats;
    private String lock;
    private String script;
    private String spend;
    private String origin;
    private int height;
    private int idx;
    private boolean listing;
}
