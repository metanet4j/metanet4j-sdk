package com.metanet4j.sdk.ordinals;

import com.google.common.collect.Lists;
import com.metanet4j.base.bsv.BsvAmount;
import com.metanet4j.base.bsv.BsvUtxo;
import com.metanet4j.bsv.primitives.TransactionSignature;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.transaction.Transaction;
import com.metanet4j.bsv.transaction.TransactionInput;
import com.metanet4j.bsv.transaction.TransactionOutput;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.bap.BapBaseCore;
import com.metanet4j.sdk.bap.SigningKeyProvider;
import com.metanet4j.sdk.exception.SignErrorException;
import com.metanet4j.sdk.transcation.TransactionBuilder;

/**
 * Ordinals 交易构建：追加铭文输入／输出并签名。
 *
 * <p>旧实现基于 bitcoinj 交易模型、{@code KeyBag} 与 {@code TransactionSigner}；
 * 按迁移计划 §3 改为新 SDK（com.metanet4j.bsv）交易模型 + {@link SigningKeyProvider} 显式取密钥 +
 * 每输入注入解锁模板后调用 {@code Transaction.sign()}。
 */
public class OrdTransactionBuilder extends TransactionBuilder {

    /** 铭文输出金额固定 1 satoshi。 */
    private static final BsvAmount INSCRIPTION_SATOSHI = BsvAmount.SATOSHI;

    public OrdTransactionBuilder(BapBaseCore bapBaseCore, SigningKeyProvider keyProvider) {
        super(bapBaseCore, keyProvider);
    }

    public OrdTransactionBuilder(BapBaseCore bapBaseCore, Transaction targetTransaction,
                                 SigningKeyProvider keyProvider) {
        super(bapBaseCore, targetTransaction, keyProvider);
    }

    /**
     * 追加 Ordinals 输入：锁定脚本取自 UTXO 脚本（无则留空），金额取整数 satoshi；
     * 连接输出挂在 {@code sourceTransaction} 上，供前像与解锁模板使用。
     */
    public OrdTransactionBuilder addOrdInput(BsvUtxo utxo) {
        Script lockingScript = utxo.getScript() == null
                ? new Script() : Script.fromBinary(utxo.getScript());
        TransactionOutput sourceOutput = new TransactionOutput(lockingScript, utxo.getSatoshis());
        TransactionInput input = new TransactionInput(utxo.getHash().toString(), (int) utxo.getIndex(),
                new Script());
        input.sequence = 0xffff_ffffL;
        announceSourceOutput(input, sourceOutput);
        targetTransaction.addInput(input);
        return this;
    }

    /** 追加铭文输出（金额 1 satoshi）。 */
    public OrdTransactionBuilder addOrdOutput(AddressEnhance destinationAddress,
                                              byte[] b64File,
                                              String mediaType,
                                              OrdScriptBuilder.ORDMap metaData) {
        Script inscription = OrdScriptBuilder.buildInscription(destinationAddress, b64File, mediaType,
                metaData);
        this.getTargetTransaction().addOutput(new TransactionOutput(inscription,
                INSCRIPTION_SATOSHI.toSdkValue()));
        return this;
    }

    public OrdTransactionBuilder signTx(SigningKeyProvider keyProvider, boolean useForkId)
            throws SignErrorException {
        return signTx(keyProvider, TransactionSignature.SIGHASH_ALL, useForkId, false);
    }

    /** 注入解锁模板并签名；{@code hashFlags}／{@code anyoneCanPay} 会贯通到签名范围。 */
    public OrdTransactionBuilder signTx(SigningKeyProvider keyProvider, int hashFlags,
                                        boolean useForkId, boolean anyoneCanPay)
            throws SignErrorException {
        setEmptyInputScriptHaveOrdinals(this.getTargetTransaction(), keyProvider);
        completeAndSignTx(keyProvider, hashFlags, anyoneCanPay);
        return this;
    }

    public Transaction build() {
        return this.getTargetTransaction();
    }

    /** 为每个输入注入解锁模板（取代旧 {@code createEmptyInputScript}）。 */
    protected void setEmptyInputScriptHaveOrdinals(final Transaction outputTransaction,
                                                   final SigningKeyProvider provider) {
        prepareInputsForSigning(outputTransaction, provider);
    }

    /** 把来源输出挂到输入上（新 SDK 以 sourceTransaction 携带连接输出）。 */
    private static void announceSourceOutput(TransactionInput input, TransactionOutput output) {
        Transaction source = new Transaction();
        source.outputs = Lists.newArrayList(output);
        input.sourceTransaction = source;
    }
}
