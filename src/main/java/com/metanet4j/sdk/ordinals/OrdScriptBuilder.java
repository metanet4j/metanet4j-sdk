package com.metanet4j.sdk.ordinals;


import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.metanet4j.base.model.MAP;
import com.metanet4j.bsv.script.OP;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.script.templates.P2PKH;
import com.metanet4j.sdk.address.AddressEnhance;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

public class OrdScriptBuilder {

    public static final String MAP_PREFIX = "1PuQa7K62MiKCtssSLKy1kh56WWU7MtUR5";


    /**
     * 构造 Ordinals 铭文脚本：P2PKH 前缀 + OP_0 OP_IF "ord" OP_1 &lt;媒体类型&gt; OP_0 &lt;内容&gt; OP_ENDIF
     * （有 MAP 元数据时再接 OP_RETURN 段）。
     */
    public static Script buildInscription(
            AddressEnhance destinationAddress,
            byte[] b64File,
            String mediaType,
            ORDMap metaData) {

        Script inscriptionAsm = new Script();
        for (var chunk : new P2PKH().lock(destinationAddress.getHash160()).getChunks()) {
            inscriptionAsm.writeOpCode(chunk.op);
            if (chunk.data != null) {
                inscriptionAsm.writeBin(chunk.data);
            }
        }

        if (b64File != null && mediaType != null) {
            inscriptionAsm.writeOpCode(OP.OP_0)
                    .writeOpCode(OP.OP_IF)
                    .writeBin(toBoxed("ord".getBytes()))
                    .writeOpCode(OP.OP_1)
                    .writeBin(toBoxed(mediaType.getBytes()))
                    .writeOpCode(OP.OP_0)
                    .writeBin(toBoxed(b64File))
                    .writeOpCode(OP.OP_ENDIF);
        }

        if (MapUtil.isNotEmpty(metaData) && StrUtil.isNotEmpty(metaData.get("app"))
                && StrUtil.isNotEmpty(metaData.get("type"))) {
            inscriptionAsm.writeOpCode(OP.OP_RETURN)
                    .writeBin(toBoxed(MAP_PREFIX.getBytes()))
                    .writeBin(toBoxed("SET".getBytes()));

            for (Map.Entry<String, String> entry : metaData.entrySet()) {
                if (!"cmd".equals(entry.getKey())) {
                    inscriptionAsm.writeBin(toBoxed(entry.getKey().getBytes()));
                    inscriptionAsm.writeBin(toBoxed(entry.getValue().getBytes()));
                }
            }
        }
        return inscriptionAsm;
    }

    private static List<Integer> toBoxed(byte[] data) {
        List<Integer> out = new java.util.ArrayList<>(data.length);
        for (byte b : data) {
            out.add(b & 0xFF);
        }
        return out;
    }

    public static class LocalSigner implements Signer {
        /** 本地签名私钥。 */
        com.metanet4j.bsv.primitives.PrivateKey idKey;
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