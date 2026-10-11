package com.metanet4j.sdk;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.HexUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.metanet4j.sdk.utils.ScriptHelper;
import com.metanet4j.base.bsv.BsvAddress;
import com.metanet4j.base.bsv.BsvHashBytes;
import com.metanet4j.base.bsv.BsvNetwork;
import com.metanet4j.bsv.script.OP;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.script.ScriptChunk;
import com.metanet4j.bsv.transaction.Transaction;
import com.metanet4j.bsv.transaction.TransactionInput;
import com.metanet4j.bsv.transaction.TransactionOutput;

import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

public class BobHelper {

    public static Map<String, Object> toBob(String txHex) {
        return toBob(txHex, "|");
    }


    public static Map<String, Object> toBob(String txHex, String spilter) {

        Transaction txBean = Transaction.fromHex(txHex);
        String txId = txBean.id("hex");
        Map<String, Object> txMap = new LinkedHashMap<>();

        txMap.put("tx", new LinkedHashMap<String, Object>() {
            {
                this.put("h", txId);
            }
        });
        JSONArray inArray = new JSONArray();
        List<TransactionInput> inputs = txBean.inputs;

        for (int i = 0; i < inputs.size(); i++) {

            Map<String, Object> inputItem = new LinkedHashMap<>();

            TransactionInput txInput = inputs.get(i);
            Script script = asScript(txInput.unlockingScript);
            List<ScriptChunk> chunks = chunksOf(script);

            inputItem.put("i", i);
            inputItem.put("seq", txInput.sequence);

            inputItem.put("e", new LinkedHashMap<String, Object>() {

                {
                    this.put("h", txidOf(txInput.sourceTXID));
                    this.put("i", txInput.sourceOutputIndex);
                    if (chunks.size() == 2 && ScriptHelper.getPushData(chunks.get(1)) != null) {
                        ScriptChunk scriptChunk = chunks.get(1);
                        this.put("a", addressOfPubKey(ScriptHelper.getPushData(scriptChunk)));
                    }else{
                        //script hash

                        // BOB 的 e.a 取「脚本身份哈希」的自基线路由约定：旧库 Sha256Hash.toString() 即摘要原样，
                        // 而 BsvHashBytes.toString() 会再反转一次，故此处必须用 getBytes()（见 §34 的对照实验）。
                        this.put("a", cn.hutool.core.util.HexUtil.encodeHexStr(
                                BsvHashBytes.of(script.toUint8Array()).getBytes()));
                    }
                }

            });

            inputItem.put("len", chunks.size());
            inputItem.put("tape", toTape(spilter, txId, i, chunks));
            inArray.add(inputItem);
        }


        JSONArray outArray = new JSONArray();
        List<TransactionOutput> outputs = txBean.outputs;

        /**
         * One output corresponds to one tape.
         * One tape contains multiple tape items.
         * One tape item corresponds to one cell.
         * One cell contains multiple cell items.
         */

        for (int i = 0; i < outputs.size(); i++) {
            Map<String, Object> outItem = new LinkedHashMap<>();
            TransactionOutput txOutput = outputs.get(i);
            Script script = txOutput.lockingScript;
            List<ScriptChunk> chunks = chunksOf(script);
            outItem.put("i", i);

            JSONObject e = new JSONObject();
            e.put("v", satoshisOf(txOutput.satoshis));
            try {
                // 与基线一致：仅「OP_FALSE + OP_RETURN」（创世后形态）记 "false"；
                // 纯 OP_RETURN 走地址解析、失败时回退**可读操作码名**（旧 Script.toString()）。
                e.put("a", isOpReturnAfterGenesis(script) ? "false" : addressOfScript(script));
            } catch (RuntimeException ex) {
                e.put("a", opcodeNames(script));
            }
            e.put("i", i);

            outItem.put("e", e);
            outItem.put("len", chunks.size());
            outItem.put("tape", toTape(spilter, txId, i, chunks));
            outArray.add(outItem);

        }
        txMap.put("in", inArray);
        txMap.put("out", outArray);
        txMap.put("lock", txBean.lockTime);
        txMap.put("v", txBean.version);
        return txMap;
    }



    private static List<Map<String, Object>> toTape(String spilter, String txId, int i, List<ScriptChunk> chunks) {
        List<Map<String, Object>> tapeItems = new ArrayList<>();
        List<Map<String, Object>> globalCellItems = new ArrayList<>();


        // Record the index of cell elements in the tape
        int currentCellIndex = 0;
        // Record the index of all cell items in the tape, starting from 0
        int globalCellItemIndex = 0;
        // Record the globalCellItemIndex of the previous cell, which is the last cell item element
        int preCellLastItemIndex = 0;
        // Record the index of the current cell item in the same cell, starting from 0
        int currentCellItemIndex = 0;
        boolean cellAdded = false;
        for (int j = 0; j < chunks.size(); j++) {
            cellAdded = false;


            Map<String, Object> cellItem = new LinkedHashMap<>();

            ScriptChunk scriptChunk = chunks.get(j);
            if (scriptChunk.op==OP.OP_0) {
                cellItem.put("op", scriptChunk.op);
                cellItem.put("ops", OP.name(scriptChunk.op));
                cellItem.put("i", currentCellItemIndex);
                cellItem.put("ii", globalCellItemIndex);
                globalCellItems.add(cellItem);
                cellAdded = true;
            }

            // 注意：此处**不得**再加 `|| op == OP_0`——OP_0(OP_FALSE) 已由上一个 if 分支处理，
            // 加上会造成同一 cell 被加入两次（基线此条件仅为 op > OP_16）。
            if (scriptChunk.op > OP.OP_16) {
                cellItem.put("op", scriptChunk.op);
                cellItem.put("ops", OP.name(scriptChunk.op));
                cellItem.put("i", currentCellItemIndex);
                cellItem.put("ii", globalCellItemIndex);
                globalCellItems.add(cellItem);
                cellAdded = true;

                // begin with op_return or op_false op_return
                if (scriptChunk.op == OP.OP_RETURN && globalCellItemIndex<=1) {
                    Map<String, Object> tapeItem = new LinkedHashMap<>();
                    tapeItem.put("cell", Lists.newArrayList(globalCellItems.subList(preCellLastItemIndex, globalCellItemIndex + 1)));
                    tapeItem.put("i", currentCellIndex);
                    currentCellIndex++;
                    preCellLastItemIndex = globalCellItemIndex + 1;
                    currentCellItemIndex = -1;
                    tapeItems.add(tapeItem);
                }else if(scriptChunk.op == OP.OP_RETURN){

                    Map<String, Object> tapeItem = new LinkedHashMap<>();
                    //过滤掉OP_RETURN
                    tapeItem.put("cell", Lists.newArrayList(globalCellItems.subList(preCellLastItemIndex, globalCellItemIndex + 1)).stream()
                            .filter(o->!String.valueOf(o.get("op")).equals(String.valueOf(OP.OP_RETURN)))
                            .collect(Collectors.toList()));
                    tapeItem.put("i", currentCellIndex);
                    tapeItems.add(tapeItem);
                    currentCellIndex++;

                    // add tape has op_return
                    Map<String, Object> tapeOpReturnItem = new LinkedHashMap<>();
                    // The previous element is OP_FALSE
                    if(chunks.get(j-1).op==OP.OP_0){
                        globalCellItems.get(globalCellItemIndex-1).put("i", 0);
                        globalCellItems.get(globalCellItemIndex).put("i", 1);
                        tapeOpReturnItem.put("cell", Lists.newArrayList(globalCellItems.subList(globalCellItemIndex-1, globalCellItemIndex + 1)));
                        tapeOpReturnItem.put("i", currentCellIndex);
                        tapeItems.add(tapeOpReturnItem);
                    }else{
                        globalCellItems.get(globalCellItemIndex).put("i", 0);
                        tapeOpReturnItem.put("cell", Lists.newArrayList(globalCellItems.subList(globalCellItemIndex, globalCellItemIndex + 1)));
                        tapeOpReturnItem.put("i", currentCellIndex);
                        tapeItems.add(tapeOpReturnItem);
                    }
                    currentCellIndex++;
                    preCellLastItemIndex = globalCellItemIndex + 1;
                    currentCellItemIndex = -1;
                }


            }
            if (ScriptHelper.hasPushData(scriptChunk)) {
                String data = new String(ScriptHelper.getPushData(scriptChunk), Charset.forName("UTF-8"));
                cellItem.put("s", data);
                cellItem.put("b", Base64.encode(ScriptHelper.getPushData(scriptChunk)));
                cellItem.put("h", HexUtil.encodeHexStr(ScriptHelper.getPushData(scriptChunk)));
                cellItem.put("i", currentCellItemIndex);
                cellItem.put("ii", globalCellItemIndex);
                globalCellItems.add(cellItem);
                cellAdded = true;

                if (data.equals(spilter)) {
                    Map<String, Object> tapeItem = new LinkedHashMap<>();
                    //过滤掉"|"分隔符
                    tapeItem.put("cell", Lists.newArrayList(globalCellItems.subList(preCellLastItemIndex,
                            globalCellItemIndex + 1)).stream().filter(o -> !Optional.ofNullable(o.get("s")).orElse("").equals(spilter)).collect(
                            Collectors.toList()));
                    tapeItem.put("i", currentCellIndex);
                    currentCellIndex++;
                    preCellLastItemIndex = globalCellItemIndex + 1;
                    currentCellItemIndex = -1;
                    tapeItems.add(tapeItem);
                }
            }

            currentCellItemIndex++;
            if (cellAdded) {
                globalCellItemIndex++;
            }
            cellAdded = false;

        }
        // Process the last tape
        if (globalCellItemIndex > preCellLastItemIndex) {
            Map<String, Object> tapeItem = new LinkedHashMap<>();
            tapeItem.put("cell", Lists.newArrayList(globalCellItems.subList(preCellLastItemIndex, globalCellItemIndex)));
            tapeItem.put("i", currentCellIndex);
            currentCellIndex++;
            tapeItems.add(tapeItem);
        }

        return tapeItems;
    }


    /** 解锁脚本字段可能是 {@code Script}、{@code byte[]} 或字节列表。 */
    private static Script asScript(Object unlockingScript) {
        if (unlockingScript instanceof Script script) {
            return script;
        }
        if (unlockingScript instanceof byte[] bytes) {
            return Script.fromBinary(bytes);
        }
        if (unlockingScript instanceof java.util.List<?> list) {
            byte[] bytes = new byte[list.size()];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) ((Number) list.get(i)).intValue();
            }
            return Script.fromBinary(bytes);
        }
        return new Script();
    }

    /** 是否 OP_RETURN 输出（首个操作码为 OP_RETURN）。 */
    private static boolean isOpReturn(Script script) {
        List<ScriptChunk> chunks = chunksOf(script);
        return !chunks.isEmpty() && chunks.get(0).op == OP.OP_RETURN;
    }

    /** 由公钥字节推导 P2PKH 地址（主网）。 */
    private static String addressOfPubKey(byte[] pubKey) {
        com.metanet4j.bsv.primitives.PublicKey publicKey =
                com.metanet4j.bsv.primitives.PublicKey.fromDER(toBoxed(pubKey));
        return BsvAddress.fromPublicKey(BsvNetwork.MAINNET, publicKey).toBase58();
    }

    /** 由锁定脚本推导地址：P2PKH / P2SH；其他脚本抛异常由调用方回退。 */
    private static String addressOfScript(Script script) {
        List<ScriptChunk> chunks = chunksOf(script);
        if (chunks.size() == 5
                && chunks.get(0).op == OP.OP_DUP
                && chunks.get(1).op == OP.OP_HASH160
                && chunks.get(2).data != null && chunks.get(2).data.size() == 20
                && chunks.get(3).op == OP.OP_EQUALVERIFY
                && chunks.get(4).op == OP.OP_CHECKSIG) {
            return BsvAddress.fromPubKeyHash(BsvNetwork.MAINNET, toBytes(chunks.get(2).data)).toBase58();
        }
        if (chunks.size() == 3
                && chunks.get(0).op == OP.OP_HASH160
                && chunks.get(1).data != null && chunks.get(1).data.size() == 20
                && chunks.get(2).op == OP.OP_EQUAL) {
            return BsvAddress.fromScriptHash(BsvNetwork.MAINNET, toBytes(chunks.get(1).data)).toBase58();
        }
        throw new IllegalArgumentException("脚本不是可识别的地址类型：" + script.toHex());
    }

    private static List<Double> toBoxed(byte[] data) {
        Double[] values = new Double[data.length];
        for (int i = 0; i < data.length; i++) {
            values[i] = (double) (data[i] & 0xFF);
        }
        return java.util.List.of(values);
    }

    private static byte[] toBytes(List<Integer> values) {
        byte[] bytes = new byte[values.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (int) values.get(i);
        }
        return bytes;
    }

    /** txid 显示序 hex；sourceTXID 可能是 hex 串或字节。 */
    private static String txidOf(Object sourceTXID) {
        if (sourceTXID == null) {
            return BsvHashBytes.ZERO_HASH.toString();
        }
        if (sourceTXID instanceof String text) {
            return text;
        }
        if (sourceTXID instanceof byte[] bytes) {
            return new BsvHashBytes(bytes).toString();
        }
        if (sourceTXID instanceof List<?> list) {
            byte[] bytes = new byte[list.size()];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) ((Number) list.get(i)).intValue();
            }
            return new BsvHashBytes(bytes).toString();
        }
        return sourceTXID.toString();
    }

    /** 金额字段可能是 {@code Undefined}、数字或字符串。 */
    private static long satoshisOf(Object satoshis) {
        if (satoshis instanceof Number number) {
            return number.longValue();
        }
        if (satoshis instanceof String text && !text.isEmpty()) {
            return Long.parseLong(text);
        }
        return 0L;
    }

    /**
     * 按脚本字节自行切分 chunk。
     *
     * <p><b>为何不用 {@code Script.getChunks()}</b>：R1 接入的 bsv-sdk 0.2.0 的
     * {@code Script.parseChunks} 会在遇到 {@code OP_RETURN} 后**停止切分**，
     * 导致其后的 push 全部丢失；而本项目的 BOB tape 与 Ordinals 铭文恰好都是
     * 「OP_RETURN 之后才是载荷」的形态。已登记为上游缺陷（migration-rules §40），
     * 这里做等价规避：按 BIP 脚本编码自行解析，行为对正常脚本与上游一致。
     * 上游修复后可回退为 {@code script.getChunks()}。
     */
    private static List<ScriptChunk> chunksOf(Script script) {
        byte[] raw = script.toUint8Array();
        List<ScriptChunk> out = new java.util.ArrayList<>();
        int i = 0;
        while (i < raw.length) {
            int op = raw[i] & 0xFF;
            i++;
            int len;
            if (op == 0) {
                out.add(new ScriptChunk(OP.OP_0));
                continue;
            } else if (op >= 1 && op <= 75) {
                len = op;
            } else if (op == 76) {
                if (i >= raw.length) break;
                len = raw[i] & 0xFF;
                i++;
            } else if (op == 77) {
                if (i + 1 >= raw.length) break;
                len = (raw[i] & 0xFF) | ((raw[i + 1] & 0xFF) << 8);
                i += 2;
            } else if (op == 78) {
                if (i + 3 >= raw.length) break;
                len = (raw[i] & 0xFF) | ((raw[i + 1] & 0xFF) << 8)
                        | ((raw[i + 2] & 0xFF) << 16) | ((raw[i + 3] & 0xFF) << 24);
                i += 4;
            } else {
                out.add(new ScriptChunk(op));
                continue;
            }
            if (i + len > raw.length) {
                len = raw.length - i;
            }
            List<Integer> data = new java.util.ArrayList<>(len);
            for (int k = 0; k < len; k++) {
                data.add(raw[i + k] & 0xFF);
            }
            i += len;
            out.add(new ScriptChunk(op, data));
        }
        return out;
    }


    /** 是否为「OP_FALSE + OP_RETURN」形态（旧库 isOpReturnAfterGenesis）。 */
    private static boolean isOpReturnAfterGenesis(Script script) {
        List<ScriptChunk> cs = chunksOf(script);
        return cs.size() >= 2 && cs.get(0).op == OP.OP_0 && cs.get(1).op == OP.OP_RETURN;
    }


    /** 把脚本渲染为可读操作码名（去掉新 SDK 的 "OP_" 前缀），与旧库 Script.toString() 对齐。 */
    private static String opcodeNames(Script script) {
        StringBuilder sb = new StringBuilder();
        for (ScriptChunk chunk : chunksOf(script)) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            String name = OP.name(chunk.op);
            if (name == null) {
                name = String.valueOf(chunk.op);
            }
            sb.append(name.startsWith("OP_") ? name.substring(3) : name);
        }
        return sb.toString();
    }

}
