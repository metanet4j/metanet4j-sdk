package com.metanet4j.sdk.transcation;

import cn.hutool.core.util.HexUtil;
import com.alibaba.fastjson.JSONObject;
import com.metanet4j.sdk.BobHelper;
import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.TxoHelper;
import com.metanet4j.sdk.bap.BapBase;
import com.metanet4j.sdk.bap.RemoteBapBase;
import com.metanet4j.sdk.utxo.impl.bitails.BitailsClient;
import io.bitcoinsv.bitcoinjsv.core.Address;
import io.bitcoinsv.bitcoinjsv.core.AddressLite;
import io.bitcoinsv.bitcoinjsv.core.Utils;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.msg.protocol.TransactionInput;
import io.bitcoinsv.bitcoinjsv.msg.protocol.TransactionOutput;
import io.bitcoinsv.bitcoinjsv.msg.protocol.TxHelper;
import io.bitcoinsv.bitcoinjsv.params.Net;
import io.bitcoinsv.bitcoinjsv.script.Script;
import io.bitcoinsv.bitcoinjsv.script.ScriptChunk;
import io.bitcoinsv.bitcoinjsv.script.ScriptUtils_legacy;
import io.bitcoinsv.bitcoinjsv.script.ScriptVerifyFlag;
import io.bitcoinsv.bitcoinjsv.script.interpreter.ScriptExecutionException;
import io.bitcoinsv.bitcoinjsv.temp.KeyBag;
import io.bitcoinsv.bitcoinjsv.temp.RedeemData;
import org.junit.Before;

import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bitcoinsv.bitcoinjsv.script.ScriptOpCodes.getOpCodeName;

public class TransactionContextTest {
    public BapBase bapBase;
    public RemoteBapBase remoteBapBase;
    public AddressLite paymentAddress;
    public Address ordAddress;
    public Address changeAddress;
    public KeyBag keyBag;
    public long feePerKb = 50L;


    @Before
    public void before() {
        bapBase = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        remoteBapBase = new RemoteBapBase(bapBase.getRootAddress(), bapBase.getPreviouAddress(), bapBase.getCurrentAddress(), bapBase.getPayAccountAddress(), bapBase.getOrdAddress(), bapBase.getAppName());
        paymentAddress = bapBase.getPayAccountAddress();
        ordAddress = bapBase.getOrdAddress();
        changeAddress = new Address(paymentAddress);
        keyBag = new BapBase.BapProviderKeyBag(bapBase);
    }

    public static void parseTx(Transaction transaction) {
        String s = HexUtil.encodeHexStr(transaction.bitcoinSerialize());
        parseTx(s);
    }

    public static void parseTx(String rawTx) {
        Transaction transaction = new Transaction(Net.MAINNET, HexUtil.decodeHex(rawTx));

        Map<String, Object> bob = BobHelper.toBob(rawTx);
        Map<String, Object> txo = TxoHelper.toTxo(rawTx);

    }

    private static void printTx(Transaction transaction) {
        for (TransactionOutput transactionOutput : transaction.getOutputs()) {
            if (transactionOutput.getScriptPubKey().isOpReturnAfterGenesis()) {
                Script metaidScript = new Script(transaction.getOutputs().get(1).getScriptBytes());
                System.out.println();
                metaidScript.getChunks().stream().forEach(o -> {

                    System.out.print("opcode:" + o.opcode);
                    if (o.data != null && o.data.data() != null) {
                        System.out.print(",data:" + new String(o.data.data()));
                    }
                    System.out.println("-----------");


                });
                System.out.println(transaction.getMessageSize());
                byte[] bytes = transaction.serialize();
            }

        }
    }

    public static String scriptToString(Script scriptPubKey) {
        final StringBuilder buf = new StringBuilder();
        for (ScriptChunk chunk : scriptPubKey.getChunks()) {
            if (buf.length() > 0) {
                buf.append(" ");
            }
            if (chunk.isOpCode()) {
                buf.append(getOpCodeName(chunk.opcode));
            } else if (chunk.data != null) {
                // Data chunk
                buf.append("0x")
                        .append(Integer.toString(chunk.opcode, 16)).append(" 0x")
                        .append(Utils.HEX.encode(chunk.data()));
            } else {
                buf.append(chunk.toString());
            }
        }
        return buf.toString();
    }


    private static void addOutputs(final Transaction outputTransaction, final KeyBag bag) throws ScriptExecutionException {
        int numInputs = outputTransaction.getInputs().size();
        for (int i = 0; i < numInputs; i++) {
            TransactionInput txIn = outputTransaction.getInput(i);
            Script scriptPubKey = txIn.getConnectedOutput().getScriptPubKey();
            RedeemData redeemData = TxHelper.getConnectedRedeemData(txIn, bag);
            checkNotNull(redeemData, "Transaction exists in wallet that we cannot redeem: %s", txIn.getOutpoint().getHash());
            txIn.setScriptSig(scriptPubKey.createEmptyInputScript(redeemData.keys.get(0).getPubKey(), redeemData.redeemScript));
        }
    }

    public void correctlySpends(Transaction transaction) {
        TransactionInput input = transaction.getInputs().get(0);
        //verify
        ScriptUtils_legacy.correctlySpends(input.getScriptSig(), transaction, 0,
                input.getConnectedOutput().getScriptPubKey(), input.getConnectedOutput().getValue(), ScriptVerifyFlag.GENESIS_SET);


    }

    public static String broadcast(Transaction transaction) {
        String s = HexUtil.encodeHexStr(transaction.bitcoinSerialize());
        String broadcast = TransactionBuilder.broadcast(s, raw -> BitailsClient.broadcast(raw));
        String txId = JSONObject.parseObject(broadcast).getString("txid");
        System.out.println("you can view your tx on whatsonchain:" + "https://whatsonchain.com/tx/" + txId);
        return txId;
    }


}
