package com.metanet4j.sdk.script;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.bitcoinsv.bitcoinjsv.bitcoin.api.BitcoinObject;
import io.bitcoinsv.bitcoinjsv.bitcoin.bean.base.TxBean;
import io.bitcoinsv.bitcoinjsv.core.Coin;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.params.Net;
import io.bitcoinsv.bitcoinjsv.script.Script;
import io.bitcoinsv.bitcoinjsv.script.ScriptBuilder;
import io.bitcoinsv.bitcoinjsv.script.SigHash;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SigHashExtendContractTest {

    @Test
    void forkIdDigestMatchesIndependentPreimageAcrossFlags() throws Exception {
        Transaction tx = transaction(2);
        TxBean bean = new TxBean(tx.bitcoinSerialize());
        byte[] connectedScript = new ScriptBuilder().data(new byte[] {7, 8}).build().getProgram();
        Coin previousValue = Coin.valueOf(900);

        for (SigHash.Flags type : Arrays.asList(SigHash.Flags.ALL, SigHash.Flags.SINGLE, SigHash.Flags.NONE)) {
            for (boolean anyoneCanPay : new boolean[] {false, true}) {
                Sha256Hash expected = referenceDigest(tx, 1, connectedScript, previousValue, type, anyoneCanPay);
                Sha256Hash actual = SigHashExtend.hashForForkIdSignature(bean, 1,
                        connectedScript, previousValue, type, anyoneCanPay);
                assertEquals(expected, actual, type + " anyoneCanPay=" + anyoneCanPay);
            }
        }
    }

    @Test
    void singleWithoutCorrespondingOutputUsesZeroOutputHash() throws Exception {
        Transaction tx = transaction(1);
        TxBean bean = new TxBean(tx.bitcoinSerialize());
        byte[] script = new ScriptBuilder().data(new byte[] {9}).build().getProgram();
        Coin value = Coin.valueOf(1000);

        assertEquals(referenceDigest(tx, 1, script, value, SigHash.Flags.SINGLE, false),
                SigHashExtend.hashForForkIdSignature(bean, 1, script, value, SigHash.Flags.SINGLE, false));
        assertThrows(IndexOutOfBoundsException.class,
                () -> SigHashExtend.hashForForkIdSignature(bean, 2, script, value, SigHash.Flags.ALL, false));
    }

    @Test
    void manuallyBuiltTransactionHashesBeforeItsSerializedSizeIsKnown() throws Exception {
        Transaction original = transaction(2);
        byte[] serialized = original.bitcoinSerialize();
        TxBean parsed = new TxBean(serialized);
        TxBean building = new TxBean((BitcoinObject) null);
        building.setVersion(parsed.getVersion());
        building.setLockTime(parsed.getLockTime());
        building.setInputs(new ArrayList<>(parsed.getInputs()));
        building.setOutputs(new ArrayList<>(parsed.getOutputs()));
        assertEquals(BitcoinObject.UNKNOWN_MESSAGE_LENGTH, building.getMessageSize());

        byte[] script = {2, 7, 8};
        Coin value = Coin.valueOf(900);
        Sha256Hash expected = referenceDigest(original, 1, script, value, SigHash.Flags.ALL, false);
        assertEquals(expected, SigHashExtend.hashForForkIdSignature(
                building, 1, script, value, SigHash.Flags.ALL, false));

        assertArrayEquals(serialized, building.serialize());
        assertEquals(serialized.length, building.getMessageSize());
        assertEquals(expected, SigHashExtend.hashForForkIdSignature(
                building, 1, script, value, SigHash.Flags.ALL, false));
    }

    private static Transaction transaction(int outputCount) {
        Transaction tx = new Transaction(Net.MAINNET);
        tx.setVersion(2);
        tx.setLockTime(25);
        byte[] firstHash = new byte[32];
        byte[] secondHash = new byte[32];
        firstHash[0] = 1;
        secondHash[0] = 2;
        tx.addInput(Sha256Hash.wrap(firstHash), 3, new ScriptBuilder().build()).setSequenceNumber(11);
        tx.addInput(Sha256Hash.wrap(secondHash), 4, new ScriptBuilder().build()).setSequenceNumber(12);
        tx.addOutput(Coin.valueOf(111), new ScriptBuilder().data(new byte[] {1, 2}).build());
        if (outputCount == 2) {
            tx.addOutput(Coin.valueOf(222), new ScriptBuilder().data(new byte[] {3, 4, 5}).build());
        }
        return tx;
    }

    private static Sha256Hash referenceDigest(Transaction tx, int selected, byte[] script, Coin value,
            SigHash.Flags type, boolean anyoneCanPay) throws Exception {
        ByteArrayOutputStream allOutpoints = new ByteArrayOutputStream();
        ByteArrayOutputStream allSequences = new ByteArrayOutputStream();
        for (var input : tx.getInputs()) {
            allOutpoints.write(input.getOutpoint().getHash().getReversedBytes());
            write32(allOutpoints, input.getOutpoint().getIndex());
            write32(allSequences, input.getSequenceNumber());
        }

        byte[] previousHash = anyoneCanPay ? new byte[32] : twice(allOutpoints.toByteArray());
        byte[] sequenceHash = anyoneCanPay || type != SigHash.Flags.ALL
                ? new byte[32] : twice(allSequences.toByteArray());
        ByteArrayOutputStream selectedOutputs = new ByteArrayOutputStream();
        if (type == SigHash.Flags.ALL) {
            for (var output : tx.getOutputs()) {
                writeOutput(selectedOutputs, output.getValue().getValue(), output.getScriptPubKey());
            }
        } else if (type == SigHash.Flags.SINGLE && selected < tx.getOutputs().size()) {
            var output = tx.getOutput(selected);
            writeOutput(selectedOutputs, output.getValue().getValue(), output.getScriptPubKey());
        }
        byte[] outputHash = selectedOutputs.size() == 0 ? new byte[32] : twice(selectedOutputs.toByteArray());

        ByteArrayOutputStream preimage = new ByteArrayOutputStream();
        write32(preimage, tx.getVersion());
        preimage.write(previousHash);
        preimage.write(sequenceHash);
        var input = tx.getInput(selected);
        preimage.write(input.getOutpoint().getHash().getReversedBytes());
        write32(preimage, input.getOutpoint().getIndex());
        preimage.write(script.length);
        preimage.write(script);
        write64(preimage, value.getValue());
        write32(preimage, input.getSequenceNumber());
        preimage.write(outputHash);
        write32(preimage, tx.getLockTime());
        int flag = (type == SigHash.Flags.ALL ? 1 : type == SigHash.Flags.NONE ? 2 : 3) | 0x40;
        write32(preimage, anyoneCanPay ? flag | 0x80 : flag);
        return Sha256Hash.wrap(twice(preimage.toByteArray()));
    }

    private static void writeOutput(ByteArrayOutputStream out, long value, Script script) {
        byte[] bytes = script.getProgram();
        write64(out, value);
        out.write(bytes.length);
        out.writeBytes(bytes);
    }

    private static void write32(ByteArrayOutputStream out, long value) {
        out.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt((int) value).array());
    }

    private static void write64(ByteArrayOutputStream out, long value) {
        out.writeBytes(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array());
    }

    private static byte[] twice(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(digest.digest(bytes));
    }
}
