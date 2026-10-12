package com.metanet4j.sdk.signers;

import cn.hutool.core.util.StrUtil;
import com.metanet4j.sdk.exception.SignErrorException;
import com.metanet4j.sdk.script.ScriptExtend;
import com.metanet4j.sdk.script.SigHashExtend;
import com.metanet4j.sdk.utils.RedeemDataExtend;
import com.metanet4j.sdk.utils.TxHelperExtend;
import com.metanet4j.base.bsv.BsvAmount;
import com.metanet4j.base.bsv.BsvHashBytes;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.script.templates.P2PKH;
import com.metanet4j.bsv.transaction.Transaction;
import com.metanet4j.bsv.transaction.TransactionInput;
import com.metanet4j.bsv.transaction.TransactionOutput;
import com.metanet4j.sdk.bap.SigningKeyProvider;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ordinals 输入签名器（业务组件）。
 *
 * <p>旧实现实现 bitcoinj 的 {@code TransactionSigner}；按迁移计划 §3 该抽象已取消，
 * 改为「按公钥哈希取私钥 → 注入解锁模板 → 交给交易签名」，保留单输入与批量签名入口。
 */
public class OrdinalsTransactionSigner {
    private static final Logger log = LoggerFactory.getLogger(OrdinalsTransactionSigner.class);


    /**
     * 签名单个输入：按锁定脚本取得公钥哈希 → 取私钥 → 注入解锁模板。
     *
     * @param tx          交易（新 SDK 模型）
     * @param inputIndex  输入下标
     * @param keyProvider 显式密钥提供者
     * @param hashFlags   SIGHASH 基础类型（{@code TransactionSignature.SIGHASH_*}）
     * @param anyoneCanPay 是否 ANYONECANPAY
     * @return 是否签名成功
     */
    public static boolean signInput(
            Transaction tx,
            int inputIndex,
            SigningKeyProvider keyProvider,
            int hashFlags,
            boolean anyoneCanPay) {
        if (tx == null || inputIndex < 0 || inputIndex >= tx.inputs.size()) {
            throw new SignErrorException(StrUtil.format("when signInput,invalid input index {}", inputIndex));
        }
        TransactionInput txIn = tx.inputs.get(inputIndex);
        TransactionOutput sourceOutput = sourceOutputOf(txIn);
        if (sourceOutput == null) {
            log.error("when signInput,Missing connected output, assuming input {} is already signed.", inputIndex);
            throw new SignErrorException(StrUtil.format(
                    "when signInput,Missing connected output, assuming input {} is already signed.", inputIndex));
        }
        Script scriptPubKey = sourceOutput.lockingScript == null ? new Script() : sourceOutput.lockingScript;
        // 兼容 Ordinals 铭文输入：铭文脚本以 P2PKH 前缀开头后接铭文包络（13 chunk），
        // pubKeyHashOf 只认 5 chunk 的纯 P2PKH，会返回空并把铭文输入整体跳过
        // （同一缺陷在 RemoteTransactionSigner 与 TransactionBuilder 各出现过一次，见 §96/§124）。
        byte[] pubKeyHash = pubKeyHashOf(scriptPubKey);
        if (pubKeyHash.length == 0) {
            pubKeyHash = com.metanet4j.sdk.utils.TxHelperExtend.getPubKeyHashByOrdinalScript(scriptPubKey);
        }
        if (pubKeyHash.length == 0) {
            log.warn("when signInput,script is neither P2PKH nor ordinal inscription, skip input {}", inputIndex);
            return false;
        }
        // 提供者可能以抛错表示「找不到」（见 BapProviderKeyBag）；与 key == null 同样处理
        // （本地密钥缺失即该输入签名失败），避免把原始 RuntimeException 抛给调用方。
        com.metanet4j.sdk.PrivateKey key;
        try {
            key = keyProvider == null ? null : keyProvider.findKeyFromPubHash(pubKeyHash);
        } catch (RuntimeException e) {
            key = null;
        }
        if (key == null) {
            log.warn("No local key found for input {}", inputIndex);
            return false;
        }
        txIn.unlockingScriptTemplate = new P2PKH().unlock(key.getKey(), ScriptExtend.signOutputsOf(hashFlags), anyoneCanPay,
                satoshisOf(sourceOutput));
        return true;
    }

    /**
     * 批量签名：对每个可签名输入注入解锁模板，再交给交易签名。
     *
     * @return 是否有输入签成；零输入返回 true，部分成功不代表整个交易已有效
     */
    public boolean signInputs(Transaction tx, SigningKeyProvider keyProvider, int hashFlags,
                              boolean anyoneCanPay) {
        if (tx == null) {
            return false;
        }
        boolean signedAny = false;
        for (int i = 0; i < tx.inputs.size(); i++) {
            if (!signInput(tx, i, keyProvider, hashFlags, anyoneCanPay)) {
                log.warn("Skip input {} during batch signing", i);
            } else {
                signedAny = true;
            }
        }
        try {
            tx.sign().toCompletableFuture().join();
        } catch (RuntimeException e) {
            log.warn("交易签名失败：{}", e.getMessage());
            return false;
        }
        return tx.inputs.isEmpty() || signedAny;
    }

    private static TransactionOutput sourceOutputOf(TransactionInput input) {
        // 【§87 收敛】原为四处各一份的私有副本，现委托全项目唯一实现（TxHelperExtend）。
        return com.metanet4j.sdk.utils.TxHelperExtend.sourceOutputOf(input);
    }

    private static Double satoshisOf(TransactionOutput output) {
        return output.satoshis instanceof Number number ? number.doubleValue() : null;
    }

    private static byte[] pubKeyHashOf(Script script) {
        // 【§87 收敛】原为三处各一份的私有副本，现委托全项目唯一实现。
        // 唯一实现内部用 ScriptHelper.chunksOf（**非**上游截断版 Script.getChunks()），
        // 故本处一并消除了潜在的脚本截断风险。
        return com.metanet4j.sdk.utils.TxHelperExtend.pubKeyHashOfP2pkh(script);
    }

}
