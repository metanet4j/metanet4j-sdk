package com.metanet4j.sdk.context;

import com.metanet4j.sdk.PublicKey;
import com.metanet4j.sdk.remote.RemoteSignType;
import io.bitcoinsv.bitcoinjsv.core.Coin;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import lombok.Data;

import java.util.LinkedHashMap;

@Data
public class PreSignHashContext {
    private String signAddress;
    private String sigB64;
    private Sha256Hash preSignHash;
    private RemoteSignType remoteSignType;
    private Coin fee;
    private PublicKey publicKey;

    public LinkedHashMap toMap() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("signAddress", signAddress);
        map.put("sigB64", sigB64);
        if (preSignHash != null) {
            map.put("preSignHash", preSignHash.toString());
        }

        map.put("remoteSignType", remoteSignType.name());
        if (fee != null) {
            map.put("fee", fee.getValue());
        }
        if (publicKey != null) {
            map.put("publicKey", publicKey.getPubKeyHex());
        }
        return map;
    }

    public PreSignHashContext fromMap(LinkedHashMap map) {
        this.signAddress = (String) map.get("signAddress");
        this.sigB64 = (String) map.get("sigB64");
        this.preSignHash = Sha256Hash.wrap((String) map.get("preSignHash"));
        this.remoteSignType = RemoteSignType.valueOf((String) map.get("remoteSignType"));
        this.fee = Coin.valueOf((Long) map.get("fee"));
        this.publicKey = PublicKey.fromHex(map.get("publicKey").toString());
        return this;
    }
}
