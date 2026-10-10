package com.metanet4j.sdk.remote;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.metanet4j.sdk.bap.BapBase;
import com.metanet4j.sdk.bap.BapBaseCore;
import com.metanet4j.sdk.bap.RemoteBapBase;
import com.metanet4j.sdk.ordinals.OrdScriptBuilder;
import com.metanet4j.sdk.ordinals.OrdTransactionBuilder;
import com.metanet4j.sdk.signers.OrdinalsTransactionSigner;
import com.metanet4j.sdk.transcation.BapDataLockBuilder;
import com.metanet4j.sdk.utxo.UTXOProvider;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.base.bsv.BsvAmount;
import com.metanet4j.base.bsv.BsvUtxo;
import com.metanet4j.bsv.transaction.Transaction;
import com.metanet4j.sdk.bap.SigningKeyProvider;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
public class RemoteOrdTransactionTemplate {
    private boolean doBsmSign;
    private boolean doInputSign;

    public RemoteOrdTransactionTemplate() {
        this.doBsmSign = true;
        this.doInputSign = true;
    }

    public RemoteOrdTransactionTemplate(boolean doBsmSign, boolean doInputSign) {
        this.doBsmSign = doBsmSign;
        this.doInputSign = doInputSign;
    }


    public RemoteOrdTransactionBuilder createOrdinal(
            List<AddressEnhance> payAddressEnhanceList,
            UTXOProvider<AddressEnhance, BsvUtxo> utxoProvider,
            AddressEnhance destinationAddress,
            AddressEnhance changeAddress,
            long feePerKb,
            OrdScriptBuilder.Inscription inscription,
            OrdScriptBuilder.ORDMap metaData,
            RemoteBapBase remoteBapBase,
            RemoteSignType remoteSignType

    ) {

        return createOrdinal(payAddressEnhanceList, utxoProvider, destinationAddress, changeAddress, feePerKb, inscription, metaData, remoteBapBase, new RemoteTransactionSigner(), remoteSignType);
    }


    public RemoteOrdTransactionBuilder createOrdinal(
            List<AddressEnhance> payAddressEnhanceList,
            UTXOProvider<AddressEnhance, BsvUtxo> utxoProvider,
            AddressEnhance destinationAddress,
            AddressEnhance changeAddress,
            long feePerKb,
            OrdScriptBuilder.Inscription inscription,
            OrdScriptBuilder.ORDMap metaData,
            RemoteBapBase bapBase,
            RemoteTransactionSigner transactionSigner,
            RemoteSignType remoteSignType

    ) {
        preTxBuild(transactionSigner, remoteSignType);

        RemoteOrdTransactionBuilder transactionBuilder = new RemoteOrdTransactionBuilder(bapBase, transactionSigner);
        transactionBuilder
                .addInputs(utxoProvider.listUxtos(payAddressEnhanceList));
        transactionBuilder.addOrdOutput(destinationAddress, Base64.decode(inscription.getDataB64()), inscription.getContentType(), metaData, RemoteSignType.CURRENT);


        return postTxBuild(changeAddress, feePerKb, bapBase, remoteSignType, this.doBsmSign, this.doInputSign, transactionBuilder);


    }


    public Transaction sendOrdinal(
            RemoteBapBase bapBase,
            BsvUtxo ordinalUtxo,
            BsvUtxo paymentUtxo,
            AddressEnhance destinationAddress,
            AddressEnhance changeAddress,
            long feePerKb,
            OrdScriptBuilder.Inscription reInscription,
            OrdScriptBuilder.ORDMap metaData,
            RemoteSignType remoteSignType
    ) {
        return prepareSendOrdinal(bapBase, ordinalUtxo, paymentUtxo, destinationAddress, changeAddress, feePerKb, reInscription, metaData, new RemoteTransactionSigner(), remoteSignType).build();
    }


    public RemoteOrdTransactionBuilder prepareSendOrdinal(
            RemoteBapBase bapBase,
            BsvUtxo ordinalUtxo,
            BsvUtxo paymentUtxo,
            AddressEnhance destinationAddress,
            AddressEnhance changeAddress,
            long feePerKb,
            OrdScriptBuilder.Inscription reInscription,
            OrdScriptBuilder.ORDMap metaData,
            RemoteSignType remoteSignType
    ) {
        return prepareSendOrdinal(bapBase, ordinalUtxo, paymentUtxo, destinationAddress, changeAddress, feePerKb, reInscription, metaData, new RemoteTransactionSigner(), remoteSignType);
    }


    public RemoteOrdTransactionBuilder prepareSendOrdinal(
            RemoteBapBase bapBase,
            BsvUtxo ordinalUtxo,
            BsvUtxo paymentUtxo,
            AddressEnhance destinationAddress,
            AddressEnhance changeAddress,
            long feePerKb,
            OrdScriptBuilder.Inscription reInscription,
            OrdScriptBuilder.ORDMap metaData,
            RemoteTransactionSigner transactionSigner,
            RemoteSignType remoteSignType
    ) {
        preTxBuild(transactionSigner, remoteSignType);

        RemoteOrdTransactionBuilder transactionBuilder = new RemoteOrdTransactionBuilder(bapBase, transactionSigner);
        transactionBuilder.addOrdInput(ordinalUtxo);
        transactionBuilder.addInput(paymentUtxo);

        if (reInscription != null && StrUtil.isNotEmpty(reInscription.getDataB64()) && StrUtil.isNotEmpty(reInscription.getContentType())) {
            transactionBuilder.addOrdOutput(destinationAddress, Base64.decode(reInscription.getDataB64()), reInscription.getContentType(), metaData);
        } else {
            transactionBuilder.addOutput(BsvAmount.SATOSHI, destinationAddress);
        }

        return postTxBuild(changeAddress, feePerKb, bapBase, remoteSignType, this.doBsmSign, this.doInputSign, transactionBuilder);
    }


    public Transaction sendOrdinal(
            BapBaseCore bapBase,
            BsvUtxo ordinalUtxo,
            BsvUtxo paymentUtxo,
            AddressEnhance destinationAddress,
            AddressEnhance changeAddress,
            long feePerKb,
            OrdScriptBuilder.Inscription reInscription,
            OrdScriptBuilder.ORDMap metaData,
            SigningKeyProvider keyProvider) {
        return sendOrdinal(bapBase, ordinalUtxo, paymentUtxo, destinationAddress, changeAddress, feePerKb, reInscription, metaData, keyProvider, null);

    }

    public Transaction sendOrdinal(
            BapBaseCore bapBase,
            BsvUtxo ordinalUtxo,
            BsvUtxo paymentUtxo,
            AddressEnhance destinationAddress,
            AddressEnhance changeAddress,
            long feePerKb,
            OrdScriptBuilder.Inscription reInscription,
            OrdScriptBuilder.ORDMap metaData,
            SigningKeyProvider keyProvider,
            RemoteTransactionSigner transactionSigner,
            RemoteSignType remoteSignType) {
        preTxBuild(transactionSigner, remoteSignType);

        OrdTransactionBuilder transactionBuilder = new OrdTransactionBuilder(bapBase, keyProvider);
        transactionBuilder
                .addOrdInput(ordinalUtxo);
        transactionBuilder.addInput(paymentUtxo);

        if (reInscription != null && StrUtil.isNotEmpty(reInscription.getDataB64()) && StrUtil.isNotEmpty(reInscription.getContentType())) {
            transactionBuilder.addOrdOutput(destinationAddress, Base64.decode(reInscription.getDataB64()), reInscription.getContentType(), metaData);
        } else {
            transactionBuilder.addOutput(BsvAmount.SATOSHI, destinationAddress);
        }
        transactionBuilder.withFeeKb(BsvAmount.valueOf(feePerKb))
                .changeAddress(changeAddress);

        transactionBuilder.signTx(keyProvider, com.metanet4j.bsv.primitives.TransactionSignature.SIGHASH_SINGLE, true, true);
        return transactionBuilder.build();
    }

    @Deprecated
    public Transaction sendUtxos(
            BapBase bapBase,
            List<BsvUtxo> utxos,
            AddressEnhance destinationAddress,
            long feePerByte,
            SigningKeyProvider keyProvider) throws Exception {
        OrdTransactionBuilder transactionBuilder = new OrdTransactionBuilder(bapBase, keyProvider);
        transactionBuilder.addInputs(utxos);
        transactionBuilder.withFeeKb(BsvAmount.valueOf(feePerByte));
        transactionBuilder.changeAddress(destinationAddress);

        transactionBuilder.signTx(keyProvider, com.metanet4j.bsv.primitives.TransactionSignature.SIGHASH_ALL, true, true);
        return transactionBuilder.build();
    }


    /**
     * bap + 1sat ordinal
     * content-type:application/bap
     *
     * @param payAddressEnhanceList
     * @param utxoProvider
     * @param destinationAddress
     * @param changeAddress
     * @param feePerKb
     * @param bapBase
     * @return
     */
    public Transaction createBapOrdinal(List<AddressEnhance> payAddressEnhanceList,
                                        UTXOProvider<AddressEnhance, BsvUtxo> utxoProvider,
                                        AddressEnhance destinationAddress,
                                        AddressEnhance changeAddress,
                                        long feePerKb,
                                        RemoteBapBase bapBase,
                                        RemoteTransactionSigner transactionSigner,
                                        RemoteSignType remoteSignType) {
        preTxBuild(transactionSigner, remoteSignType);
        RemoteOrdTransactionBuilder transactionBuilder = new RemoteOrdTransactionBuilder(bapBase, transactionSigner);
        transactionBuilder
                .addInputs(utxoProvider.listUxtos(payAddressEnhanceList));
        //should first output
        transactionBuilder.addOrdOutput(destinationAddress, bapBase.getIdentityKey().getBytes(StandardCharsets.UTF_8), "application/bap", null);
        BapDataLockBuilder bapDataLockBuilder = new BapDataLockBuilder(bapBase, true, remoteSignType);
        transactionBuilder.addDataOutput(bapDataLockBuilder.buildRoot());
        return postTxBuild(changeAddress, feePerKb, bapBase, remoteSignType,
                this.doBsmSign, this.doInputSign, transactionBuilder).build();
    }

    public Transaction createPostOrdinal(List<AddressEnhance> payAddressEnhanceList,
                                         UTXOProvider<AddressEnhance, BsvUtxo> utxoProvider,
                                         AddressEnhance destinationAddress,
                                         AddressEnhance changeAddress,
                                         long feePerKb,
                                         OrdScriptBuilder.Inscription inscription,
                                         RemoteBapBase remoteBapBase,
                                         RemoteSignType remoteSignType) {


        return createPostOrdinal(payAddressEnhanceList, utxoProvider, destinationAddress, changeAddress, feePerKb, inscription, remoteBapBase, new RemoteTransactionSigner(), remoteSignType);

    }


    public Transaction createPostOrdinal(List<AddressEnhance> payAddressEnhanceList,
                                         UTXOProvider<AddressEnhance, BsvUtxo> utxoProvider,
                                         AddressEnhance destinationAddress,
                                         AddressEnhance changeAddress,
                                         long feePerKb,
                                         OrdScriptBuilder.Inscription inscription,
                                         RemoteBapBase bapBase,
                                         RemoteTransactionSigner transactionSigner,
                                         RemoteSignType remoteSignType) {

        OrdScriptBuilder.ORDMap ordMap = new OrdScriptBuilder.ORDMap();
        ordMap.put("app", bapBase.getAppName());
        ordMap.put("type", "post");
        return createOrdinal(payAddressEnhanceList, utxoProvider, destinationAddress, changeAddress, feePerKb, inscription, ordMap, bapBase, transactionSigner, remoteSignType).build();

    }

    private void preTxBuild(RemoteTransactionSigner transactionSigner, RemoteSignType remoteSignType) {
        if (remoteSignType != null) {
            Assert.isInstanceOf(RemoteTransactionSigner.class, transactionSigner);
        }
    }

    private RemoteOrdTransactionBuilder postTxBuild(AddressEnhance changeAddress, long feePerKb, BapBaseCore bapBase, RemoteSignType remoteSignType, boolean doBsmSign, boolean doInputSign, RemoteOrdTransactionBuilder transactionBuilder) {
        if (doBsmSign) {
            transactionBuilder.addSigmaSign(bapBase, remoteSignType);
        }
        transactionBuilder.withFeeKb(BsvAmount.valueOf(feePerKb))
                .changeAddress(changeAddress);

        if (doBsmSign) {
            boolean verify = transactionBuilder.getSigma().verify();
            log.info("verify result:" + verify);
        }
        if (doInputSign) {
            return transactionBuilder.signTx(null, true);
        } else {
            return transactionBuilder;
        }
    }

    public boolean isDoBsmSign() {
        return doBsmSign;
    }

    public void setDoBsmSign(boolean doBsmSign) {
        this.doBsmSign = doBsmSign;
    }

    public boolean isDoInputSign() {
        return doInputSign;
    }

    public void setDoInputSign(boolean doInputSign) {
        this.doInputSign = doInputSign;
    }
}
