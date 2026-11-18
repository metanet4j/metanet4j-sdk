package com.metanet4j.sdk.transaction;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UnspendableDataSig {
    private String sig;
    private String signatureAddress;


}
