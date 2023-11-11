package com.metanet4j.sdk.transcation;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.lang.Assert;
import com.google.common.collect.Lists;
import com.metanet4j.base.util.JacksonUtil;
import com.metanet4j.sdk.EcKeyLiteExtend;
import com.metanet4j.sdk.PublicKey;
import com.metanet4j.sdk.context.PreSignHashContext;
import com.metanet4j.sdk.exception.SignErrorException;
import com.metanet4j.sdk.ordinals.OrdScriptBuilder;
import com.metanet4j.sdk.remote.*;
import com.metanet4j.sdk.utxo.impl.bitails.BitailsUtxoProvider;
import io.bitcoinsv.bitcoinjsv.core.Coin;
import io.bitcoinsv.bitcoinjsv.ecc.ECDSASignature;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.script.SigHash;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class RemoteSignBitcoinschemaTransactionTest extends TransactionContextTest {


    @Test
    public void testBapRootTransactionAndGetAllBsmPreSignHash() {

        try {
            BapDataLockBuilder bapDataLockBuilder = new BapDataLockBuilder(remoteBapBase, true, RemoteSignType.ROOT);
            buildBschemaTxAndGetAllBsmPreSignHash(bapDataLockBuilder.buildId());
        } catch (SignErrorException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testBapRootTransactionAndRemoteSign() {
        BapDataLockBuilder bapDataLockBuilder = new BapDataLockBuilder(remoteBapBase, true, RemoteSignType.ROOT);
        bapDataLockBuilder.buildId();
        //对bsm hash 远程签名
        signRemoteAllPreSignHash(Lists.newArrayList(bapDataLockBuilder.getPreSignHashContext()));

        try {
            BapDataLockBuilder bsmSignedBapDataLockBuilder = new BapDataLockBuilder(remoteBapBase, true, RemoteSignType.ROOT);
            bsmSignedBapDataLockBuilder.buildId();
            bsmSignedBapDataLockBuilder.sign();

            //对 input hash 远程签名
            signRemoteAllPreSignHash(buildBschemaTxAndGetAllInputPreSignHash(bsmSignedBapDataLockBuilder));

            buildRemoteSignTxThenBroadcast(bsmSignedBapDataLockBuilder);
        } catch (SignErrorException e) {
            e.printStackTrace();
        }
    }


    @Test
    public void testCreateOrdinalAndRemoteSign() {
        OrdScriptBuilder.ORDMap ordMap = new OrdScriptBuilder.ORDMap();
        ordMap.put("app", "metanet-sdk");
        ordMap.put("type", "ord");

        OrdScriptBuilder.Inscription inscription = OrdScriptBuilder.Inscription.builder().contentType("text/markdown")
                .dataB64(Base64.encode("test send oridinals 1111-3 from metanet-sdk".getBytes())).build();

        RemoteOrdTransactionTemplate ordTransactionTemplate = new RemoteOrdTransactionTemplate(false, false);

        RemoteOrdTransactionBuilder ordTransactionBuilder = createOrdinalForRemoteSign(ordMap, inscription, ordTransactionTemplate);

        signRemoteAllPreSignHash(ordTransactionBuilder.getAllBsmSignHash(true, false, bapBase));


        ordTransactionTemplate.setDoBsmSign(true);
        RemoteOrdTransactionBuilder ordTransactionBuilder2 = createOrdinalForRemoteSign(ordMap, inscription, ordTransactionTemplate);


        signRemoteAllPreSignHash(ordTransactionBuilder2.getTxInputSignHash(true, bapBase));

        ordTransactionTemplate.setDoInputSign(true);
        ordTransactionBuilder2.signTx(null, SigHash.Flags.ALL, true, true);

        Transaction transaction = ordTransactionBuilder2.build();
        correctlySpends(transaction);
        broadcast(transaction);
        parseTx(transaction);

    }

    private RemoteOrdTransactionBuilder createOrdinalForRemoteSign(OrdScriptBuilder.ORDMap ordMap, OrdScriptBuilder.Inscription inscription, RemoteOrdTransactionTemplate ordTransactionTemplate) {
        RemoteOrdTransactionBuilder ordTransactionBuilder = ordTransactionTemplate.createOrdinal(
                Lists.newArrayList(paymentAddress),
                new BitailsUtxoProvider(),
                ordAddress,
                changeAddress,
                feePerKb,
                inscription,
                ordMap,
                remoteBapBase,
                new RemoteTransactionSigner(),
                RemoteSignType.CURRENT

        );
        return ordTransactionBuilder;
    }


    private void signRemoteAllPreSignHash(List<PreSignHashContext> allPreSignHash) throws SignErrorException {
        for (PreSignHashContext preSignHashContext : allPreSignHash) {
            RemoteSignType remoteSignType = preSignHashContext.getRemoteSignType();
            switch (remoteSignType) {
                case ROOT:
                    String s = EcKeyLiteExtend.fromPrivate(bapBase.getRootPrivateKey().getPrivKey()).signHash(preSignHashContext.getPreSignHash());
                    preSignHashContext.setPublicKey(PublicKey.fromHex(bapBase.getRootPrivateKey().getPublicKeyAsHex()));
                    preSignHashContext.setSigB64(s);
                    break;
                case PREVIOUS:
                    String s1 = EcKeyLiteExtend.fromPrivate(bapBase.getPreviousPrivateKey().getPrivKey()).signHash(preSignHashContext.getPreSignHash());
                    preSignHashContext.setSigB64(s1);
                    preSignHashContext.setPublicKey(PublicKey.fromHex(bapBase.getPreviousPrivateKey().getPublicKeyAsHex()));
                    break;
                case CURRENT:
                    String s2 = EcKeyLiteExtend.fromPrivate(bapBase.getCurrentPrivateKey().getPrivKey()).signHash(preSignHashContext.getPreSignHash());
                    preSignHashContext.setSigB64(s2);
                    preSignHashContext.setPublicKey(PublicKey.fromHex(bapBase.getCurrentPrivateKey().getPublicKeyAsHex()));
                    break;
                case ORD:
                    ECDSASignature signature = bapBase.getOrdPrivateKey().sign(preSignHashContext.getPreSignHash());
                    preSignHashContext.setSigB64(Base64.encode(signature.encodeToDER()));
                    preSignHashContext.setPublicKey(PublicKey.fromHex(bapBase.getOrdPrivateKey().getPublicKeyAsHex()));
                    break;
                case PAYMENT:
                    ECDSASignature signature1 = bapBase.getPayAccountKey().sign(preSignHashContext.getPreSignHash());
                    preSignHashContext.setSigB64(Base64.encode(signature1.encodeToDER()));
                    preSignHashContext.setPublicKey(PublicKey.fromHex(bapBase.getPayAccountKey().getPublicKeyAsHex()));
                    break;

            }

            PreSignHashUtils.setPreSignHashContext(preSignHashContext);
        }
    }




    private void buildRemoteSignTxThenBroadcast(UnSpendableDataLockBuilder dataLockBuilder) throws SignErrorException {
        RemoteTransactionBuilder transactionBuilder = buildDataTx(dataLockBuilder);
        Transaction transaction = transactionBuilder.completeAndSignTx(transactionBuilder.getKeyBag(), true);
        correctlySpends(transaction);
        broadcast(transaction);
        parseTx(transaction);

    }


    private List<PreSignHashContext> buildBschemaTxAndGetAllBsmPreSignHash(UnSpendableDataLockBuilder dataLockBuilder) throws SignErrorException {
        RemoteTransactionBuilder transactionBuilder = buildDataTx(dataLockBuilder);
        List<PreSignHashContext> AllBsmSignHash = transactionBuilder.getAllBsmSignHash(true, false, remoteBapBase);
        System.out.println(JacksonUtil.obj2String(AllBsmSignHash.stream().map(o -> o.toMap()).collect(Collectors.toList())));
        return AllBsmSignHash;

    }

    private List<PreSignHashContext> buildBschemaTxAndGetAllInputPreSignHash(UnSpendableDataLockBuilder dataLockBuilder) throws SignErrorException {
        Assert.isTrue(dataLockBuilder.isRemoteSign() && dataLockBuilder.isHaveSign(), "dataLockBuilder should be remote sign and have sign");
        RemoteTransactionBuilder transactionBuilder = buildDataTx(dataLockBuilder);
        List<PreSignHashContext> inputSignHash = transactionBuilder.getTxInputSignHash(false, remoteBapBase);
        System.out.println(JacksonUtil.obj2String(inputSignHash.stream().map(o -> o.toMap()).collect(Collectors.toList())));
        return inputSignHash;

    }

    private RemoteTransactionBuilder buildDataTx(UnSpendableDataLockBuilder dataLockBuilder) {
        RemoteTransactionBuilder transactionBuilder = new RemoteTransactionBuilder(this.remoteBapBase, new RemoteTransactionSigner());
        transactionBuilder
                .addInputs(Lists.newArrayList(paymentAddress),
                        new BitailsUtxoProvider())
                .addDataOutput(dataLockBuilder)
                .withFeeKb(Coin.valueOf(feePerKb))
                .changeAddress(changeAddress);
        return transactionBuilder;
    }
}
