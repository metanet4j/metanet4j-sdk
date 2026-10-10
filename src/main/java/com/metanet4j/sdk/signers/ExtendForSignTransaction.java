package com.metanet4j.sdk.signers;

import com.metanet4j.bsv.primitives.TransactionSignature;
import com.metanet4j.bsv.transaction.Transaction;

/**
 * 待签名交易及其签名选项。
 *
 * <p>旧实现继承 bitcoinj 的 {@code TransactionSigner.ProposedTransaction}；按迁移计划 §3，
 * 旧 {@code TransactionSigner} 抽象已取消（改为每输入注入解锁模板 + {@code Transaction.sign()}），
 * 因此本类改为**普通业务载体**：持有新 SDK 交易与 ForkID／ANYONECANPAY／SIGHASH 选项。
 */
public class ExtendForSignTransaction {

    /** 待签名交易（新 SDK 模型）。 */
    public final Transaction partialTx;

    private final boolean useForkId;
    private final boolean anyoneCanPay;
    private final int hashFlags;

    public ExtendForSignTransaction(Transaction partialTx) {
        this(partialTx, TransactionSignature.SIGHASH_ALL, false, false);
    }

    public ExtendForSignTransaction(Transaction partialTx, int hashFlags, boolean useForkId,
                                    boolean anyoneCanPay) {
        if (partialTx == null) {
            throw new IllegalArgumentException("待签名交易不能为空");
        }
        this.partialTx = partialTx;
        this.hashFlags = hashFlags;
        this.useForkId = useForkId;
        this.anyoneCanPay = anyoneCanPay;
    }

    /** 是否使用 ForkID（BIP143 风格前像）。 */
    public boolean isUseForkId() {
        return useForkId;
    }

    /** 是否 ANYONECANPAY。 */
    public boolean isAnyoneCanPay() {
        return anyoneCanPay;
    }

    /** SIGHASH 基础类型（{@code TransactionSignature.SIGHASH_*} 常量）。 */
    public int getHashFlags() {
        return hashFlags;
    }

    /** 拼出完整范围值：基础类型 + ANYONECANPAY + ForkID。 */
    public int signatureScope() {
        return hashFlags
                | (anyoneCanPay ? TransactionSignature.SIGHASH_ANYONECANPAY : 0)
                | (useForkId ? TransactionSignature.SIGHASH_FORKID : 0);
    }
}
