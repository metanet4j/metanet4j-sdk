package com.metanet4j.sdk.utxo.impl.bitails.dto.res;

import java.util.List;
import lombok.Data;

@Data
public class AddressUnspentResDto {

    private String address;
    private String scripthash;
    private List<Unspent> unspent;

    @Data
    public static class Unspent {

        private String txid;
        private int vout;
        private int satoshis;
        private int time;
        private int confirmations;
    }

}
