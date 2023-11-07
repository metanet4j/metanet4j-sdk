package com.metanet4j.sdk.utxo.impl.bitails.dto.res;

import lombok.Data;

import java.util.List;

@Data
public class AddressUnspentResDto {

    private String address;
    private String scripthash;
    private List<Unspent> unspent;

    @Data
    public static class Unspent {

        private String txid;
        private Integer vout;
        private Integer satoshis;
        private Integer time;
        private Integer blockheight;
        private Integer confirmations;
    }

}
