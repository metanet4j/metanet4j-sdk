package com.metanet4j.sdk.signers;

import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.script.SigHash;
import io.bitcoinsv.bitcoinjsv.signers.TransactionSigner;


public class ExtendForSignTransaction extends TransactionSigner.ProposedTransaction {

    private boolean useForkId = false;
    private boolean anyoneCanPay = false;
    private SigHash.Flags hashFlags = SigHash.Flags.ALL;

    public ExtendForSignTransaction(Transaction partialTx) {
        super(partialTx);
    }

    public ExtendForSignTransaction(Transaction partialTx, boolean useForkId) {
        super(partialTx, useForkId);
    }

    public ExtendForSignTransaction(Transaction partialTx, boolean useForkId, boolean anyoneCanPay) {
        super(partialTx);
        this.useForkId = useForkId;
        this.anyoneCanPay = anyoneCanPay;
    }

    public ExtendForSignTransaction(Transaction partialTx, SigHash.Flags hashFlags, boolean useForkId, boolean anyoneCanPay) {
        super(partialTx, useForkId);
        this.useForkId = useForkId;
        this.anyoneCanPay = anyoneCanPay;
        this.hashFlags = hashFlags;
    }

    public boolean isUseForkId() {
        return useForkId;
    }

    public boolean isAnyoneCanPay() {
        return anyoneCanPay;
    }

    public SigHash.Flags getHashFlags() {
        return hashFlags;
    }
}
