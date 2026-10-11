package com.metanet4j.sdk.transcation;

import com.google.common.collect.Lists;
import com.metanet4j.base.bsv.BsvAmount;
import com.metanet4j.base.bsv.BsvUtxo;
import com.metanet4j.bsv.script.OP;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.script.ScriptChunk;
import com.metanet4j.bsv.script.templates.P2PKH;
import com.metanet4j.bsv.primitives.TransactionSignature;
import com.metanet4j.bsv.primitives.Writer;
import com.metanet4j.bsv.transaction.Transaction;
import com.metanet4j.bsv.transaction.TransactionInput;
import com.metanet4j.bsv.transaction.TransactionOutput;
import com.metanet4j.sdk.SignType;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.bap.BapBaseCore;
import com.metanet4j.sdk.bap.SigningKeyProvider;
import com.metanet4j.sdk.exception.SignErrorException;
import com.metanet4j.sdk.script.ScriptExtend;
import com.metanet4j.sdk.sigma.Sigma;
import com.metanet4j.sdk.utxo.UTXOProvider;
import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 交易构建器。
 *
 * <p>旧实现基于 bitcoinj 的交易模型、{@code KeyBag} 反查与 {@code TransactionSigner} 遍历签名；
 * 按迁移计划 §3 全部替换为：新 SDK（com.metanet4j.bsv）交易模型、{@link SigningKeyProvider}
 * 显式取密钥、每输入注入 {@code unlockingScriptTemplate} 后调用 {@code Transaction.sign()}；
 * 金额统一 {@link BsvAmount}（整数 satoshi）。
 */
@Data
@Slf4j
public class TransactionBuilder {

    protected BapBaseCore bapBaseCore;

    /** 签名密钥提供者（取代旧 {@code KeyBag} 反查）。 */
    protected SigningKeyProvider keyProvider;

    protected Transaction targetTransaction;

    protected AddressEnhance changeAddress;

    protected BsvAmount feePerKb = BsvAmount.valueOf(500);

    protected Sigma sigma;

    private BsvAmount changeAmount = BsvAmount.ZERO;
    private BsvAmount fee = BsvAmount.ZERO;

    public TransactionBuilder(BapBaseCore bapBaseCore, SigningKeyProvider keyProvider) {
        this(bapBaseCore, null, keyProvider);
    }

    public TransactionBuilder(BapBaseCore bapBaseCore, Transaction targetTransaction,
                              SigningKeyProvider keyProvider) {
        this.bapBaseCore = bapBaseCore;
        this.targetTransaction = targetTransaction == null ? new Transaction() : targetTransaction;
        this.keyProvider = keyProvider;
    }

    public TransactionBuilder addInput(AddressEnhance address,
                                       UTXOProvider<AddressEnhance, BsvUtxo> utxoProvider) {
        addInputs(utxoProvider.listUxtos(Lists.newArrayList(address)));
        return this;
    }

    public TransactionBuilder addInputs(List<AddressEnhance> addressList,
                                        UTXOProvider<AddressEnhance, BsvUtxo> utxoProvider) {
        addInputs(utxoProvider.listUxtos(addressList));
        return this;
    }

    public TransactionBuilder addInputs(List<BsvUtxo> utxoList) {
        utxoList.forEach(this::addInput);
        return this;
    }

    /**
     * 由 UTXO 追加输入：锁定脚本按 UTXO 地址生成 P2PKH（无地址时取脚本，均无则留空）。
     * 金额与 outpoint 直接取自 UTXO（整数 satoshi + 显示序 txid）。
     */
    public TransactionBuilder addInput(BsvUtxo utxo) {
        Script lockingScript = new Script();
        if (utxo.getAddress() != null) {
            try {
                AddressEnhance address = AddressEnhance.fromBase58(utxo.getAddress());
                lockingScript = new P2PKH().lock(address.toBase58());
            } catch (RuntimeException e) {
                log.warn("UTXO 地址无法解析，输入锁定脚本留空：{}", utxo.getAddress());
            }
        } else if (utxo.getScript() != null) {
            lockingScript = Script.fromBinary(utxo.getScript());
        }
        TransactionInput input = new TransactionInput(utxo.getHash().toString(), (int) utxo.getIndex(),
                new Script());
        input.sequence = 0xffff_ffffL;
        attachSourceOutput(input, new TransactionOutput(lockingScript, utxo.getSatoshis()));
        targetTransaction.addInput(input);
        return this;
    }

    /** 追加一个已有输出作为花费来源。 */
    public TransactionBuilder addInput(TransactionOutput output) {
        TransactionInput input = new TransactionInput(null, 0, new Script());
        input.sequence = 0xffff_ffffL;
        attachSourceOutput(input, output);
        targetTransaction.addInput(input);
        return this;
    }

    public TransactionBuilder addOutput(BsvAmount value, AddressEnhance address) {
        targetTransaction.addOutput(new TransactionOutput(new P2PKH().lock(address.toBase58()),
                value.toSdkValue()));
        return this;
    }

    public TransactionBuilder addDataOutput(LockingScriptBuilder lockingScriptBuilder) {
        targetTransaction.addOutput(new TransactionOutput(lockingScriptBuilder.getLockingScript(), 0d));
        return this;
    }

    public TransactionBuilder addSigmaSign(BapBaseCore bapBase) {
        Sigma localSigma = new Sigma(bapBase, targetTransaction, SignType.CURRENT);
        targetTransaction = localSigma.sign().getSignedTx();
        this.sigma = localSigma;
        return this;
    }

    public TransactionBuilder changeAddress(AddressEnhance address) {
        addChangeOutput(address);
        return this;
    }

    public void addChangeOutput(AddressEnhance address) {
        this.changeAddress = address;
        BsvAmount change = calculateChangeAmount(targetTransaction, address, this.feePerKb);
        targetTransaction.addOutput(new TransactionOutput(new P2PKH().lock(address.toBase58()),
                change.toSdkValue()));
    }

    /**
     * 计算找零：出大小 = 找零输出字节 + 输出数的 CompactSize + 交易序列化长度 + 各输入解锁脚本估算。
     * 金额全程用 {@link BsvAmount} 保证整数精度。
     */
    public BsvAmount calculateChangeAmount(Transaction transaction, AddressEnhance address,
                                           BsvAmount feePerKb) {
        long size = 0;
        size += outputSize(new TransactionOutput(new P2PKH().lock(address.toBase58()), 0d));
        size += Writer.varIntNum(transaction.outputs.size() + 1).size();
        size += transaction.toBinary().size();
        for (TransactionInput transactionInput : transaction.inputs) {
            size += getUnLockScriptSize(transactionInput, lockingScriptOf(transactionInput));
        }
        BsvAmount localFee = BsvAmount.valueOf((long) Math.ceil(size * feePerKb.getValue() / 1000.0));
        this.fee = localFee;
        this.changeAmount = inputSum(transaction).subtract(outputSum(transaction)).subtract(localFee);
        return this.changeAmount;
    }

    /**
     * 估算解锁脚本字节数（手续费估算，不参与签名）。
     *
     * <p>口径由 {@code TransactionBuilderContractTest} 反向确认（见 migration-rules §12）。
     */
    protected long getUnLockScriptSize(TransactionInput transactionInput, Script script) {
        // 能取到私钥时按实际公钥长度计费；取不到时保守按 65 字节（由 ScriptExtend 处理 pubKeyLen<=0）
        byte[] pubKeyHash = pubKeyHashWithOrdinalsFallback(script);
        int pubKeyLen = -1;
        if (keyProvider != null && pubKeyHash.length > 0) {
            // 提供者可能以抛错表示「找不到」（见 BapProviderKeyBag）；费用估算不应因此中断，
            // 视同「取不到密钥」走保守估算（与 pubKeyLen<=0 的既有口径一致）。
            com.metanet4j.sdk.PrivateKey key;
            try {
                key = keyProvider.findKeyFromPubHash(pubKeyHash);
            } catch (RuntimeException e) {
                key = null;
            }
            if (key != null) {
                pubKeyLen = key.getPubKey().length;
            }
        }
        try {
            return ScriptExtend.getNumberOfBytesRequiredToSpend(script, pubKeyLen, null);
        } catch (RuntimeException e) {
            // 手续费估算不得因非标准锁定脚本而中断构链：退回保守的最大解锁脚本估算
            // （签名 + 非压缩公钥），与「取不到密钥」时的口径一致。
            log.debug("锁定脚本类型未识别，费用按保守值估算：{}", e.getMessage());
            return ScriptExtend.SIG_SIZE + 65;
        }
    }

    public BsvAmount getFee() {
        return this.fee;
    }

    public TransactionBuilder withFeeKb(BsvAmount feePerKb) {
        this.feePerKb = feePerKb;
        return this;
    }

    /**
     * 完成并签名：为每个可签名输入注入解锁模板，再交给新 SDK 签名。
     *
     * @throws SignErrorException 任一输入缺少可用密钥或签名失败
     */
    public Transaction completeAndSignTx(SigningKeyProvider provider, boolean useForkId)
            throws SignErrorException {
        return completeAndSignTx(provider, TransactionSignature.SIGHASH_ALL, false);
    }

    /**
     * 完成并签名，并指定签名的范围。
     *
     * @param hashFlags   SIGHASH 基础类型（{@code TransactionSignature.SIGHASH_ALL／NONE／SINGLE}）
     * @param anyoneCanPay 是否 ANYONECANPAY
     */
    public Transaction completeAndSignTx(SigningKeyProvider provider, int hashFlags,
                                         boolean anyoneCanPay) throws SignErrorException {
        prepareInputsForSigning(targetTransaction, provider, hashFlags, anyoneCanPay);
        try {
            targetTransaction.sign().toCompletableFuture().join();
        } catch (RuntimeException e) {
            // 【R6 改进】原实现抛出无 message、无 cause 的 SignErrorException，
            // 把"签名为何失败"的关键信息完全丢弃（任何调用方都只能看到一句无信息量的 SignError）。
            // 现改为携带原异常，保留根因的类与消息；SignErrorException 的类型契约不变。
            throw new SignErrorException(String.valueOf(e), e);
        }
        return targetTransaction;
    }

    /** 为每个输入注入解锁模板（取代旧 {@code setEmptyInputScript}），签名范围按 {@code ALL}。 */
    protected void prepareInputsForSigning(Transaction transaction, SigningKeyProvider provider) {
        prepareInputsForSigning(transaction, provider, TransactionSignature.SIGHASH_ALL, false);
    }

    /**
     * 为每个输入注入解锁模板（取代旧 {@code setEmptyInputScript}）。
     *
     * <p>签名范围必须传进解锁模板，否则 SIGHASH 选择会被静默丢弃（旧流程由
     * {@code TransactionSigner} 携带，新流程只能在此处传递）。
     */
    protected void prepareInputsForSigning(Transaction transaction, SigningKeyProvider provider,
                                           int hashFlags, boolean anyoneCanPay) {
        if (provider == null) {
            throw new IllegalArgumentException("签名密钥提供者不能为空");
        }
        String signOutputs = signOutputsOf(hashFlags);
        for (int i = 0; i < transaction.inputs.size(); i++) {
            TransactionInput input = transaction.inputs.get(i);
            byte[] pubKeyHash = pubKeyHashWithOrdinalsFallback(lockingScriptOf(input));
            if (pubKeyHash.length == 0) {
                continue;
            }
            // 注意：部分提供者（如 BapProviderKeyBag）在解析不到密钥时**抛 RuntimeException**
            // 而不是返回 null（BapProviderKeyBagTest 明确要求该行为）。
            // 但本层的契约是「签名失败以 SignErrorException 上报」，故此处需把两种表现统一转换，
            // 否则调用方拿到的是原始 RuntimeException，与契约不符。
            com.metanet4j.sdk.PrivateKey key;
            try {
                key = provider.findKeyFromPubHash(pubKeyHash);
            } catch (RuntimeException e) {
                throw new SignErrorException("交易输入 " + i + " 找不到可签名密钥", e);
            }
            if (key == null) {
                throw new SignErrorException("交易输入 " + i + " 找不到可签名密钥");
            }
            input.unlockingScriptTemplate = new P2PKH().unlock(key.getKey(), signOutputs,
                    anyoneCanPay, satoshisOf(input));
        }
    }

    /** SIGHASH 基础类型映射为解锁模板的 {@code signOutputs} 取值（与 ScriptExtend 共用同一实现）。 */
    private static String signOutputsOf(int hashFlags) {
        return ScriptExtend.signOutputsOf(hashFlags);
    }

    public static String broadcast(String raw, Broadcaster broadcaster) {
        return broadcaster.broadcast(raw);
    }

    public Sigma getSigma() {
        return sigma;
    }

    /** 序列化一个输出所占字节数：金额 8 字节 + CompactSize 长度 + 锁定脚本。 */
    private static long outputSize(TransactionOutput output) {
        byte[] script = output.lockingScript == null ? new byte[0] : output.lockingScript.toUint8Array();
        return 8L + Writer.varIntNum(script.length).size() + script.length;
    }

    /** 把来源输出挂到输入上（新 SDK 用 sourceTransaction 携带连接输出）。 */
    private static void attachSourceOutput(TransactionInput input, TransactionOutput output) {
        // 【R6 修正】合成来源交易时，**必须把输出放在 input.sourceOutputIndex 所指的位置**——
        // 原实现固定放在 0 号，而 UTXO 的 vout 常不为 0（如测试夹具的 "vout": 1），
        // 于是 TxHelperExtend.sourceOutputOf 按索引取值时越界、取不到金额与锁定脚本
        //（表现为找零为负 ⇒ "satoshis must be a positive integer or zero"）。
        Transaction source = new Transaction();
        List<TransactionOutput> outputs = Lists.newArrayList();
        int index = Math.max(input.sourceOutputIndex, 0);
        for (int i = 0; i < index; i++) {
            outputs.add(new TransactionOutput(new Script(), 0d));   // 占位，使目标索引存在
        }
        outputs.add(output);
        source.outputs = outputs;
        input.sourceTransaction = source;
    }

    /** 输入的连接输出锁定脚本；无可解析输出时返回空脚本。 */
    private static Script lockingScriptOf(TransactionInput input) {
        TransactionOutput output = sourceOutputOf(input);
        return output == null || output.lockingScript == null ? new Script() : output.lockingScript;
    }

    /** 输入的连接输出金额；未知返回 {@code null}。 */
    private static Double satoshisOf(TransactionInput input) {
        TransactionOutput output = sourceOutputOf(input);
        if (output != null && output.satoshis instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private static TransactionOutput sourceOutputOf(TransactionInput input) {
        // 【§87 收敛】原为四处各一份的私有副本，现委托全项目唯一实现（TxHelperExtend）。
        return com.metanet4j.sdk.utils.TxHelperExtend.sourceOutputOf(input);
    }

    /** P2PKH 锁定脚本的公钥哈希；非 P2PKH 返回空数组。 */
    private static byte[] pubKeyHashOf(Script script) {
        // 【§87 收敛】原为三处各一份的私有副本，现委托全项目唯一实现。
        // 唯一实现内部用 ScriptHelper.chunksOf（**非**上游截断版 Script.getChunks()），
        // 故本处一并消除了潜在的脚本截断风险。
        return com.metanet4j.sdk.utils.TxHelperExtend.pubKeyHashOfP2pkh(script);
    }

    /** 输入金额合计（整数 satoshi）。 */
    private static BsvAmount inputSum(Transaction transaction) {
        BsvAmount sum = BsvAmount.ZERO;
        for (TransactionInput input : transaction.inputs) {
            Double satoshis = satoshisOf(input);
            sum = sum.add(BsvAmount.valueOf(satoshis == null ? 0L : satoshis.longValue()));
        }
        return sum;
    }

    /** 输出金额合计（整数 satoshi）。 */
    private static BsvAmount outputSum(Transaction transaction) {
        BsvAmount sum = BsvAmount.ZERO;
        for (TransactionOutput output : transaction.outputs) {
            long satoshis = output.satoshis instanceof Number number ? number.longValue() : 0L;
            sum = sum.add(BsvAmount.valueOf(satoshis));
        }
        return sum;
    }

    @FunctionalInterface
    public interface Broadcaster {
        String broadcast(String raw);
    }

    /**
     * 取锁定脚本对应的公钥哈希，**兼容 Ordinals 铭文输入**。
     *
     * <p>铭文脚本以 P2PKH 前缀开头后接铭文包络（13 个 chunk），`pubKeyHashOf` 只认 5 chunk 的
     * 纯 P2PKH，会返回空；旧实现在此处只认 P2PKH，导致**铭文输入被整体跳过、解锁脚本始终为空**
     * （与 RemoteTransactionSigner 修过的同类问题一致，见 migration-rules §96）。
     */
    private static byte[] pubKeyHashWithOrdinalsFallback(Script script) {
        byte[] pubKeyHash = pubKeyHashOf(script);
        if (pubKeyHash.length == 0) {
            pubKeyHash = com.metanet4j.sdk.utils.TxHelperExtend.getPubKeyHashByOrdinalScript(script);
        }
        return pubKeyHash;
    }

}
