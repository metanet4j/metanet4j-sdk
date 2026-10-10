package com.metanet4j.sdk.remote;

import com.metanet4j.sdk.bap.BapBaseCore;
import com.metanet4j.sdk.bap.RemoteBapBase;
import com.metanet4j.sdk.context.PreSignHashContext;
import com.metanet4j.sdk.exception.SignErrorException;
import com.metanet4j.sdk.input.TransactionInputEnhance;
import com.metanet4j.sdk.input.TransactionOutPointEnhance;
import com.metanet4j.sdk.ordinals.OrdScriptBuilder;
import com.metanet4j.sdk.output.TransactionOutputEnhance;
import com.metanet4j.sdk.sigma.Sigma;
import com.metanet4j.sdk.signers.ExtendForSignTransaction;
import io.bitcoinsv.bitcoinjsv.core.Address;
import io.bitcoinsv.bitcoinjsv.core.Coin;
import io.bitcoinsv.bitcoinjsv.core.UTXO;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.msg.protocol.TransactionOutput;
import io.bitcoinsv.bitcoinjsv.script.SigHash;
import io.bitcoinsv.bitcoinjsv.temp.KeyBag;

public class RemoteOrdTransactionBuilder extends RemoteTransactionBuilder {

    public RemoteOrdTransactionBuilder(RemoteBapBase bapBaseCore, RemoteTransactionSigner transactionSigner) {
        super(bapBaseCore, transactionSigner);
    }


    public RemoteOrdTransactionBuilder addOrdInput(UTXO utxo) {
        TransactionOutputEnhance output = new TransactionOutputEnhance(this.getNet(), null, utxo.getValue(), utxo.getScript().getProgram(), (int) utxo.getIndex());
        TransactionOutPointEnhance transactionOutPointEnhance = new TransactionOutPointEnhance(this.getNet(), output, utxo.getHash());
        TransactionInputEnhance transactionInputEnhance = new TransactionInputEnhance(this.getNet(), this.getTargetTransaction(), transactionOutPointEnhance, utxo.getValue());
        this.getTargetTransaction().addInput(transactionInputEnhance);
        return this;
    }


    public RemoteOrdTransactionBuilder addOrdOutput(Address destinationAddress,
                                                    byte[] b64File,
                                                    String mediaType,
                                                    OrdScriptBuilder.ORDMap metaData,
                                                    RemoteSignType remoteSignType) {
        this.getTargetTransaction().addOutput(new TransactionOutput(this.getNet(), this.getTargetTransaction(), Coin.SATOSHI, OrdScriptBuilder.buildInscription(destinationAddress, b64File, mediaType, metaData).getProgram()));
        this.bsmSignHashContexts.add(getSigmaPreSignHash(this.bapBaseCore, remoteSignType));
        return this;
    }


    public RemoteTransactionBuilder addOrdOutput(Address destinationAddress,
                                                 byte[] b64File,
                                                 String mediaType,
                                                 OrdScriptBuilder.ORDMap metaData) {
        return addOrdOutput(destinationAddress, b64File, mediaType, metaData, null);
    }

    private PreSignHashContext getSigmaPreSignHash(BapBaseCore bapBase, RemoteSignType remoteSignType) {
        Sigma sigma = new Sigma(bapBase, targetTransaction, true, remoteSignType);
        return sigma.getPreSignHashContext();
    }


    public RemoteOrdTransactionBuilder signTx(KeyBag keyBag, boolean useForkId) throws SignErrorException {
        return signTx(keyBag, TransactionSignature.SIGHASH_ALL, useForkId, false);
    }


    public RemoteOrdTransactionBuilder signTx(KeyBag keyBag, SigHash.Flags hashFlags, boolean useForkId, boolean anyoneCanPay) throws SignErrorException {
        final ExtendForSignTransaction extendForSignTransaction = new ExtendForSignTransaction(this.getTargetTransaction(), hashFlags, useForkId, anyoneCanPay);
        if (!this.getTransactionSigner().signInputs(extendForSignTransaction, keyBag)) {
            throw new SignErrorException();
        }
        return this;

    }


    public Transaction build() {
        return this.getTargetTransaction();
    }

}
