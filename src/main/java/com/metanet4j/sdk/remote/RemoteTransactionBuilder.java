package com.metanet4j.sdk.remote;

import com.google.common.collect.Lists;
import com.metanet4j.base.bsv.BsvAmount;
import com.metanet4j.base.bsv.BsvHashBytes;
import com.metanet4j.bsv.script.OP;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.script.ScriptChunk;
import com.metanet4j.bsv.transaction.Transaction;
import com.metanet4j.bsv.transaction.TransactionInput;
import com.metanet4j.bsv.transaction.TransactionOutput;
import com.metanet4j.sdk.bap.BapBaseCore;
import com.metanet4j.sdk.bap.RemoteBapBase;
import com.metanet4j.sdk.bap.SigningKeyProvider;
import com.metanet4j.sdk.context.PreSignHashContext;
import com.metanet4j.sdk.exception.SignErrorException;
import com.metanet4j.sdk.script.SigHashExtend;
import com.metanet4j.sdk.sigma.Sigma;
import com.metanet4j.sdk.transcation.LockingScriptBuilder;
import com.metanet4j.sdk.transcation.TransactionBuilder;
import com.metanet4j.sdk.utils.TxHelperExtend;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * 远程签名交易构建：只生成各输入的预签名哈希与签名地址，不落地私钥。
 *
 * <p>旧实现依赖 bitcoinj 交易模型、{@code KeyBag} 与 {@code TransactionSigner}；
 * 按迁移计划 §3 改为新 SDK 交易模型 + {@link SigningKeyProvider} 显式密钥，
 * 金额用 {@link BsvAmount}，预签名哈希用 {@link BsvHashBytes}。
 */
@Slf4j
public class RemoteTransactionBuilder extends TransactionBuilder implements TransactionAllPreSignHash {

    public List<PreSignHashContext> bsmSignHashContexts;

    private final RemoteTransactionSigner remoteTransactionSigner = new RemoteTransactionSigner();

    public RemoteTransactionBuilder(RemoteBapBase bapBaseCore, SigningKeyProvider keyProvider) {
        super(bapBaseCore, null, keyProvider);
        this.bsmSignHashContexts = new ArrayList<>();
    }

    /** 远程签名器（回填远程返回的签名）。 */
    public RemoteTransactionSigner getRemoteTransactionSigner() {
        return remoteTransactionSigner;
    }

    @Override
    public TransactionBuilder addDataOutput(LockingScriptBuilder lockingScriptBuilder) {
        if (!lockingScriptBuilder.isHaveSign()) {
            bsmSignHashContexts.add(lockingScriptBuilder.getPreSignHashContext());
        }
        return super.addDataOutput(lockingScriptBuilder);
    }

    public TransactionBuilder addSigmaSign(BapBaseCore bapBase, RemoteSignType remoteSignType) {
        Sigma sigma = new Sigma(bapBase, targetTransaction, true, remoteSignType);
        targetTransaction = sigma.sign().getSignedTx();
        this.sigma = sigma;
        return this;
    }

    /** 远程流程不在本地清空输入脚本（由预签名哈希替代）。 */
    @Override
    protected void prepareInputsForSigning(Transaction transaction, SigningKeyProvider provider) {
        // no-op：远程签名由 RemoteTransactionSigner 回填
    }

    @Override
    protected void prepareInputsForSigning(Transaction transaction, SigningKeyProvider provider,
                                           int hashFlags, boolean anyoneCanPay) {
        prepareInputsForSigning(transaction, provider, hashFlags, true, anyoneCanPay);
    }

    @Override
    protected void prepareInputsForSigning(Transaction transaction, SigningKeyProvider provider,
                                           int hashFlags, boolean useForkId, boolean anyoneCanPay) {
        if (!getRemoteTransactionSigner().signInputs(transaction, provider, hashFlags, useForkId, anyoneCanPay)) {
            throw new SignErrorException("没有输入完成远程签名回填");
        }
    }

    @Override
    public List<PreSignHashContext> getAllBsmSignHash(boolean useForkId, boolean anyoneCanPay,
                                                      BapBaseCore bapBase) {
        return this.bsmSignHashContexts;
    }

    public List<PreSignHashContext> getTxInputSignHash(boolean anyoneCanPay, BapBaseCore bapBase) {
        return getTxInputSignHash(com.metanet4j.bsv.primitives.TransactionSignature.SIGHASH_ALL,
                anyoneCanPay, bapBase);
    }

    /**
     * 逐输入计算预签名哈希与签名地址。
     *
     * @param hashFlags SIGHASH 基础类型（{@code TransactionSignature.SIGHASH_*}）
     */
    public List<PreSignHashContext> getTxInputSignHash(int hashFlags, boolean anyoneCanPay,
                                                       BapBaseCore bapBase) {
        return getTxInputSignHash(hashFlags, true, anyoneCanPay, bapBase);
    }

    public List<PreSignHashContext> getTxInputSignHash(int hashFlags, boolean useForkId,
                                                       boolean anyoneCanPay, BapBaseCore bapBase) {
        List<PreSignHashContext> preSignHashContexts = Lists.newArrayList();
        int numInputs = this.targetTransaction.inputs.size();
        for (int i = 0; i < numInputs; i++) {
            PreSignHashContext preSignHashContext = new PreSignHashContext();
            TransactionInput txIn = this.targetTransaction.inputs.get(i);
            TransactionOutput sourceOutput = sourceOutputOf(txIn);
            if (sourceOutput == null) {
                log.warn("Missing connected output, assuming input {} is already signed.", i);
                continue;
            }
            Script scriptPubKey = sourceOutput.lockingScript == null
                    ? new Script() : sourceOutput.lockingScript;
            BsvHashBytes hash = SigHashExtend.hashForSignature(this.targetTransaction, i,
                    scriptPubKey.toUint8Array(), BsvAmount.fromSdkValue(sourceOutput.satoshis),
                    hashFlags, anyoneCanPay, useForkId);
            preSignHashContext.setPreSignHash(hash);

            if (TxHelperExtend.isSentToOrdinals(scriptPubKey)) {
                preSignHashContext.setRemoteSignType(RemoteSignType.ORD);
                preSignHashContext.setSignAddress(bapBase.getSignAddress(RemoteSignType.ORD));
            } else {
                preSignHashContext.setRemoteSignType(RemoteSignType.PAYMENT);
                preSignHashContext.setSignAddress(bapBase.getSignAddress(RemoteSignType.PAYMENT));
            }
            preSignHashContexts.add(preSignHashContext);
        }
        return preSignHashContexts.stream().map(o -> {
            o.setFee(this.getFee());
            return o;
        }).collect(Collectors.toList());
    }

    @Override
    protected long getUnLockScriptSize(TransactionInput transactionInput, Script script) {
        return 76 + 33 + 10;
    }

    private static TransactionOutput sourceOutputOf(TransactionInput input) {
        // 【§87 收敛】原为四处各一份的私有副本，现委托全项目唯一实现（TxHelperExtend）。
        // 注意：委托后，本类的 §216／§219／§230 三个用例实际覆盖的是**公共实现**的那三个条件，
        // 这正是收敛的目的——同一批条件只需覆盖一次。
        return com.metanet4j.sdk.utils.TxHelperExtend.sourceOutputOf(input);
    }
}
