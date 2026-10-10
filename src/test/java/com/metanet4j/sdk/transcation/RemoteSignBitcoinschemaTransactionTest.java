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
import com.metanet4j.base.bsv.BsvAmount;
import com.metanet4j.bsv.transaction.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
/**
 * 外部服务/链上数据依赖：本类通过公网 API（Bitails / GorillaPool）拉取实时 UTXO 并广播交易，
 * 且 fixture 引用 2023 年的主网 outpoint，链上状态早已变化 → 不适合放进常规门禁。
 * 执行方式：mvn clean test -DexcludedGroups=external（默认排除）；需人工验证时去掉该参数。
 */
@Tag("external")
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
        ordTransactionBuilder2.signTx(keyProvider, com.metanet4j.bsv.primitives.TransactionSignature.SIGHASH_ALL, true, true);

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
                    preSignHashContext.setPublicKey(bapBase.getRootPrivateKey().getPublicKey());
                    preSignHashContext.setSigB64(s);
                    break;
                case PREVIOUS:
                    String s1 = EcKeyLiteExtend.fromPrivate(bapBase.getPreviousPrivateKey().getPrivKey()).signHash(preSignHashContext.getPreSignHash());
                    preSignHashContext.setSigB64(s1);
                    preSignHashContext.setPublicKey(bapBase.getPreviousPrivateKey().getPublicKey());
                    break;
                case CURRENT:
                    String s2 = EcKeyLiteExtend.fromPrivate(bapBase.getCurrentPrivateKey().getPrivKey()).signHash(preSignHashContext.getPreSignHash());
                    preSignHashContext.setSigB64(s2);
                    preSignHashContext.setPublicKey(bapBase.getCurrentPrivateKey().getPublicKey());
                    break;
                case ORD:
                    com.metanet4j.bsv.primitives.Signature signature =
                            bapBase.getOrdPrivateKey().sign(preSignHashContext.getPreSignHash());
                    preSignHashContext.setSigB64(Base64.encode(toBytes(signature.toDER())));
                    preSignHashContext.setPublicKey(bapBase.getOrdPrivateKey().getPublicKey());
                    break;
                case PAYMENT:
                    com.metanet4j.bsv.primitives.Signature signature1 =
                            bapBase.getPayAccountKey().sign(preSignHashContext.getPreSignHash());
                    preSignHashContext.setSigB64(Base64.encode(toBytes(signature1.toDER())));
                    preSignHashContext.setPublicKey(bapBase.getPayAccountKey().getPublicKey());
                    break;

            }

            PreSignHashUtils.setPreSignHashContext(preSignHashContext);
        }
    }




    private void buildRemoteSignTxThenBroadcast(UnSpendableDataLockBuilder dataLockBuilder) throws SignErrorException {
        RemoteTransactionBuilder transactionBuilder = buildDataTx(dataLockBuilder);
        Transaction transaction = transactionBuilder.completeAndSignTx(transactionBuilder.getKeyProvider(), true);
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
        RemoteTransactionBuilder transactionBuilder = new RemoteTransactionBuilder(this.remoteBapBase, keyProvider);
        transactionBuilder
                .addInputs(java.util.List.<com.metanet4j.sdk.address.AddressEnhance>of(paymentAddress),
                        new BitailsUtxoProvider())
                .addDataOutput(dataLockBuilder)
                .withFeeKb(BsvAmount.valueOf(feePerKb))
                .changeAddress(changeAddress);
        return transactionBuilder;
    }

    private static byte[] toBytes(java.util.List<?> values) {
        byte[] bytes = new byte[values.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ((Number) values.get(i)).intValue();
        }
        return bytes;
    }
}