package com.metanet4j.sdk.input;

import io.bitcoinsv.bitcoinjsv.core.Coin;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.msg.protocol.TransactionInput;
import io.bitcoinsv.bitcoinjsv.msg.protocol.TransactionOutPoint;
import io.bitcoinsv.bitcoinjsv.params.Net;
import javax.annotation.Nullable;

public class TransactionInputEnhance extends TransactionInput {

    public TransactionInputEnhance(Net net, @Nullable Transaction parentTransaction,
            TransactionOutPoint outpoint, @Nullable Coin value) {
        super(net, parentTransaction, null, outpoint, value);
        byte[] scriptBytes =new byte[0];
        super.setScriptBytes(scriptBytes);
    }


}
