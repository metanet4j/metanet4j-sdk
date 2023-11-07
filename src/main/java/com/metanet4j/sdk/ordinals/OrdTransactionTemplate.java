package com.metanet4j.sdk.ordinals;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.metanet4j.sdk.RemoteSignType;
import com.metanet4j.sdk.bap.BapBase;
import com.metanet4j.sdk.bap.BapBaseCore;
import com.metanet4j.sdk.bap.RemoteBapBase;
import com.metanet4j.sdk.exception.SignErrorException;
import com.metanet4j.sdk.signers.OrdinalsTransactionSigner;
import com.metanet4j.sdk.signers.RemoteTransactionSigner;
import com.metanet4j.sdk.transcation.BapDataLockBuilder;
import com.metanet4j.sdk.utxo.UTXOProvider;
import io.bitcoinsv.bitcoinjsv.core.Address;
import io.bitcoinsv.bitcoinjsv.core.AddressLite;
import io.bitcoinsv.bitcoinjsv.core.Coin;
import io.bitcoinsv.bitcoinjsv.core.UTXO;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.script.SigHash;
import io.bitcoinsv.bitcoinjsv.signers.TransactionSigner;
import io.bitcoinsv.bitcoinjsv.temp.KeyBag;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
public class OrdTransactionTemplate {
    private boolean doBsmSign;
    private boolean doInputSign;

    public OrdTransactionTemplate() {
        this.doBsmSign = true;
        this.doInputSign = true;
    }

    public OrdTransactionTemplate(boolean doBsmSign, boolean doInputSign) {
        this.doBsmSign = doBsmSign;
        this.doInputSign = doInputSign;
    }

    public OrdTransactionBuilder createOrdinal(
            List<AddressLite> payAddressLiteList,
            UTXOProvider utxoProvider,
            Address destinationAddress,
            Address changeAddress,
            long feePerKb,
            OrdScriptBuilder.Inscription inscription,
            OrdScriptBuilder.ORDMap metaData,
            BapBase bapBase,
            KeyBag keyBag
    ) {
        return createOrdinal(payAddressLiteList, utxoProvider, destinationAddress, changeAddress, feePerKb, inscription, metaData, bapBase, keyBag, new OrdinalsTransactionSigner(), null);
    }

    public OrdTransactionBuilder createOrdinal(
            List<AddressLite> payAddressLiteList,
            UTXOProvider utxoProvider,
            Address destinationAddress,
            Address changeAddress,
            long feePerKb,
            OrdScriptBuilder.Inscription inscription,
            OrdScriptBuilder.ORDMap metaData,
            RemoteBapBase remoteBapBase,
            RemoteSignType remoteSignType

    ) {

        return createOrdinal(payAddressLiteList, utxoProvider, destinationAddress, changeAddress, feePerKb, inscription, metaData, remoteBapBase, null, new OrdinalsTransactionSigner(), remoteSignType);
    }


    public OrdTransactionBuilder createOrdinal(
            List<AddressLite> payAddressLiteList,
            UTXOProvider utxoProvider,
            Address destinationAddress,
            Address changeAddress,
            long feePerKb,
            OrdScriptBuilder.Inscription inscription,
            OrdScriptBuilder.ORDMap metaData,
            BapBaseCore bapBase,
            KeyBag keyBag,
            TransactionSigner transactionSigner,
            RemoteSignType remoteSignType

    ) {
        preTxBuild(transactionSigner, remoteSignType);

        OrdTransactionBuilder transactionBuilder = new OrdTransactionBuilder(bapBase, keyBag, transactionSigner);
        transactionBuilder
                .addInputs(utxoProvider.listUxtos(payAddressLiteList));
        transactionBuilder.addOrdOutput(destinationAddress, Base64.decode(inscription.getDataB64()), inscription.getContentType(), metaData, RemoteSignType.CURRENT);


        return postTxBuild(changeAddress, feePerKb, bapBase, keyBag, remoteSignType, this.doBsmSign, this.doInputSign, transactionBuilder);


    }


    public Transaction sendOrdinal(
            RemoteBapBase bapBase,
            UTXO ordinalUtxo,
            UTXO paymentUtxo,
            Address destinationAddress,
            Address changeAddress,
            long feePerKb,
            OrdScriptBuilder.Inscription reInscription,
            OrdScriptBuilder.ORDMap metaData,
            RemoteSignType remoteSignType
    ) {
        return sendOrdinal(bapBase, ordinalUtxo, paymentUtxo, destinationAddress, changeAddress, feePerKb, reInscription, metaData, null, new RemoteTransactionSigner(), remoteSignType);
    }


    public Transaction sendOrdinal(
            BapBaseCore bapBase,
            UTXO ordinalUtxo,
            UTXO paymentUtxo,
            Address destinationAddress,
            Address changeAddress,
            long feePerKb,
            OrdScriptBuilder.Inscription reInscription,
            OrdScriptBuilder.ORDMap metaData,
            KeyBag keyBag) {
        return sendOrdinal(bapBase, ordinalUtxo, paymentUtxo, destinationAddress, changeAddress, feePerKb, reInscription, metaData, keyBag, new OrdinalsTransactionSigner(), null);

    }

    public Transaction sendOrdinal(
            BapBaseCore bapBase,
            UTXO ordinalUtxo,
            UTXO paymentUtxo,
            Address destinationAddress,
            Address changeAddress,
            long feePerKb,
            OrdScriptBuilder.Inscription reInscription,
            OrdScriptBuilder.ORDMap metaData,
            KeyBag keyBag,
            TransactionSigner transactionSigner,
            RemoteSignType remoteSignType) {
        preTxBuild(transactionSigner, remoteSignType);

        OrdTransactionBuilder transactionBuilder = new OrdTransactionBuilder(bapBase, keyBag, transactionSigner);
        transactionBuilder
                .addOrdInput(ordinalUtxo);
        transactionBuilder.addInput(paymentUtxo);

        if (reInscription != null && StrUtil.isNotEmpty(reInscription.getDataB64()) && StrUtil.isNotEmpty(reInscription.getContentType())) {
            transactionBuilder.addOrdOutput(destinationAddress, Base64.decode(reInscription.getDataB64()), metaData.get("type"), metaData);
        } else {
            transactionBuilder.addOutput(Coin.SATOSHI, destinationAddress);
        }
        transactionBuilder.withFeeKb(Coin.valueOf(feePerKb))
                .changeAddress(changeAddress);

        try {
            transactionBuilder.signTx(keyBag, SigHash.Flags.SINGLE, true, true);
        } catch (SignErrorException e) {

        }
        return transactionBuilder.build();
    }

    @Deprecated
    public Transaction sendUtxos(
            BapBase bapBase,
            List<UTXO> utxos,
            Address destinationAddress,
            long feePerByte,
            KeyBag keyBag) throws Exception {
        OrdTransactionBuilder transactionBuilder = new OrdTransactionBuilder(bapBase, keyBag, new OrdinalsTransactionSigner());
        transactionBuilder.addInputs(utxos);
        transactionBuilder.withFeeKb(Coin.valueOf(feePerByte));
        transactionBuilder.changeAddress(destinationAddress);

        transactionBuilder.signTx(keyBag, SigHash.Flags.ALL, true, true);
        return transactionBuilder.build();
    }

    /**
     * bap + 1sat ordinal
     * content-type:application/bap
     *
     * @param payAddressLiteList
     * @param utxoProvider
     * @param destinationAddress
     * @param changeAddress
     * @param feePerKb
     * @param bapBase
     * @param keyBag
     * @return
     */
    public Transaction createBapOrdinal(List<AddressLite> payAddressLiteList,
                                        UTXOProvider utxoProvider,
                                        Address destinationAddress,
                                        Address changeAddress,
                                        long feePerKb,
                                        BapBase bapBase,
                                        KeyBag keyBag) {

        return createBapOrdinal(payAddressLiteList, utxoProvider, destinationAddress, changeAddress, feePerKb, bapBase, keyBag, new OrdinalsTransactionSigner(), null);

    }

    public Transaction createBapOrdinal(List<AddressLite> payAddressLiteList,
                                        UTXOProvider utxoProvider,
                                        Address destinationAddress,
                                        Address changeAddress,
                                        long feePerKb,
                                        RemoteBapBase bapBase,
                                        RemoteSignType remoteSignType) {

        return createBapOrdinal(payAddressLiteList, utxoProvider, destinationAddress, changeAddress, feePerKb, bapBase, null, new RemoteTransactionSigner(), remoteSignType);

    }


    /**
     * bap + 1sat ordinal
     * content-type:application/bap
     *
     * @param payAddressLiteList
     * @param utxoProvider
     * @param destinationAddress
     * @param changeAddress
     * @param feePerKb
     * @param bapBase
     * @param keyBag
     * @return
     */
    public Transaction createBapOrdinal(List<AddressLite> payAddressLiteList,
                                        UTXOProvider utxoProvider,
                                        Address destinationAddress,
                                        Address changeAddress,
                                        long feePerKb,
                                        BapBaseCore bapBase,
                                        KeyBag keyBag,
                                        TransactionSigner transactionSigner,
                                        RemoteSignType remoteSignType) {
        preTxBuild(transactionSigner, remoteSignType);


        OrdTransactionBuilder transactionBuilder = new OrdTransactionBuilder(bapBase, keyBag, transactionSigner);
        transactionBuilder
                .addInputs(utxoProvider.listUxtos(payAddressLiteList));
        //should first output
        transactionBuilder.addOrdOutput(destinationAddress, bapBase.getIdentityKey().getBytes(StandardCharsets.UTF_8), "application/bap", null);
        BapDataLockBuilder bapDataLockBuilder = null;
        if (remoteSignType != null) {
            bapDataLockBuilder = new BapDataLockBuilder(bapBase, true, remoteSignType);
        } else {
            bapDataLockBuilder = new BapDataLockBuilder(bapBase);
        }

        transactionBuilder.addDataOutput(bapDataLockBuilder.buildRoot());
        if (remoteSignType != null) {
            transactionBuilder.addSigmaSign(bapBase, remoteSignType);
        } else {
            transactionBuilder.addSigmaSign(bapBase);
        }
        transactionBuilder.withFeeKb(Coin.valueOf(feePerKb))
                .changeAddress(changeAddress);

        boolean verify = transactionBuilder.getSigma().verify();
        log.info("verify result:" + verify);
        return transactionBuilder.signTx(keyBag, true).build();
    }

    public Transaction createPostOrdinal(List<AddressLite> payAddressLiteList,
                                         UTXOProvider utxoProvider,
                                         Address destinationAddress,
                                         Address changeAddress,
                                         long feePerKb,
                                         OrdScriptBuilder.Inscription inscription,
                                         RemoteBapBase remoteBapBase,
                                         RemoteSignType remoteSignType) {


        return createPostOrdinal(payAddressLiteList, utxoProvider, destinationAddress, changeAddress, feePerKb, inscription, remoteBapBase, null, new RemoteTransactionSigner(), remoteSignType);

    }

    public Transaction createPostOrdinal(List<AddressLite> payAddressLiteList,
                                         UTXOProvider utxoProvider,
                                         Address destinationAddress,
                                         Address changeAddress,
                                         long feePerKb,
                                         OrdScriptBuilder.Inscription inscription,
                                         BapBase bapBase,
                                         KeyBag keyBag) {


        return createPostOrdinal(payAddressLiteList, utxoProvider, destinationAddress, changeAddress, feePerKb, inscription, bapBase, keyBag, new OrdinalsTransactionSigner(), null);

    }

    public Transaction createPostOrdinal(List<AddressLite> payAddressLiteList,
                                         UTXOProvider utxoProvider,
                                         Address destinationAddress,
                                         Address changeAddress,
                                         long feePerKb,
                                         OrdScriptBuilder.Inscription inscription,
                                         BapBaseCore bapBase,
                                         KeyBag keyBag,
                                         TransactionSigner transactionSigner,
                                         RemoteSignType remoteSignType) {

        OrdScriptBuilder.ORDMap ordMap = new OrdScriptBuilder.ORDMap();
        ordMap.put("app", bapBase.getAppName());
        ordMap.put("type", "post");
        return createOrdinal(payAddressLiteList, utxoProvider, destinationAddress, changeAddress, feePerKb, inscription, null, bapBase, keyBag, transactionSigner, remoteSignType).build();

    }

    private void preTxBuild(TransactionSigner transactionSigner, RemoteSignType remoteSignType) {
        if (remoteSignType != null) {
            Assert.isInstanceOf(RemoteTransactionSigner.class, transactionSigner);
        }
        if (remoteSignType == null) {
            Assert.isInstanceOf(OrdinalsTransactionSigner.class, transactionSigner);
        }
    }

    private OrdTransactionBuilder postTxBuild(Address changeAddress, long feePerKb, BapBaseCore bapBase, KeyBag keyBag, RemoteSignType remoteSignType, boolean doBsmSign, boolean doInputSign, OrdTransactionBuilder transactionBuilder) {
        if (remoteSignType != null && doBsmSign) {
            transactionBuilder.addSigmaSign(bapBase, remoteSignType);
        } else if (remoteSignType == null && doBsmSign) {
            transactionBuilder.addSigmaSign(bapBase);
        }
        transactionBuilder.withFeeKb(Coin.valueOf(feePerKb))
                .changeAddress(changeAddress);

        if (doBsmSign) {
            boolean verify = transactionBuilder.getSigma().verify();
            log.info("verify result:" + verify);
        }
        if (doInputSign) {
            return transactionBuilder.signTx(keyBag, true);
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
