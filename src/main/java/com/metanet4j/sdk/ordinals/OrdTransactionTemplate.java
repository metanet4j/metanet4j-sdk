package com.metanet4j.sdk.ordinals;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.StrUtil;
import com.metanet4j.sdk.bap.BapBase;
import com.metanet4j.sdk.exception.SignErrorException;
import com.metanet4j.sdk.signers.OrdinalsTransactionSigner;
import com.metanet4j.sdk.transcation.BapDataLockBuilder;
import com.metanet4j.sdk.utxo.UTXOProvider;
import io.bitcoinsv.bitcoinjsv.core.Address;
import io.bitcoinsv.bitcoinjsv.core.AddressLite;
import io.bitcoinsv.bitcoinjsv.core.Coin;
import io.bitcoinsv.bitcoinjsv.core.UTXO;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.script.SigHash;
import io.bitcoinsv.bitcoinjsv.temp.KeyBag;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
public class OrdTransactionTemplate {


    public static Transaction createOrdinal(
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
        OrdTransactionBuilder transactionBuilder = new OrdTransactionBuilder(keyBag, new OrdinalsTransactionSigner());
        transactionBuilder
                .addInputs(utxoProvider.listUxtos(payAddressLiteList));
        transactionBuilder.addOrdOutput(destinationAddress, Base64.decode(inscription.getDataB64()), inscription.getContentType(), metaData);
        transactionBuilder.addSigmaSign(bapBase);
        transactionBuilder.withFeeKb(Coin.valueOf(feePerKb))
                .changeAddress(changeAddress);

        boolean verify = transactionBuilder.getSigma().verify();
        log.info("verify result:" + verify);
        return transactionBuilder.signTx(keyBag, true).build();

    }

    public static Transaction sendOrdinal(
            UTXO ordinalUtxo,
            UTXO paymentUtxo,
            Address destinationAddress,
            Address changeAddress,
            long feePerKb,
            OrdScriptBuilder.Inscription reInscription,
            OrdScriptBuilder.ORDMap metaData,
            KeyBag keyBag) {

        OrdTransactionBuilder transactionBuilder = new OrdTransactionBuilder(keyBag, new OrdinalsTransactionSigner());
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

    public static Transaction sendUtxos(
            List<UTXO> utxos,
            Address destinationAddress,
            long feePerByte,
            KeyBag keyBag) throws Exception {
        OrdTransactionBuilder transactionBuilder = new OrdTransactionBuilder(keyBag, new OrdinalsTransactionSigner());
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
    public static Transaction createBapOrdinal(List<AddressLite> payAddressLiteList,
                                               UTXOProvider utxoProvider,
                                               Address destinationAddress,
                                               Address changeAddress,
                                               long feePerKb,
                                               BapBase bapBase,
                                               KeyBag keyBag) {

        OrdTransactionBuilder transactionBuilder = new OrdTransactionBuilder(keyBag, new OrdinalsTransactionSigner());
        transactionBuilder
                .addInputs(utxoProvider.listUxtos(payAddressLiteList));
        //should first output
        transactionBuilder.addOrdOutput(destinationAddress, bapBase.getIdentityKey().getBytes(StandardCharsets.UTF_8), "application/bap", null);
        BapDataLockBuilder bapDataLockBuilder = new BapDataLockBuilder(bapBase);
        transactionBuilder.addDataOutput(bapDataLockBuilder.buildRoot());
        transactionBuilder.addSigmaSign(bapBase);
        transactionBuilder.withFeeKb(Coin.valueOf(feePerKb))
                .changeAddress(changeAddress);

        boolean verify = transactionBuilder.getSigma().verify();
        log.info("verify result:" + verify);
        return transactionBuilder.signTx(keyBag, true).build();
    }

    public static Transaction createPostOrdinal(List<AddressLite> payAddressLiteList,
                                                UTXOProvider utxoProvider,
                                                Address destinationAddress,
                                                Address changeAddress,
                                                long feePerKb,
                                                OrdScriptBuilder.Inscription inscription,

                                                BapBase bapBase,
                                                KeyBag keyBag) {

        OrdScriptBuilder.ORDMap ordMap = new OrdScriptBuilder.ORDMap();
        ordMap.put("app", bapBase.getAppName());
        ordMap.put("type", "post");
        return createOrdinal(payAddressLiteList, utxoProvider, destinationAddress, changeAddress, feePerKb, inscription, null, bapBase, keyBag);

    }

}
