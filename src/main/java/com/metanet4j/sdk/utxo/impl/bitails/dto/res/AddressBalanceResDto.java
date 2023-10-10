package com.metanet4j.sdk.utxo.impl.bitails.dto.res;

import lombok.Data;

@Data
public class AddressBalanceResDto {

    private String address;
    private String scripthash;
    private int confirmed;
    private int unconfirmed;
    private int summary;
    private int count;
}
