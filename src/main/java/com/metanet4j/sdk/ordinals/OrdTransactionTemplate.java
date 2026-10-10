package com.metanet4j.sdk.ordinals;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.StrUtil;
import com.metanet4j.sdk.bap.BapBase;
import com.metanet4j.sdk.bap.BapBaseCore;
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
public class OrdTransactionTemplate {


    public OrdTransactionTemplate() {

    }



    public OrdTransactionBuilder createOrdinal(
            List<AddressEnhance> payAddressEnhanceList,
            UTXOProvider<AddressEnhance, BsvUtxo> utxoProvider,
            AddressEnhance destinationAddress,
            AddressEnhance changeAddress,
            long feePerKb,
            OrdScriptBuilder.Inscription inscription,
            OrdScriptBuilder.ORDMap metaData,
            BapBaseCore bapBase,
            SigningKeyProvider keyProvider

    ) {


        OrdTransactionBuilder transactionBuilder = new OrdTransactionBuilder(bapBase, keyProvider);
        transactionBuilder
                .addInputs(utxoProvider.listUxtos(payAddressEnhanceList));
        transactionBuilder.addOrdOutput(destinationAddress, Base64.decode(inscription.getDataB64()), inscription.getContentType(), metaData);

        transactionBuilder.addSigmaSign(bapBase);
        transactionBuilder.withFeeKb(BsvAmount.valueOf(feePerKb))
                .changeAddress(changeAddress);
        boolean verify = transactionBuilder.getSigma().verify();
        log.info("verify result:" + verify);
        return transactionBuilder.signTx(keyProvider, true);


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
            SigningKeyProvider keyProvider
            ) {

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
     * @param keyBag
     * @return
     */
    public Transaction createBapOrdinal(List<AddressEnhance> payAddressEnhanceList,
                                        UTXOProvider<AddressEnhance, BsvUtxo> utxoProvider,
                                        AddressEnhance destinationAddress,
                                        AddressEnhance changeAddress,
                                        long feePerKb,
                                        BapBase bapBase,
                                        SigningKeyProvider keyProvider) {

        return createBapOrdinal(payAddressEnhanceList, utxoProvider, destinationAddress, changeAddress, feePerKb, bapBase, keyProvider);

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
     * @param keyBag
     * @return
     */
    public Transaction createBapOrdinal(List<AddressEnhance> payAddressEnhanceList,
                                        UTXOProvider<AddressEnhance, BsvUtxo> utxoProvider,
                                        AddressEnhance destinationAddress,
                                        AddressEnhance changeAddress,
                                        long feePerKb,
                                        BapBaseCore bapBase,
                                        SigningKeyProvider keyProvider
                                        ) {

        OrdTransactionBuilder transactionBuilder = new OrdTransactionBuilder(bapBase, keyProvider);
        transactionBuilder
                .addInputs(utxoProvider.listUxtos(payAddressEnhanceList));
        //should first output
        transactionBuilder.addOrdOutput(destinationAddress, bapBase.getIdentityKey().getBytes(StandardCharsets.UTF_8), "application/bap", null);
        BapDataLockBuilder bapDataLockBuilder = new BapDataLockBuilder(bapBase);
        transactionBuilder.addDataOutput(bapDataLockBuilder.buildRoot().sign());
        transactionBuilder.addSigmaSign(bapBase);
        transactionBuilder.withFeeKb(BsvAmount.valueOf(feePerKb))
                .changeAddress(changeAddress);

        boolean verify = transactionBuilder.getSigma().verify();
        log.info("verify result:" + verify);
        return transactionBuilder.signTx(keyProvider, true).build();
    }


    public Transaction createPostOrdinal(List<AddressEnhance> payAddressEnhanceList,
                                         UTXOProvider<AddressEnhance, BsvUtxo> utxoProvider,
                                         AddressEnhance destinationAddress,
                                         AddressEnhance changeAddress,
                                         long feePerKb,
                                         OrdScriptBuilder.Inscription inscription,
                                         BapBaseCore bapBase,
                                         SigningKeyProvider keyProvider
                                         ) {

        OrdScriptBuilder.ORDMap ordMap = new OrdScriptBuilder.ORDMap();
        ordMap.put("app", bapBase.getAppName());
        ordMap.put("type", "post");
        return createOrdinal(payAddressEnhanceList, utxoProvider, destinationAddress, changeAddress, feePerKb, inscription, ordMap, bapBase, keyProvider).build();

    }


}
