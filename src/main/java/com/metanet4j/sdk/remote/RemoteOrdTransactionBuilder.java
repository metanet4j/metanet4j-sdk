package com.metanet4j.sdk.remote;

import com.metanet4j.base.bsv.BsvAmount;
import com.metanet4j.base.bsv.BsvUtxo;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.script.templates.P2PKH;
import com.metanet4j.bsv.primitives.TransactionSignature;
import com.metanet4j.bsv.transaction.Transaction;
import com.metanet4j.bsv.transaction.TransactionInput;
import com.metanet4j.bsv.transaction.TransactionOutput;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.bap.BapBaseCore;
import com.metanet4j.sdk.bap.RemoteBapBase;
import com.metanet4j.sdk.bap.SigningKeyProvider;
import com.metanet4j.sdk.context.PreSignHashContext;
import com.metanet4j.sdk.exception.SignErrorException;
import com.metanet4j.sdk.ordinals.OrdScriptBuilder;
import com.metanet4j.sdk.sigma.Sigma;

/** 远程 Ordinals 交易构建：追加铭文输入／输出并生成预签名哈希。 */
public class RemoteOrdTransactionBuilder extends RemoteTransactionBuilder {

    public RemoteOrdTransactionBuilder(RemoteBapBase bapBaseCore,
                                       SigningKeyProvider keyProvider) {
        super(bapBaseCore, keyProvider);
    }

    /**
     * 追加 Ordinals 输入：锁定脚本取自 UTXO 脚本，金额取整数 satoshi。
     * 新 SDK 把 outpoint 拆为 sourceTXID + sourceOutputIndex，连接输出挂在 sourceTransaction 上。
     */
    public RemoteOrdTransactionBuilder addOrdInput(BsvUtxo utxo) {
        Script lockingScript = utxo.getScript() == null
                ? new Script() : Script.fromBinary(utxo.getScript());
        TransactionOutput sourceOutput = new TransactionOutput(lockingScript, utxo.getSatoshis());
        TransactionInput input = new TransactionInput(utxo.getHash().toString(), (int) utxo.getIndex(),
                new Script());
        input.sequence = 0xffff_ffffL;
        attachSourceOutput(input, sourceOutput);
        this.getTargetTransaction().addInput(input);
        return this;
    }

    /** 追加铭文输出（金额 1 satoshi）。 */
    public RemoteOrdTransactionBuilder addOrdOutput(AddressEnhance destinationAddress,
                                                    byte[] b64File,
                                                    String mediaType,
                                                    OrdScriptBuilder.ORDMap metaData,
                                                    RemoteSignType remoteSignType) {
        Script inscription = OrdScriptBuilder.buildInscription(destinationAddress, b64File, mediaType,
                metaData);
        this.getTargetTransaction().addOutput(new TransactionOutput(inscription,
                BsvAmount.SATOSHI.toSdkValue()));
        this.bsmSignHashContexts.add(getSigmaPreSignHash(this.bapBaseCore, remoteSignType));
        return this;
    }

    public RemoteTransactionBuilder addOrdOutput(AddressEnhance destinationAddress,
                                                 byte[] b64File,
                                                 String mediaType,
                                                 OrdScriptBuilder.ORDMap metaData) {
        return addOrdOutput(destinationAddress, b64File, mediaType, metaData, null);
    }

    private PreSignHashContext getSigmaPreSignHash(BapBaseCore bapBase, RemoteSignType remoteSignType) {
        Sigma sigma = new Sigma(bapBase, targetTransaction, true, remoteSignType);
        return sigma.getPreSignHashContext();
    }

    public RemoteOrdTransactionBuilder signTx(SigningKeyProvider keyProvider, boolean useForkId)
            throws SignErrorException {
        return signTx(keyProvider, TransactionSignature.SIGHASH_ALL, useForkId, false);
    }

    /** 远程签名：按预签名哈希回填（密钥由 {@link SigningKeyProvider} 显式提供）。 */
    public RemoteOrdTransactionBuilder signTx(SigningKeyProvider keyProvider, int hashFlags,
                                              boolean useForkId, boolean anyoneCanPay)
            throws SignErrorException {
        if (!this.getRemoteTransactionSigner().signInputs(this.getTargetTransaction(), keyProvider,
                hashFlags, anyoneCanPay)) {
            throw new SignErrorException();
        }
        return this;
    }

    public Transaction build() {
        return this.getTargetTransaction();
    }
}
