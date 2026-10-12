package com.metanet4j.sdk.remote;

import cn.hutool.core.codec.Base64;
import com.metanet4j.bsv.primitives.Signature;
import com.metanet4j.bsv.primitives.TransactionSignature;
import com.metanet4j.bsv.script.OP;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.script.ScriptChunk;
import com.metanet4j.bsv.script.templates.P2PKH;
import com.metanet4j.bsv.transaction.Transaction;
import com.metanet4j.bsv.transaction.TransactionInput;
import com.metanet4j.bsv.transaction.TransactionOutput;
import com.metanet4j.sdk.PrivateKey;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.bap.SigningKeyProvider;
import com.metanet4j.sdk.context.PreSignHashContext;
import com.metanet4j.sdk.script.ScriptExtend;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 远程签名器：按预签名上下文把远程返回的签名回填到输入。
 *
 * <p>旧实现继承 bitcoinj 的 {@code TransactionSigner} 并遍历输入签名；按迁移计划 §3，
 * 该抽象已取消，改为「按输入的公钥哈希取回签名 → 注入解锁模板 → 交给交易签名」，
 * 因此本类改为普通业务组件（不再实现旧接口）。
 */
public class RemoteTransactionSigner {

    private static final Logger log = LoggerFactory.getLogger(RemoteTransactionSigner.class);

    /**
     * 回填远程签名。
     *
     * @param tx          待签名交易（新 SDK 模型）
     * @param hashFlags   SIGHASH 基础类型（{@code TransactionSignature.SIGHASH_*}）
     * @param anyoneCanPay 是否 ANYONECANPAY
     * @return 是否有输入完成回填；零输入返回 true，部分成功不代表整个交易已有效
     */
    public boolean signInputs(Transaction tx, SigningKeyProvider keyProvider, int hashFlags,
                              boolean anyoneCanPay) {
        if (tx == null) {
            return false;
        }
        boolean signedAny = false;
        for (int i = 0; i < tx.inputs.size(); i++) {
            TransactionInput txIn = tx.inputs.get(i);
            TransactionOutput sourceOutput = sourceOutputOf(txIn);
            if (sourceOutput == null) {
                log.warn("Missing connected output, assuming input {} is already signed.", i);
                continue;
            }
            Script scriptPubKey = sourceOutput.lockingScript == null
                    ? new Script() : sourceOutput.lockingScript;
            // 兼容两类输入：标准 P2PKH 与 Ordinals 铭文脚本
            // （铭文脚本以 P2PKH 前缀开头后接铭文包络，其公钥哈希在固定位置）。
            // 旧实现在此只认 P2PKH，会把铭文输入整体跳过、导致该输入没有签名。
            byte[] pubKeyHash = pubKeyHashOf(scriptPubKey);
            if (pubKeyHash.length == 0) {
                pubKeyHash = com.metanet4j.sdk.utils.TxHelperExtend.getPubKeyHashByOrdinalScript(scriptPubKey);
            }
            if (pubKeyHash.length == 0) {
                log.warn("输入 {} 的锁定脚本既非 P2PKH 也非 Ordinals 铭文，跳过远程签名回填。", i);
                continue;
            }
            PreSignHashContext context;
            try {
                // 按与 RemoteTransactionBuilder.getTxInputSignHash 相同的口径重算该输入的前像哈希，
                // 再查预签名上下文；此前用 ZERO_HASH 查询，永远匹配不到任何上下文。
                com.metanet4j.base.bsv.BsvHashBytes preSignHash =
                        com.metanet4j.sdk.script.SigHashExtend.hashForForkIdSignature(tx, i,
                                scriptPubKey.toUint8Array(),
                                com.metanet4j.base.bsv.BsvAmount.fromSdkValue(sourceOutput.satoshis),
                                hashFlags, anyoneCanPay);
                context = PreSignHashUtils.getSignedHashContext(preSignHash);
            } catch (RuntimeException e) {
                log.warn("输入 {} 缺少预签名上下文：{}", i, e.getMessage());
                continue;
            }
            // 远程流程**不依赖本地私钥**：预签名上下文里已带远程服务返回的公钥，
            // 用它拼装解锁脚本即可（keyProvider 为空或解析不到密钥时不应跳过该输入）。
            PrivateKey key = keyProvider == null ? null : keyProvider.findKeyFromPubHash(pubKeyHash);
            byte[] pubKeyBytes = context.getPublicKey() != null
                    ? context.getPublicKey().getPubKeyBytes()
                    : key == null ? null : key.getPubKey();
            if (pubKeyBytes == null) {
                log.warn("输入 {} 既无预签名上下文公钥、也无本地密钥，跳过远程签名回填。", i);
                continue;
            }
            // 远程返回的是 DER（含 sighash 尾字节）的 Base64
            Signature signature = Signature.fromDER(Base64.decode(context.getSigB64()));
            if (key != null) {
                txIn.unlockingScriptTemplate = new P2PKH().unlock(key.getKey(),
                        ScriptExtend.signOutputsOf(hashFlags), anyoneCanPay, satoshisOf(sourceOutput));
            }
            // toChecksigFormat 是子类 TransactionSignature 的能力：签名器产出即该类型
            List<Integer> checksig = new java.util.ArrayList<>();
            if (signature instanceof com.metanet4j.bsv.primitives.TransactionSignature ts) {
                checksig.addAll(ts.toChecksigFormat());
            } else {
                // 上下文中存的是**纯 DER**（远程服务返回），解锁脚本需要 DER + SIGHASH 尾字节；
                // 缺少尾字节会让脚本无法通过校验（且尾字节本身就是范围声明）。
                for (Double b : signature.toDER()) {
                    checksig.add(b.intValue());
                }
                checksig.add(ScriptExtend.signOutputsOf(hashFlags).equals("single")
                        ? (TransactionSignature.SIGHASH_SINGLE | TransactionSignature.SIGHASH_FORKID
                           | (anyoneCanPay ? TransactionSignature.SIGHASH_ANYONECANPAY : 0))
                        : (TransactionSignature.SIGHASH_ALL | TransactionSignature.SIGHASH_FORKID
                           | (anyoneCanPay ? TransactionSignature.SIGHASH_ANYONECANPAY : 0)));
            }
            txIn.unlockingScript = new Script().writeBin(checksig)
                    .writeBin(toBoxed(pubKeyBytes));
            signedAny = true;
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
        // 注意：唯一实现内部用的是 ScriptHelper.chunksOf（**非**上游截断版 Script.getChunks()），
        // 故本处一并消除了一处潜在的脚本截断风险。
        return com.metanet4j.sdk.utils.TxHelperExtend.pubKeyHashOfP2pkh(script);
    }

    private static List<Integer> toBoxed(byte[] data) {
        List<Integer> out = new java.util.ArrayList<>(data.length);
        for (byte b : data) {
            out.add(b & 0xFF);
        }
        return out;
    }
}
