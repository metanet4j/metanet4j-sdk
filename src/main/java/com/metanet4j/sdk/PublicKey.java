
package com.metanet4j.sdk;

import io.bitcoinsv.bitcoinjsv.core.ECKeyLite;
import io.bitcoinsv.bitcoinjsv.core.Utils;

public class PublicKey {


    private ECKeyLite key;

    private PublicKey(ECKeyLite key){
       this.key = key;
    }

    public static PublicKey fromHex(String encoded) {
       byte[] pubkeyBytes = Utils.HEX.decode(encoded);

       return new PublicKey(ECKeyLite.fromPublicOnly(pubkeyBytes));
    }

    public static PublicKey fromBytes(byte[] pubkeyBytes){
        return new PublicKey(ECKeyLite.fromPublicOnly(pubkeyBytes));
    }

    public byte[] getPubKeyHash(){
        return key.getPubKeyHash();
    }

    public byte[] getPubKeyBytes(){
        return key.getPubKey();
    }

    public String getPubKeyHex(){
        return key.getPublicKeyAsHex();
    }

    public ECKeyLite getKey() {
        return key;
    }
}
