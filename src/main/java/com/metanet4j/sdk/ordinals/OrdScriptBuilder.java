package com.metanet4j.sdk.ordinals;


import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.metanet4j.base.model.MAP;
import io.bitcoinsv.bitcoinjsv.core.Address;
import io.bitcoinsv.bitcoinjsv.core.ECKeyLite;
import io.bitcoinsv.bitcoinjsv.script.Script;
import io.bitcoinsv.bitcoinjsv.script.ScriptBuilder;
import io.bitcoinsv.bitcoinjsv.script.ScriptChunk;
import io.bitcoinsv.bitcoinjsv.script.ScriptOpCodes;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

public class OrdScriptBuilder {

    public static final String MAP_PREFIX = "1PuQa7K62MiKCtssSLKy1kh56WWU7MtUR5";


    public static Script buildInscription(
            Address destinationAddress,
            byte[] b64File,
            String mediaType,
            ORDMap metaData) {

        ScriptBuilder inscriptionAsm = new ScriptBuilder();

        List<ScriptChunk> chunks = ScriptBuilder.createOutputScript(destinationAddress).getChunks();
        for (ScriptChunk chunk : chunks) {
            inscriptionAsm.addChunk(chunk);
        }

        if (b64File != null && mediaType != null) {
            inscriptionAsm.addChunk(new ScriptChunk(ScriptOpCodes.OP_FALSE, null));

            inscriptionAsm.op(ScriptOpCodes.OP_IF)
                    .data("ord".getBytes())
                    .op(ScriptOpCodes.OP_TRUE)
                    .data(mediaType.getBytes());
            inscriptionAsm.addChunk(new ScriptChunk(ScriptOpCodes.OP_FALSE, null));

            inscriptionAsm.data(b64File)
                    .op(ScriptOpCodes.OP_ENDIF);
        }

        if (MapUtil.isNotEmpty(metaData) && StrUtil.isNotEmpty(metaData.get("app")) && StrUtil.isNotEmpty(metaData.get("type"))) {
            inscriptionAsm
                    .op(ScriptOpCodes.OP_RETURN)
                    .data(MAP_PREFIX.getBytes())
                    .data("SET".getBytes());

            for (Map.Entry<String, String> entry : metaData.entrySet()) {
                if (!"cmd".equals(entry.getKey())) {
                    inscriptionAsm.data(entry.getKey().getBytes());
                    inscriptionAsm.data(entry.getValue().getBytes());
                }
            }
        }

        return inscriptionAsm.build();
    }

    public static class LocalSigner implements Signer {
        ECKeyLite idKey;
    }

    public static class RemoteSigner implements Signer {
        String keyHost;
        String authToken;
    }


    @Data
    @Builder
    public static class Inscription {
        String dataB64;
        String contentType;
    }

    @Data
    public static class ORDMap extends MAP {

    }

}