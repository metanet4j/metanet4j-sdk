package com.metanet4j.sdk.utils;

import cn.hutool.core.codec.Base64;
import io.bitcoinsv.bitcoinjsv.core.AddressLite;
import io.bitcoinsv.bitcoinjsv.params.MainNetParams;
import io.bitcoinsv.bitcoinjsv.script.Script;
import io.bitcoinsv.bitcoinjsv.script.ScriptChunk;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ScriptHelper {
    public static boolean hasPushData(ScriptChunk scriptChunk) {
        return scriptChunk.data != null && scriptChunk.data.data() != null && scriptChunk.isPushData() && scriptChunk.opcode != 0;
    }

    public static String getAddressByOrdinalScript(String scriptB) {
        Script script = new Script(Base64.decode(scriptB));
        byte[] pubKeyHash = script.getChunks().get(2).data();
        return new AddressLite(MainNetParams.get(), pubKeyHash).toBase58();
    }
}
