package com.metanet4j.sdk.input;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.metanet4j.sdk.output.TransactionOutputEnhance;
import io.bitcoinsv.bitcoinjsv.core.Address;
import io.bitcoinsv.bitcoinjsv.core.Coin;
import io.bitcoinsv.bitcoinjsv.core.ECKey;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.params.MainNetParams;
import io.bitcoinsv.bitcoinjsv.params.Net;
import io.bitcoinsv.bitcoinjsv.script.ScriptBuilder;
import io.bitcoinsv.bitcoinjsv.script.ScriptUtils;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class TransactionEnhanceContractTest {

    @Test
    void preservesExplicitOutpointHashAndConnectedAddressOutput() {
        Transaction funding = new Transaction(Net.MAINNET);
        Address recipient = ECKey.fromPrivate(BigInteger.TWO).toAddress(MainNetParams.get());
        Coin value = Coin.valueOf(546);
        TransactionOutputEnhance output = new TransactionOutputEnhance(
                Net.MAINNET, funding, value, recipient, 3);
        Sha256Hash knownHash = Sha256Hash.wrap(new byte[32]);
        TransactionOutPointEnhance outpoint = new TransactionOutPointEnhance(
                Net.MAINNET, output, knownHash);
        Transaction spending = new Transaction(Net.MAINNET);
        TransactionInputEnhance input = new TransactionInputEnhance(
                Net.MAINNET, spending, outpoint, value);

        assertEquals(3, output.getIndex());
        assertEquals(recipient.toBase58(),
                ScriptUtils.getToAddress(output.getScriptPubKey(), MainNetParams.get()).toBase58());
        assertEquals(knownHash, input.getOutpoint().getHash());
        assertEquals(3, input.getOutpoint().getIndex());
        assertSame(output, input.getConnectedOutput());
        assertEquals(value, input.getValue());
        assertArrayEquals(new byte[0], input.getScriptBytes());
    }

    @Test
    void keepsRawOutputScriptAndDetachedIndex() {
        byte[] program = new ScriptBuilder().data(new byte[] {1, 2, 3}).build().getProgram();
        TransactionOutputEnhance output = new TransactionOutputEnhance(
                Net.MAINNET, null, Coin.valueOf(1), program, 7);

        assertEquals(7, output.getIndex());
        assertArrayEquals(program, output.getScriptBytes());
    }
}
