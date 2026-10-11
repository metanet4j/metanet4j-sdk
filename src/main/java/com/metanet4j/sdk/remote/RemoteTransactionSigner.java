package com.metanet4j.sdk.remote;

import cn.hutool.core.codec.Base64;
import com.metanet4j.bsv.primitives.Signature;
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
     * @return 是否全部输入都已具备签名
     */
    public boolean signInputs(Transaction tx, SigningKeyProvider keyProvider, int hashFlags,
                              boolean anyoneCanPay) {
        if (tx == null) {
            return false;
        }
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
            PrivateKey key = keyProvider == null ? null : keyProvider.findKeyFromPubHash(pubKeyHash);
            if (key == null) {
                log.warn("输入 {} 找不到可签名密钥。", i);
                continue;
            }
            // 远程返回的是 DER（含 sighash 尾字节）的 Base64
            Signature signature = Signature.fromDER(Base64.decode(context.getSigB64()));
            txIn.unlockingScriptTemplate = new P2PKH().unlock(key.getKey(), ScriptExtend.signOutputsOf(hashFlags), anyoneCanPay,
                    satoshisOf(sourceOutput));
            // toChecksigFormat 是子类 TransactionSignature 的能力：签名器产出即该类型
            List<Integer> checksig = new java.util.ArrayList<>();
            if (signature instanceof com.metanet4j.bsv.primitives.TransactionSignature ts) {
                checksig.addAll(ts.toChecksigFormat());
            } else {
                for (Double b : signature.toDER()) {
                    checksig.add(b.intValue());
                }
            }
            txIn.unlockingScript = new Script().writeBin(checksig)
                    .writeBin(toBoxed(key.getPubKey()));
        }
        return true;
    }

    private static TransactionOutput sourceOutputOf(TransactionInput input) {
        if (input.sourceTransaction instanceof Transaction source && source.outputs != null
                && !source.outputs.isEmpty()) {
            return source.outputs.get(0);
        }
        return null;
    }

    private static Double satoshisOf(TransactionOutput output) {
        return output.satoshis instanceof Number number ? number.doubleValue() : null;
    }

    private static byte[] pubKeyHashOf(Script script) {
        List<ScriptChunk> chunks = script.getChunks();
        if (chunks.size() == 5
                && chunks.get(0).op == OP.OP_DUP
                && chunks.get(1).op == OP.OP_HASH160
                && chunks.get(2).data != null && chunks.get(2).data.size() == 20
                && chunks.get(3).op == OP.OP_EQUALVERIFY
                && chunks.get(4).op == OP.OP_CHECKSIG) {
            byte[] hash = new byte[20];
            for (int i = 0; i < hash.length; i++) {
                hash[i] = (byte) (int) chunks.get(2).data.get(i);
            }
            return hash;
        }
        return new byte[0];
    }

    private static List<Integer> toBoxed(byte[] data) {
        List<Integer> out = new java.util.ArrayList<>(data.length);
        for (byte b : data) {
            out.add(b & 0xFF);
        }
        return out;
    }
}
