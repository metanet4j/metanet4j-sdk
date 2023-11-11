package com.metanet4j.sdk.ordinals;

import com.metanet4j.sdk.bap.BapBaseCore;
import com.metanet4j.sdk.exception.SignErrorException;
import com.metanet4j.sdk.input.TransactionInputEnhance;
import com.metanet4j.sdk.input.TransactionOutPointEnhance;
import com.metanet4j.sdk.output.TransactionOutputEnhance;
import com.metanet4j.sdk.signers.ExtendForSignTransaction;
import com.metanet4j.sdk.signers.TransactionExtend;
import com.metanet4j.sdk.transcation.TransactionBuilder;
import com.metanet4j.sdk.utils.RedeemDataExtend;
import com.metanet4j.sdk.utils.TxHelperExtend;
import io.bitcoinsv.bitcoinjsv.core.Address;
import io.bitcoinsv.bitcoinjsv.core.Coin;
import io.bitcoinsv.bitcoinjsv.core.UTXO;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.msg.protocol.TransactionInput;
import io.bitcoinsv.bitcoinjsv.msg.protocol.TransactionOutPoint;
import io.bitcoinsv.bitcoinjsv.msg.protocol.TransactionOutput;
import io.bitcoinsv.bitcoinjsv.params.Net;
import io.bitcoinsv.bitcoinjsv.script.Script;
import io.bitcoinsv.bitcoinjsv.script.ScriptBuilder;
import io.bitcoinsv.bitcoinjsv.script.SigHash;
import io.bitcoinsv.bitcoinjsv.script.interpreter.ScriptExecutionException;
import io.bitcoinsv.bitcoinjsv.signers.TransactionSigner;
import io.bitcoinsv.bitcoinjsv.temp.KeyBag;

import static com.google.common.base.Preconditions.checkNotNull;

public class OrdTransactionBuilder extends TransactionBuilder {

    public OrdTransactionBuilder(BapBaseCore bapBaseCore, KeyBag keyBag) {
        super(bapBaseCore, keyBag);
    }

    public OrdTransactionBuilder(BapBaseCore bapBaseCore, KeyBag keyBag, TransactionSigner transactionSigner) {
        super(bapBaseCore, keyBag, transactionSigner);
    }

    public OrdTransactionBuilder(BapBaseCore bapBaseCore, Net net, KeyBag keyBag) {
        super(bapBaseCore, net, keyBag);
    }

    public OrdTransactionBuilder(BapBaseCore bapBaseCore, Net net, Transaction targetTransaction, KeyBag keyBag) {
        super(bapBaseCore, net, targetTransaction, keyBag);
    }

    public OrdTransactionBuilder(BapBaseCore bapBaseCore, Net net, Transaction targetTransaction, TransactionSigner transactionSigner, KeyBag keyBag) {
        super(bapBaseCore, net, targetTransaction, transactionSigner, keyBag);
    }

    public OrdTransactionBuilder addOrdInput(UTXO utxo) {
        TransactionOutputEnhance output = new TransactionOutputEnhance(super.net, null, utxo.getValue(), utxo.getScript().getProgram(), (int) utxo.getIndex());
        TransactionOutPoint transactionOutPointEnhance = new TransactionOutPointEnhance(net, output, utxo.getHash());
        TransactionInputEnhance transactionInputEnhance = new TransactionInputEnhance(net, this.targetTransaction,
                transactionOutPointEnhance, utxo.getValue());
        targetTransaction.addInput(transactionInputEnhance);
        return this;
    }


    public OrdTransactionBuilder addOrdOutput(Address destinationAddress,
                                              byte[] b64File,
                                              String mediaType,
                                              OrdScriptBuilder.ORDMap metaData) {
        this.getTargetTransaction().addOutput(new TransactionOutput(this.getNet(), this.getTargetTransaction(), Coin.SATOSHI, OrdScriptBuilder.buildInscription(destinationAddress, b64File, mediaType, metaData).getProgram()));
        return this;
    }


    public OrdTransactionBuilder signTx(KeyBag keyBag, boolean useForkId) throws SignErrorException {
        return signTx(keyBag, SigHash.Flags.ALL, true, false);
    }


    public OrdTransactionBuilder signTx(KeyBag keyBag, SigHash.Flags hashFlags, boolean useForkId, boolean anyoneCanPay) throws SignErrorException {

        setEmptyInputScriptHaveOrdinals(this.getTargetTransaction(), keyBag);
        final ExtendForSignTransaction extendForSignTransaction = new ExtendForSignTransaction(TransactionExtend.toTransactionExtend(this.getTargetTransaction()), hashFlags, useForkId, anyoneCanPay);
        boolean b = this.getTransactionSigner().signInputs(extendForSignTransaction, keyBag);
        return this;

    }

    public Transaction build() {
        return this.getTargetTransaction();
    }

    protected void setEmptyInputScriptHaveOrdinals(final Transaction outputTransaction, final KeyBag bag)
            throws ScriptExecutionException {
        int numInputs = outputTransaction.getInputs().size();
        for (int i = 0; i < numInputs; i++) {
            TransactionInput txIn = outputTransaction.getInput(i);
            Script scriptPubKey = txIn.getConnectedOutput().getScriptPubKey();
            RedeemDataExtend redeemData = TxHelperExtend.getConnectedRedeemDataExtend(txIn.getOutpoint(), bag);
            checkNotNull(redeemData, "Transaction exists in wallet that we cannot redeem: %s",
                    txIn.getOutpoint().getHash());
            if (TxHelperExtend.isSentToOrdinals(scriptPubKey)) {
                txIn.setScriptSig(
                        ScriptBuilder.createInputScript(null, redeemData.keys.get(0).getPubKey()));
            } else {
                txIn.setScriptSig(
                        scriptPubKey.createEmptyInputScript(redeemData.keys.get(0).getPubKey(), redeemData.redeemScript));
            }

        }
    }

}
