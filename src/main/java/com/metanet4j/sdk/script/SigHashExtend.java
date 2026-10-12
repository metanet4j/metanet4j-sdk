package com.metanet4j.sdk.script;

import com.metanet4j.base.bsv.BsvAmount;
import com.metanet4j.base.bsv.BsvHashBytes;
import com.metanet4j.bsv.primitives.Hash;
import com.metanet4j.bsv.primitives.TransactionSignature;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.transaction.Transaction;
import com.metanet4j.bsv.transaction.TransactionInput;
import com.metanet4j.bsv.transaction.TransactionOutput;
import java.util.ArrayList;
import java.util.List;

/**
 * ForkID（BIP143 风格）前像摘要计算。
 *
 * <p>旧实现继承 bitcoinj 的 {@code SigHash} 并自行拼装前像；现改为把参数适配到新 SDK
 * （com.metanet4j.bsv）的 {@link TransactionSignature#formatBip143} 编码器，
 * 再取双 SHA-256 作为摘要。参数语义与原实现逐项对应：
 * {@code hashPrevouts}（outpoint 显示序 + index）、{@code hashSequence}、
 * {@code hashOutputs}（金额 LE + CompactSize 脚本长度 + 脚本）、
 * 当前输入的 outpoint／subscript／prevValue／sequence、lockTime 与 sighash 字节。
 *
 * <p>对外仍暴露 {@code (Transaction, int, byte[], BsvAmount, int, boolean)} 的静态入口，
 * 供交易与签名流程调用；返回类型为业务哈希值类型 {@link BsvHashBytes}。
 */
public class SigHashExtend {

    /** ForkID 标志位（BIP143 的 0x40）。 */
    private static final int SIGHASH_FORKID = TransactionSignature.SIGHASH_FORKID;

    /**
     * 计算指定输入在 ForkID 模式下的前像摘要。
     *
     * @param transaction     交易（新 SDK 模型）
     * @param inputIndex      待签名输入下标
     * @param connectedScript 该输入所花费的锁定脚本
     * @param prevValue       该输入引用的输出金额
     * @param type            SIGHASH 基础类型（ALL／NONE／SINGLE 的 int 常量）
     * @param anyoneCanPay    是否 ANYONECANPAY
     */
    public static BsvHashBytes hashForForkIdSignature(Transaction transaction,
                                                      int inputIndex,
                                                      byte[] connectedScript,
                                                      BsvAmount prevValue,
                                                      int type,
                                                      boolean anyoneCanPay) {
        return hashForSignature(transaction, inputIndex, connectedScript, prevValue, type,
                anyoneCanPay, true);
    }

    /** 统一计算 ForkID 与原 API 的 legacy／OTDA 摘要。 */
    public static BsvHashBytes hashForSignature(Transaction transaction, int inputIndex,
            byte[] connectedScript, BsvAmount prevValue, int type, boolean anyoneCanPay,
            boolean useForkId) {
        if (transaction == null) {
            throw new IllegalArgumentException("交易不能为空");
        }
        List<TransactionInput> inputs = transaction.inputs;
        List<TransactionOutput> outputs = transaction.outputs;
        if (inputIndex < 0 || inputIndex >= inputs.size()) {
            throw new IllegalArgumentException("输入下标越界：" + inputIndex + "（输入数 " + inputs.size() + "）");
        }
        TransactionInput indexedInput = inputs.get(inputIndex);

        int scope = type | (anyoneCanPay ? TransactionSignature.SIGHASH_ANYONECANPAY : 0)
                | (useForkId ? SIGHASH_FORKID : 0);

        TransactionSignature.Params params = new TransactionSignature.Params();
        params.sourceTXID = txidOf(indexedInput.sourceTXID);
        params.sourceOutputIndex = indexedInput.sourceOutputIndex;
        params.sourceSatoshis = prevValue == null ? 0d : prevValue.toSdkValue();
        params.transactionVersion = transaction.version;
        params.allInputs = new ArrayList<>(inputs);
        params.outputs = new ArrayList<>(outputs);
        params.inputIndex = inputIndex;
        params.subscript = Script.fromBinary(connectedScript);
        params.inputSequence = sequenceOf(indexedInput.sequence);
        params.lockTime = transaction.lockTime;
        params.scope = scope;

        if (TransactionSignature.usesOtdaSingleBug(params)) {
            byte[] one = new byte[32];
            one[0] = 1;
            return BsvHashBytes.wrap(one);
        }
        byte[] preimage = TransactionSignature.formatBytes(params);
        // 摘要已执行双 SHA-256；wrap 直接承载内部字节，of 会错误地再哈希一次。
        return BsvHashBytes.wrap(toBytes(Hash.hash256(toBoxed(preimage), null)));
    }

    /** 摘要的 hex 形式（显示序），便于日志与断言。 */
    public static String hashHexForForkIdSignature(Transaction transaction, int inputIndex,
                                                   byte[] connectedScript, BsvAmount prevValue,
                                                   int type, boolean anyoneCanPay) {
        return hashForForkIdSignature(transaction, inputIndex, connectedScript, prevValue, type,
                anyoneCanPay).toString();
    }

    /** {@code sourceTXID} 可能是 hex 串或字节列表；统一为显示序 hex。 */
    private static String txidOf(Object sourceTXID) {
        if (sourceTXID == null) {
            return BsvHashBytes.ZERO_HASH.toString();
        }
        if (sourceTXID instanceof String text) {
            return text;
        }
        if (sourceTXID instanceof byte[] bytes) {
            return new BsvHashBytes(bytes).toString();
        }
        if (sourceTXID instanceof List<?> list) {
            byte[] bytes = new byte[list.size()];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) ((Number) list.get(i)).intValue();
            }
            return new BsvHashBytes(bytes).toString();
        }
        return sourceTXID.toString();
    }

    /** sequence 字段为 {@code Long} 且可省略，缺省按 0xffffffff。 */
    private static long sequenceOf(Long sequence) {
        return sequence == null ? 0xffff_ffffL : sequence;
    }

    private static byte[] toBytes(List<? extends Number> values) {
        byte[] out = new byte[values == null ? 0 : values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) (values.get(i).intValue() & 0xFF);
        }
        return out;
    }

    private static List<Double> toBoxed(byte[] data) {
        Double[] values = new Double[data.length];
        for (int i = 0; i < data.length; i++) {
            values[i] = (double) (data[i] & 0xFF);
        }
        return List.of(values);
    }
}
