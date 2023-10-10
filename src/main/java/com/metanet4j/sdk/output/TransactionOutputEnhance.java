package com.metanet4j.sdk.output;

import io.bitcoinsv.bitcoinjsv.core.Address;
import io.bitcoinsv.bitcoinjsv.core.Coin;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.msg.protocol.TransactionOutput;
import io.bitcoinsv.bitcoinjsv.params.Net;

import javax.annotation.Nullable;

public class TransactionOutputEnhance extends TransactionOutput {

    private int index;

    public TransactionOutputEnhance(Net net,
                                    @Nullable Transaction parent,
                                    Coin value, Address to, int index) {
        super(net, parent, value, to);
        this.index = index;

    }

    public TransactionOutputEnhance(Net net, @Nullable Transaction parent, Coin value, byte[] scriptBytes, int index) {
        super(net, parent, value, scriptBytes);
        this.index = index;
    }

    @Override
    public int getIndex() {
        return this.index;
    }


}
