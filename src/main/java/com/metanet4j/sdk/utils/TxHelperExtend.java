package com.metanet4j.sdk.utils;

import cn.hutool.core.codec.Base64;
import io.bitcoinsv.bitcoinjsv.core.AddressLite;
import io.bitcoinsv.bitcoinjsv.msg.protocol.TransactionOutPoint;
import io.bitcoinsv.bitcoinjsv.msg.protocol.TransactionOutput;
import io.bitcoinsv.bitcoinjsv.msg.protocol.TxHelper;
import io.bitcoinsv.bitcoinjsv.params.MainNetParams;
import io.bitcoinsv.bitcoinjsv.script.Script;
import io.bitcoinsv.bitcoinjsv.script.ScriptChunk;
import io.bitcoinsv.bitcoinjsv.script.interpreter.ScriptExecutionException;
import io.bitcoinsv.bitcoinjsv.temp.KeyBag;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bitcoinsv.bitcoinjsv.script.ScriptOpCodes.*;

public class TxHelperExtend extends TxHelper {

    private static final int ADDRESS_LENGTH = 20;

    @Nullable
    public static RedeemDataExtend getConnectedRedeemDataExtend(TransactionOutPoint transactionOutPoint, KeyBag keyBag) throws ScriptExecutionException {
        TransactionOutput connectedOutput = transactionOutPoint.getConnectedOutput();
        checkNotNull(connectedOutput, "Input is not connected so cannot retrieve key");
        Script connectedScript = connectedOutput.getScriptPubKey();

        RedeemDataExtend redeemData = null;
        try {
            byte[] bytes = getPubKeyHashByOrdinalScript(connectedScript);
            if (bytes == null || bytes.length == 0) {

            } else {
                redeemData = RedeemDataExtend.of(keyBag.findKeyFromPubHash(bytes), connectedScript);
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (redeemData == null) {
            return RedeemDataExtend.of(TxHelper.getConnectedRedeemData(transactionOutPoint, keyBag));
        } else {
            return redeemData;
        }

    }

    public static boolean isSentToOrdinals(Script connectedScript) {
        try {
            List<ScriptChunk> chunks = connectedScript.getChunks();
            return
                    chunks.get(0).equalsOpCode(OP_DUP) &&
                            chunks.get(1).equalsOpCode(OP_HASH160) &&
                            chunks.get(2).data.length() == ADDRESS_LENGTH &&
                            chunks.get(3).equalsOpCode(OP_EQUALVERIFY) &&
                            chunks.get(4).equalsOpCode(OP_CHECKSIG) &&
                            chunks.get(5).equalsOpCode(OP_FALSE) &&
                            chunks.get(6).equalsOpCode(OP_IF) &&
                            Arrays.equals(chunks.get(7).data(), "ord".getBytes()) &&
                            chunks.get(8).equalsOpCode(OP_TRUE) &&
                            chunks.get(9).isPushData() &&
                            chunks.get(10).equalsOpCode(OP_FALSE) &&
                            chunks.get(11).isPushData() &&
                            chunks.get(12).equalsOpCode(OP_ENDIF);
        } catch (Exception e) {
            return false;
        }
    }

    public static byte[] getPubKeyHashByOrdinalScript(String scriptB64) {
        Script script = new Script(Base64.decode(scriptB64));
        return getPubKeyHashByOrdinalScript(script);
    }

    public static byte[] getPubKeyHashByOrdinalScript(Script script) {

        boolean b = isSentToOrdinals(script);
        if (!b) {
            return new byte[0];
        }
        byte[] pubKeyHash = script.getChunks().get(2).data();
        return pubKeyHash;
    }

    public static String getAddressByOrdinalScript(String scriptB64) {
        Script script = new Script(Base64.decode(scriptB64));
        return getAddressByOrdinalScript(script);
    }

    public static String getAddressByOrdinalScript(Script script) {
        return new AddressLite(MainNetParams.get(), getPubKeyHashByOrdinalScript(script)).toBase58();
    }
}
