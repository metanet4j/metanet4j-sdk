package com.metanet4j.sdk.signers;

import com.metanet4j.sdk.script.SigHashExtend;
import io.bitcoinsv.bitcoinjsv.core.Coin;
import io.bitcoinsv.bitcoinjsv.core.ECKey;
import io.bitcoinsv.bitcoinjsv.core.ProtocolException;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import io.bitcoinsv.bitcoinjsv.ecc.TransactionSignature;
import io.bitcoinsv.bitcoinjsv.msg.Message;
import io.bitcoinsv.bitcoinjsv.msg.Translate;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.params.Net;
import io.bitcoinsv.bitcoinjsv.params.SerializeMode;
import io.bitcoinsv.bitcoinjsv.script.SigHash;

import javax.annotation.Nullable;

public class TransactionExtend extends Transaction {


    public TransactionExtend(Net net) {
        super(net);
    }

    public TransactionExtend(Net net, byte[] payloadBytes) throws ProtocolException {
        super(net, payloadBytes);
    }

    public TransactionExtend(Net net, byte[] payload, int offset) throws ProtocolException {
        super(net, payload, offset);
    }

    public TransactionExtend(Net net, byte[] payload, int offset, @Nullable Message parent, SerializeMode serializeMode, int length) throws ProtocolException {
        super(net, payload, offset, parent, serializeMode, length);
    }

    public TransactionExtend(Net net, byte[] payload, @Nullable Message parent, SerializeMode serializeMode, int length) throws ProtocolException {
        super(net, payload, parent, serializeMode, length);
    }

    public static TransactionExtend toTransactionExtend(Transaction transaction) {
        TransactionExtend transactionExtend = new TransactionExtend(Net.MAINNET);
        transaction.getInputs().forEach(transactionExtend::addInput);
        transaction.getOutputs().forEach(transactionExtend::addOutput);
        return transactionExtend;
    }

    @Override
    public TransactionSignature calculateForkIdSignature(int inputIndex, ECKey key, byte[] redeemScript, Coin value, SigHash.Flags hashType, boolean anyoneCanPay) {
        Sha256Hash hash = SigHashExtend.hashForForkIdSignature(Translate.toTx(this), inputIndex, redeemScript, value, hashType, anyoneCanPay);
        return new TransactionSignature(key.sign(hash), hashType, anyoneCanPay, true);
    }
}
