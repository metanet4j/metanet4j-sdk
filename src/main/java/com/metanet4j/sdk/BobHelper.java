package com.metanet4j.sdk;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.HexUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.metanet4j.sdk.utils.ScriptHelper;
import io.bitcoinsv.bitcoinjsv.bitcoin.api.base.TxInput;
import io.bitcoinsv.bitcoinjsv.bitcoin.api.base.TxOutput;
import io.bitcoinsv.bitcoinjsv.bitcoin.bean.base.TxBean;
import io.bitcoinsv.bitcoinjsv.core.BasicAddress;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import io.bitcoinsv.bitcoinjsv.core.Utils;
import io.bitcoinsv.bitcoinjsv.params.MainNetParams;
import io.bitcoinsv.bitcoinjsv.script.Script;
import io.bitcoinsv.bitcoinjsv.script.ScriptChunk;
import io.bitcoinsv.bitcoinjsv.script.ScriptOpCodes;
import io.bitcoinsv.bitcoinjsv.script.ScriptUtils;
import io.bitcoinsv.bitcoinjsv.script.interpreter.ScriptExecutionException;

import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

public class BobHelper {

    public static Map<String, Object> toBob(String txHex) {
        return toBob(txHex, "|");
    }


    public static Map<String, Object> toBob(String txHex, String spilter) {

        TxBean txBean = new TxBean(HexUtil.decodeHex(txHex));
        String txId = txBean.calculateHash().toString();
        Map<String, Object> txMap = new LinkedHashMap<>();

        txMap.put("tx", new LinkedHashMap<String, Object>() {
            {
                this.put("h", txId);
            }
        });
        JSONArray inArray = new JSONArray();
        List<TxInput> inputs = txBean.getInputs();

        for (int i = 0; i < inputs.size(); i++) {

            Map<String, Object> inputItem = new LinkedHashMap<>();

            TxInput txInput = inputs.get(i);
            Script script = new Script(txInput.getScriptBytes());
            List<ScriptChunk> chunks = script.getChunks();

            inputItem.put("i", i);
            inputItem.put("seq", txInput.getSequenceNumber());

            inputItem.put("e", new LinkedHashMap<String, Object>() {

                {
                    this.put("h", txInput.getOutpoint().getHashAsString());
                    this.put("i", txInput.getOutpoint().getIndex());
                    if (chunks.size() == 2) {
                        try {
                            ScriptChunk scriptChunk = chunks.get(1);
                            this.put("a", new BasicAddress(MainNetParams.get(),
                                    Utils.sha256hash160(scriptChunk.data())).toBase58());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }else{
                        //script hash

                        this.put("a", Sha256Hash.of(script.getProgram()).toString());
                    }
                }

            });

            inputItem.put("len", chunks.size());
            inputItem.put("tape", toTape(spilter, txId, i, chunks));
            inArray.add(inputItem);
        }


        JSONArray outArray = new JSONArray();
        List<TxOutput> outputs = txBean.getOutputs();

        /**
         * One output corresponds to one tape.
         * One tape contains multiple tape items.
         * One tape item corresponds to one cell.
         * One cell contains multiple cell items.
         */

        for (int i = 0; i < outputs.size(); i++) {
            Map<String, Object> outItem = new LinkedHashMap<>();
            TxOutput txOutput = outputs.get(i);
            Script script = new Script(txOutput.getScriptBytes());
            List<ScriptChunk> chunks = script.getChunks();
            outItem.put("i", i);

            JSONObject e = new JSONObject();
            e.put("v", txOutput.getValue().getValue());
            try {
                e.put("a", script.isOpReturnAfterGenesis() ? "false" :
                        ScriptUtils.getToAddress(script, MainNetParams.get()).toBase58());
            } catch (ScriptExecutionException ex) {
                e.put("a", script.toString());
            }
            e.put("i", i);

            outItem.put("e", e);
            outItem.put("len", chunks.size());
            outItem.put("tape", toTape(spilter, txId, i, chunks));
            outArray.add(outItem);

        }
        txMap.put("in", inArray);
        txMap.put("out", outArray);
        txMap.put("lock", txBean.getLockTime());
        txMap.put("v", txBean.getVersion());
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
        for (int j = 0; j < chunks.size(); j++) {


            Map<String, Object> cellItem = new LinkedHashMap<>();

            ScriptChunk scriptChunk = chunks.get(j);
            if (scriptChunk.opcode ==ScriptOpCodes.OP_FALSE) {
                cellItem.put("op", scriptChunk.opcode);
                cellItem.put("ops", "OP_" + ScriptOpCodes.getOpCodeName(scriptChunk.opcode));
                cellItem.put("i", currentCellItemIndex);
                cellItem.put("ii", globalCellItemIndex);
                globalCellItems.add(cellItem);
            }

            if (scriptChunk.isOpCode()) {
                cellItem.put("op", scriptChunk.opcode);
                cellItem.put("ops", "OP_" + ScriptOpCodes.getOpCodeName(scriptChunk.opcode));
                cellItem.put("i", currentCellItemIndex);
                cellItem.put("ii", globalCellItemIndex);
                globalCellItems.add(cellItem);

                // begin with op_return or op_false op_return
                if (scriptChunk.opcode == ScriptOpCodes.OP_RETURN && globalCellItemIndex<=1) {
                    Map<String, Object> tapeItem = new LinkedHashMap<>();
                    tapeItem.put("cell", Lists.newArrayList(globalCellItems.subList(preCellLastItemIndex, globalCellItemIndex + 1)));
                    tapeItem.put("i", currentCellIndex);
                    currentCellIndex++;
                    preCellLastItemIndex = globalCellItemIndex + 1;
                    currentCellItemIndex = -1;
                    tapeItems.add(tapeItem);
                }else if(scriptChunk.opcode == ScriptOpCodes.OP_RETURN && globalCellItemIndex>1){

                    Map<String, Object> tapeItem = new LinkedHashMap<>();
                    //过滤掉OP_RETURN
                    tapeItem.put("cell", Lists.newArrayList(globalCellItems.subList(preCellLastItemIndex, globalCellItemIndex + 1)).stream()
                            .filter(o->!String.valueOf(o.get("op")).equals(String.valueOf(ScriptOpCodes.OP_RETURN)))
                            .collect(Collectors.toList()));
                    tapeItem.put("i", currentCellIndex);
                    tapeItems.add(tapeItem);
                    currentCellIndex++;

                    // add tape has op_return
                    Map<String, Object> tapeOpReturnItem = new LinkedHashMap<>();
                    // The previous element is OP_FALSE
                    if(chunks.get(j-1).opcode==ScriptOpCodes.OP_FALSE){
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
                    if (scriptChunk.data.length() < Integer.MAX_VALUE) {
                        String data = new String(scriptChunk.data.data(), Charset.forName("UTF-8"));
                        cellItem.put("s", data);
                        cellItem.put("b", Base64.encode(scriptChunk.data.data()));
                        cellItem.put("h", HexUtil.encodeHexStr(scriptChunk.data.data()));
                        cellItem.put("i", currentCellItemIndex);
                        cellItem.put("ii", globalCellItemIndex);
                        globalCellItems.add(cellItem);

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
                    } else {
                        cellItem.put("f", txId + ".out." + i + "." + j);
                        cellItem.put("i", currentCellItemIndex);
                        cellItem.put("ii", globalCellItemIndex);
                        globalCellItems.add(cellItem);
                    }

            }

            currentCellItemIndex++;
            globalCellItemIndex++;

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

}
