package com.metanet4j.sdk.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.metanet4j.base.bsv.BsvAmount;
import com.metanet4j.base.bsv.BsvHashBytes;
import com.metanet4j.bsv.primitives.TransactionSignature;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.transaction.Transaction;
import com.metanet4j.bsv.transaction.TransactionInput;
import com.metanet4j.bsv.transaction.TransactionOutput;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link SigHashExtend} 的 ForkID 前像摘要契约（V09 相关）。
 *
 * <p><b>计划 §3 的硬约束</b>：本类中的 {@link #referenceDigest} 是**独立的前像计算器**——
 * 它自行拼装 BIP143 前像，**不得**改为调用 {@code SigHashExtend}，否则验收失去独立参照。
 * 迁移只做类型与访问器替换（旧 {@code TxBean}/{@code Coin}/{@code Sha256Hash} →
 * 新 SDK {@code Transaction}/{@code BsvAmount}/{@code BsvHashBytes}）。
 */
class SigHashExtendContractTest {

    @Test
    void forkIdDigestMatchesIndependentPreimageAcrossFlags() throws Exception {
        Transaction tx = transaction(2);
        byte[] connectedScript = script(new byte[] {7, 8});
        BsvAmount previousValue = BsvAmount.valueOf(900);

        for (int type : Arrays.asList(TransactionSignature.SIGHASH_ALL,
                TransactionSignature.SIGHASH_SINGLE, TransactionSignature.SIGHASH_NONE)) {
            for (boolean anyoneCanPay : new boolean[] {false, true}) {
                BsvHashBytes expected = referenceDigest(tx, 1, connectedScript, previousValue, type, anyoneCanPay);
                BsvHashBytes actual = SigHashExtend.hashForForkIdSignature(tx, 1,
                        connectedScript, previousValue, type, anyoneCanPay);
                assertEquals(expected, actual, "type=" + type + " anyoneCanPay=" + anyoneCanPay);
            }
        }
    }

    @Test
    void singleWithoutCorrespondingOutputUsesZeroOutputHash() throws Exception {
        Transaction tx = transaction(1);
        byte[] connectedScript = script(new byte[] {9});
        BsvAmount value = BsvAmount.valueOf(1000);

        assertEquals(
                referenceDigest(tx, 1, connectedScript, value, TransactionSignature.SIGHASH_SINGLE, false),
                SigHashExtend.hashForForkIdSignature(tx, 1, connectedScript, value,
                        TransactionSignature.SIGHASH_SINGLE, false));
        assertThrows(IllegalArgumentException.class,
                () -> SigHashExtend.hashForForkIdSignature(tx, 2, connectedScript, value,
                        TransactionSignature.SIGHASH_ALL, false));
    }

    @Test
    void manuallyBuiltAndParsedTransactionsProduceTheSameDigest() throws Exception {
        Transaction original = transaction(2);
        Transaction parsed = Transaction.fromBinary(original.toBinary());

        byte[] connectedScript = {2, 7, 8};
        BsvAmount value = BsvAmount.valueOf(900);
        BsvHashBytes expected = referenceDigest(original, 1, connectedScript, value,
                TransactionSignature.SIGHASH_ALL, false);

        assertEquals(expected, SigHashExtend.hashForForkIdSignature(parsed, 1, connectedScript, value,
                TransactionSignature.SIGHASH_ALL, false));
        assertEquals(original.toBinary(), parsed.toBinary(), "重新序列化应得到相同字节");
    }

    /** 固定夹具：2 个输入（sequence 11/12）、1 或 2 个输出，version=2、lockTime=25。 */
    private static Transaction transaction(int outputCount) {
        Transaction tx = new Transaction();
        tx.version = 2;
        tx.lockTime = 25;
        tx.inputs.add(input(1, 3, 11));
        tx.inputs.add(input(2, 4, 12));
        tx.outputs.add(new TransactionOutput(Script.fromBinary(script(new byte[] {1, 2})), 111d));
        if (outputCount == 2) {
            tx.outputs.add(new TransactionOutput(Script.fromBinary(script(new byte[] {3, 4, 5})), 222d));
        }
        return tx;
    }

    private static TransactionInput input(int firstHashByte, int index, long sequence) {
        byte[] hash = new byte[32];
        hash[0] = (byte) firstHashByte;
        TransactionInput input = new TransactionInput(new BsvHashBytes(hash).toString(), index,
                new Script());
        input.sequence = sequence;
        return input;
    }

    private static byte[] script(byte[] data) {
        return new Script().writeBin(toBoxed(data)).toUint8Array();
    }

    /**
     * 独立前像计算器（BIP143 风格 ForkID）。
     *
     * <p><b>不得</b>改为调用 {@link SigHashExtend}：它是被验收对象的独立参照。
     * 与生产实现的唯一差异是 script 长度用单字节写（脚本 &lt;253 字节时与 CompactSize 一致）。
     */
    private static BsvHashBytes referenceDigest(Transaction tx, int selected, byte[] script,
            BsvAmount value, int type, boolean anyoneCanPay) throws Exception {
        ByteArrayOutputStream allOutpoints = new ByteArrayOutputStream();
        ByteArrayOutputStream allSequences = new ByteArrayOutputStream();
        for (TransactionInput input : tx.inputs) {
            allOutpoints.write(outpointBytes(input.sourceTXID));
            write32(allOutpoints, input.sourceOutputIndex);
            write32(allSequences, sequenceOf(input));
        }

        byte[] previousHash = anyoneCanPay ? new byte[32] : twice(allOutpoints.toByteArray());
        byte[] sequenceHash = anyoneCanPay || type != TransactionSignature.SIGHASH_ALL
                ? new byte[32] : twice(allSequences.toByteArray());

        ByteArrayOutputStream selectedOutputs = new ByteArrayOutputStream();
        if (type == TransactionSignature.SIGHASH_ALL) {
            for (TransactionOutput output : tx.outputs) {
                writeOutput(selectedOutputs, satoshisOf(output), output.lockingScript);
            }
        } else if (type == TransactionSignature.SIGHASH_SINGLE && selected < tx.outputs.size()) {
            TransactionOutput output = tx.outputs.get(selected);
            writeOutput(selectedOutputs, satoshisOf(output), output.lockingScript);
        }
        byte[] outputHash = selectedOutputs.size() == 0
                ? new byte[32] : twice(selectedOutputs.toByteArray());

        ByteArrayOutputStream preimage = new ByteArrayOutputStream();
        write32(preimage, (int) tx.version);
        preimage.write(previousHash);
        preimage.write(sequenceHash);
        TransactionInput input = tx.inputs.get(selected);
        preimage.write(outpointBytes(input.sourceTXID));
        write32(preimage, input.sourceOutputIndex);
        preimage.write(script.length);
        preimage.write(script);
        write64(preimage, value.getValue());
        write32(preimage, sequenceOf(input));
        preimage.write(outputHash);
        write32(preimage, (int) tx.lockTime);
        int flag = type | TransactionSignature.SIGHASH_FORKID;
        write32(preimage, anyoneCanPay ? flag | TransactionSignature.SIGHASH_ANYONECANPAY : flag);
        return BsvHashBytes.of(twice(preimage.toByteArray()));
    }

    /** sourceTXID 为显示序 hex；前像需要内部字节序。 */
    private static byte[] outpointBytes(Object sourceTXID) {
        return BsvHashBytes.fromDisplayHex(String.valueOf(sourceTXID)).getBytes();
    }

    private static long sequenceOf(TransactionInput input) {
        return input.sequence == null ? 0xffff_ffffL : input.sequence;
    }

    private static long satoshisOf(TransactionOutput output) {
        return output.satoshis instanceof Number number ? number.longValue() : 0L;
    }

    private static void writeOutput(ByteArrayOutputStream out, long value, Script script) {
        byte[] bytes = script == null ? new byte[0] : script.toUint8Array();
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

    private static List<Integer> toBoxed(byte[] data) {
        List<Integer> out = new ArrayList<>(data.length);
        for (byte b : data) {
            out.add(b & 0xFF);
        }
        return out;
    }
}
