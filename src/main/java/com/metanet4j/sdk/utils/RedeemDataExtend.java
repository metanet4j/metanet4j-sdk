package com.metanet4j.sdk.utils;

import io.bitcoinsv.bitcoinjsv.core.ECKey;
import io.bitcoinsv.bitcoinjsv.script.Script;
import io.bitcoinsv.bitcoinjsv.temp.RedeemData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RedeemDataExtend {

    public final Script redeemScript;
    public final List<ECKey> keys;

    private RedeemDataExtend(List<ECKey> keys, Script redeemScript) {
        this.redeemScript = redeemScript;
        List<ECKey> sortedKeys = new ArrayList<ECKey>(keys);
        Collections.sort(sortedKeys, ECKey.PUBKEY_COMPARATOR);
        this.keys = sortedKeys;
    }

    public static RedeemDataExtend of(List<ECKey> keys, Script redeemScript) {
        return new RedeemDataExtend(keys, redeemScript);
    }

    public static RedeemDataExtend of(RedeemData redeemData) {
        return new RedeemDataExtend(redeemData.keys, redeemData.redeemScript);
    }

    /**
     * Creates RedeemData for pay-to-address or pay-to-pubkey input. Provided key is a single private key needed
     * to spend such inputs and provided program should be a proper CHECKSIG program.
     */
    public static RedeemDataExtend of(ECKey key, Script program) {
        return key != null ? new RedeemDataExtend(Collections.singletonList(key), program) : null;
    }

    /**
     * Returns the first key that has private bytes
     */
    public ECKey getFullKey() {
        for (ECKey key : keys)
            if (key.hasPrivKey())
                return key;
        return null;
    }
}
