package com.metanet4j.sdk.script;

import io.bitcoinsv.bitcoinjsv.bitcoin.api.base.Tx;
import io.bitcoinsv.bitcoinjsv.bitcoin.api.base.TxInput;
import io.bitcoinsv.bitcoinjsv.bitcoin.api.base.TxOutput;
import io.bitcoinsv.bitcoinjsv.bitcoin.bean.BitcoinObjectImpl;
import io.bitcoinsv.bitcoinjsv.core.*;
import io.bitcoinsv.bitcoinjsv.ecc.TransactionSignature;
import io.bitcoinsv.bitcoinjsv.script.SigHash;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;

public class SigHashExtend extends SigHash {

    public static Sha256Hash hashForForkIdSignature(Tx transaction,
                                                    int inputIndex,
                                                    byte[] connectedScript,
                                                    Coin prevValue,
                                                    Flags type,
                                                    boolean anyoneCanPay) {
        byte sigHashType = (byte) TransactionSignature.calcSigHashValue(type, anyoneCanPay, true);
        ByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(transaction.getMessageSize() == BitcoinObjectImpl.UNKNOWN_MESSAGE_LENGTH ? 512 : transaction.getMessageSize() + 4);
        try {
            byte[] hashPrevouts = new byte[32];
            byte[] hashSequence = new byte[32];
            byte[] hashOutputs = new byte[32];
            anyoneCanPay = (sigHashType & Flags.ANYONECANPAY.value) == Flags.ANYONECANPAY.value;


            TxInput indexedInput = transaction.getInputs().get(inputIndex);

            if (!anyoneCanPay) {
                ByteArrayOutputStream bosHashPrevouts = new UnsafeByteArrayOutputStream(256);
                for (int i = 0; i < transaction.getInputs().size(); ++i) {
                    TxInput input = transaction.getInputs().get(i);
                    bosHashPrevouts.write(input.getOutpoint().getHash().getReversedBytes());
                    Utils.uint32ToByteStreamLE(input.getOutpoint().getIndex(), bosHashPrevouts);
                }
                hashPrevouts = Sha256Hash.hashTwice(bosHashPrevouts.toByteArray());
            }

            if (!anyoneCanPay && type != Flags.SINGLE && type != Flags.NONE) {
                ByteArrayOutputStream bosSequence = new UnsafeByteArrayOutputStream(256);
                for (int i = 0; i < transaction.getInputs().size(); ++i) {
                    Utils.uint32ToByteStreamLE(transaction.getInputs().get(i).getSequenceNumber(), bosSequence);
                }
                hashSequence = Sha256Hash.hashTwice(bosSequence.toByteArray());
            }

            if (type != Flags.SINGLE && type != Flags.NONE) {
                ByteArrayOutputStream bosHashOutputs = new UnsafeByteArrayOutputStream(256);
                for (int i = 0; i < transaction.getOutputs().size(); ++i) {
                    TxOutput output = transaction.getOutputs().get(i);
                    Utils.uint64ToByteStreamLE(
                            BigInteger.valueOf(output.getValue().getValue()),
                            bosHashOutputs
                    );
                    bosHashOutputs.write(new VarInt(output.getScriptBytes().length).encode());
                    bosHashOutputs.write(output.getScriptBytes());
                }
                hashOutputs = Sha256Hash.hashTwice(bosHashOutputs.toByteArray());
            } else if (type == Flags.SINGLE && inputIndex < transaction.getOutputs().size()) {
                //fix bitcoinj-sv bug
                TxOutput indexedOutput = transaction.getOutputs().get(inputIndex);
                ByteArrayOutputStream bosHashOutputs = new UnsafeByteArrayOutputStream(256);
                Utils.uint64ToByteStreamLE(
                        BigInteger.valueOf(indexedOutput.getValue().getValue()),
                        bosHashOutputs
                );
                bosHashOutputs.write(new VarInt(indexedOutput.getScriptBytes().length).encode());
                bosHashOutputs.write(indexedOutput.getScriptBytes());
                hashOutputs = Sha256Hash.hashTwice(bosHashOutputs.toByteArray());
            }
            Utils.uint32ToByteStreamLE(transaction.getVersion(), bos);
            bos.write(hashPrevouts);
            bos.write(hashSequence);
            bos.write(indexedInput.getOutpoint().getHash().getReversedBytes());
            Utils.uint32ToByteStreamLE(indexedInput.getOutpoint().getIndex(), bos);
            bos.write(new VarInt(connectedScript.length).encode());
            bos.write(connectedScript);
            Utils.uint64ToByteStreamLE(BigInteger.valueOf(prevValue.getValue()), bos);
            Utils.uint32ToByteStreamLE(indexedInput.getSequenceNumber(), bos);
            bos.write(hashOutputs);
            Utils.uint32ToByteStreamLE(transaction.getLockTime(), bos);
            Utils.uint32ToByteStreamLE(0x000000ff & sigHashType, bos);
        } catch (IOException e) {
            throw new RuntimeException(e);  // Cannot happen.
        }
        return Sha256Hash.wrap(Sha256Hash.hashTwice(bos.toByteArray(), 0, bos.size()));

    }
}
