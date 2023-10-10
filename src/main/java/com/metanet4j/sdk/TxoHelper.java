package com.metanet4j.sdk;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.HexUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.metanet4j.sdk.utils.ScriptHelper;
import io.bitcoinsv.bitcoinjsv.bitcoin.api.base.TxInput;
import io.bitcoinsv.bitcoinjsv.bitcoin.api.base.TxOutput;
import io.bitcoinsv.bitcoinjsv.bitcoin.bean.base.TxBean;
import io.bitcoinsv.bitcoinjsv.core.BasicAddress;
import io.bitcoinsv.bitcoinjsv.core.Utils;
import io.bitcoinsv.bitcoinjsv.params.MainNetParams;
import io.bitcoinsv.bitcoinjsv.script.Script;
import io.bitcoinsv.bitcoinjsv.script.ScriptChunk;
import io.bitcoinsv.bitcoinjsv.script.ScriptOpCodes;
import io.bitcoinsv.bitcoinjsv.script.ScriptUtils;
import io.bitcoinsv.bitcoinjsv.script.interpreter.ScriptExecutionException;

import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TxoHelper {

    public static Map<String,Object> toTxo(String s){
        TxBean txBean =new TxBean(HexUtil.decodeHex(s));
        String txId = txBean.calculateHash().toString();
        Map<String,Object> txMap =new LinkedHashMap<>();

        txMap.put("tx",new LinkedHashMap<String,Object>(){
            {
                this.put("h",txId);
            }

        });
        JSONArray inArray =new JSONArray();
        List<TxInput> inputs = txBean.getInputs();
        for (int i = 0; i <inputs.size() ; i++) {
            Map<String,Object> inputItem =new LinkedHashMap<>();
            TxInput txInput = inputs.get(i);
            Script script = new Script(txInput.getScriptBytes());

            List<ScriptChunk> chunks = script.getChunks();
            inputItem.put("i",i);
            inputItem.put("seq",txInput.getSequenceNumber());

            inputItem.put("e",new LinkedHashMap<String,Object>(){

                {
                    this.put("h",txInput.getOutpoint().getHashAsString());
                    this.put("i",txInput.getOutpoint().getIndex());
                    if(chunks.size()==2){ //P2PKH input script
                        try {
                            ScriptChunk scriptChunk = chunks.get(1);
                            this.put("a",new BasicAddress(MainNetParams.get(),
                                    Utils.sha256hash160(scriptChunk.data())).toBase58());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }else{
                        this.put("a",script.toString());
                    }
                }

            } );

            inputItem.put("len",chunks.size());
            for (int j = 0; j <chunks.size() ; j++) {
                ScriptChunk scriptChunk = chunks.get(j);
                if(ScriptHelper.hasPushData(scriptChunk)){
                    inputItem.put("s"+j,new String(scriptChunk.data.data(),Charset.forName("UTF-8")));
                    inputItem.put("b"+j, Base64.encode(scriptChunk.data.data()));
                    inputItem.put("h"+j, HexUtil.encodeHexStr(scriptChunk.data.data()));
                }

                if(scriptChunk.isPushData()&&scriptChunk.opcode==0){
                    inputItem.put("o"+j, "OP_"+ ScriptOpCodes.getOpCodeName(scriptChunk.opcode) );
                }

                if(scriptChunk.isOpCode()){
                    inputItem.put("o"+j, "OP_"+ScriptOpCodes.getOpCodeName(scriptChunk.opcode) );
                }
            }
            inArray.add(inputItem);
        }


        JSONArray outArray =new JSONArray();
        List<TxOutput> outputs = txBean.getOutputs();
        for (int i = 0; i <outputs.size() ; i++) {
            Map<String,Object> outItem =new LinkedHashMap<>();
            TxOutput txOutput =  outputs.get(i);
            Script script =new Script(txOutput.getScriptBytes());
            List<ScriptChunk> chunks = script.getChunks();
            outItem.put("i",i);

            JSONObject e =new JSONObject();
            e.put("v",txOutput.getValue().getValue());
            try {
                e.put("a",script.isOpReturnAfterGenesis()?"false":
                        ScriptUtils.getToAddress(script, MainNetParams.get()).toBase58());
            } catch (ScriptExecutionException ex) {
                e.put("a",script.toString());
            }
            e.put("i",i);

            outItem.put("e",e);
            outItem.put("len",chunks.size());


            for (int j = 0; j <chunks.size() ; j++) {
                ScriptChunk scriptChunk = chunks.get(j);

                if (ScriptHelper.hasPushData(scriptChunk)) {
                    int length = scriptChunk.data.data().length;
                    if(scriptChunk.data.length()<Integer.MAX_VALUE){
                        outItem.put("s"+j,new String(scriptChunk.data.data(), Charset.forName("UTF-8")));
                        outItem.put("b"+j, Base64.encode(scriptChunk.data.data()));
                        outItem.put("h"+j, HexUtil.encodeHexStr(scriptChunk.data.data()));
                    }else{
                        outItem.put("f"+j, txId+".out"+i+"."+j);
                    }

                }
                if(scriptChunk.isPushData()&&scriptChunk.opcode==0){
                    outItem.put("o"+j, "OP_"+ ScriptOpCodes.getOpCodeName(scriptChunk.opcode) );
                }

                if(scriptChunk.isOpCode()){
                    outItem.put("o"+j, "OP_"+ScriptOpCodes.getOpCodeName(scriptChunk.opcode) );
                }

            }
            outArray.add(outItem);

        }
        txMap.put("in",inArray);
        txMap.put("out",outArray);
        txMap.put("lock",txBean.getLockTime());
        txMap.put("v",txBean.getVersion());
        return txMap;
    }


}
