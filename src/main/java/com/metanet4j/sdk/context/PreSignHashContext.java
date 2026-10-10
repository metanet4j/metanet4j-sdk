package com.metanet4j.sdk.context;

import com.metanet4j.sdk.PublicKey;
import com.metanet4j.sdk.remote.RemoteSignType;
import com.metanet4j.base.bsv.BsvAmount;
import com.metanet4j.base.bsv.BsvHashBytes;
import lombok.Data;

import java.util.LinkedHashMap;

@Data
public class PreSignHashContext {
    private String signAddress;
    private String sigB64;
    private BsvHashBytes preSignHash;
    private RemoteSignType remoteSignType;
    private BsvAmount fee;
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
        Object hashValue = map.get("preSignHash");
        this.preSignHash = hashValue == null ? null : BsvHashBytes.fromDisplayHex((String) hashValue);
        this.remoteSignType = RemoteSignType.valueOf((String) map.get("remoteSignType"));
        Object feeValue = map.get("fee");
        this.fee = feeValue == null ? null : BsvAmount.valueOf(((Number) feeValue).longValue());
        Object keyValue = map.get("publicKey");
        this.publicKey = keyValue == null ? null : PublicKey.fromHex(keyValue.toString());
        return this;
    }
}
