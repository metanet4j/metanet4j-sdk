package com.metanet4j.sdk.input;

import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import io.bitcoinsv.bitcoinjsv.msg.protocol.TransactionOutPoint;
import io.bitcoinsv.bitcoinjsv.msg.protocol.TransactionOutput;
import io.bitcoinsv.bitcoinjsv.params.Net;

public class TransactionOutPointEnhance extends TransactionOutPoint {

    private TransactionOutput connectedOutput;


    public TransactionOutPointEnhance(Net net,
            TransactionOutput connectedOutput,Sha256Hash hash) {
        super(net, connectedOutput);
        super.setHash(hash);
        this.connectedOutput = connectedOutput;

    }

    @Override
    public TransactionOutput getConnectedOutput() {
        return connectedOutput;
    }


}
