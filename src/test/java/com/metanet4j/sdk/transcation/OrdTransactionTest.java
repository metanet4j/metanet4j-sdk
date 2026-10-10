package com.metanet4j.sdk.transcation;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.HexUtil;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.metanet4j.sdk.ordinals.OrdScriptBuilder;
import com.metanet4j.sdk.ordinals.OrdTransactionTemplate;
import com.metanet4j.sdk.sigma.Sigma;
import com.metanet4j.sdk.utxo.impl.bitails.BitailsUtxoProvider;
import com.metanet4j.sdk.utxo.impl.gorillapool.OrdinalsOriginGorillaUtxoProvider;
import com.metanet4j.bsv.transaction.Transaction;
import com.metanet4j.base.bsv.BsvUtxo;
import com.metanet4j.sdk.address.AddressEnhance;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 外部服务/链上数据依赖：本类通过公网 API（Bitails / GorillaPool）拉取实时 UTXO 并广播交易，
 * 且 fixture 引用 2023 年的主网 outpoint，链上状态早已变化 → 不适合放进常规门禁。
 * 执行方式：mvn clean test -DexcludedGroups=external（默认排除）；需人工验证时去掉该参数。
 */
@Tag("external")
public class OrdTransactionTest extends TransactionContextTest {

    /**
     * should wait 7 confirmations
     */
    @Test
    public void testCreateOrdinal() {
        OrdScriptBuilder.ORDMap ordMap = new OrdScriptBuilder.ORDMap();
        ordMap.put("app", "metanet-sdk");
        ordMap.put("type", "ord");
        Transaction transaction = new OrdTransactionTemplate().createOrdinal(Lists.newArrayList(paymentAddress),
                new BitailsUtxoProvider(),
                ordAddress,
                changeAddress,
                feePerKb,
                OrdScriptBuilder.Inscription.builder().contentType("text/markdown")
                        .dataB64(Base64.encode("test send oridinals 1110-1 from metanet-sdk".getBytes())).build(),
                ordMap,
                bapBase,
                keyProvider
        ).build();

        parseTx(transaction);
        correctlySpends(transaction);
        String txId = broadcast(transaction);
        System.out.println("you can view your ordinal  tx on https://v3.ordinals.gorillapool.io/api/txos/" + txId + "_0?script=true");

    }


    @Test
    public void testCreateBapOrdinal() {
        OrdScriptBuilder.ORDMap ordMap = new OrdScriptBuilder.ORDMap();
        Transaction transaction = new OrdTransactionTemplate().createBapOrdinal(Lists.newArrayList(paymentAddress),
                new BitailsUtxoProvider(),
                ordAddress,
                changeAddress,
                feePerKb,
                bapBase,
                keyProvider
        );

        parseTx(transaction);
        correctlySpends(transaction);
        String txId = broadcast(transaction);
        System.out.println("you can view your ordinal  tx on https://v3.ordinals.gorillapool.io/api/txos/" + txId + "_0?script=true");
    }


    /**
     * should wait 1 confirmations
     */
    @Test
    public void testSendOrdinalByCreateUtxo() {
        String origin = "58a97bf6af7c1fa3325af5ccc717309d25df5bb8f3c611b145881e977a1eb8d4_0";

        List<BsvUtxo> originUtxoList = new OrdinalsOriginGorillaUtxoProvider().listUxtos(Lists.newArrayList(origin));
        List<BsvUtxo> paymentUtxoList = new BitailsUtxoProvider().listUxtos(Lists.newArrayList(paymentAddress));
        Transaction transaction = new OrdTransactionTemplate().sendOrdinal(this.bapBase, originUtxoList.get(0), paymentUtxoList.get(0)
                , ordAddress, changeAddress, 50L, null, null, keyProvider);
        parseTx(transaction);
        correctlySpends(transaction);
        String txId = broadcast(transaction);
        System.out.println("you can view your ordinal  tx on https://v3.ordinals.gorillapool.io/api/txos/" + txId + "_0?script=true");
    }

    @Test
    public void testSendOrdinalBySendUtxo() {
        String origin = "c51f95b21c4b0f6373f8ada9db85da469a86d5c564cc1c64183d964b78cdeadb_0";
        AddressEnhance pandaAddress = AddressEnhance.fromBase58("1PHhmDqXuwrX2kpA8DApVaMC98LrgNo9uF");
        List<BsvUtxo> originUtxoList = new OrdinalsOriginGorillaUtxoProvider().listUxtos(Lists.newArrayList(origin));
        List<BsvUtxo> paymentUtxoList = new BitailsUtxoProvider().listUxtos(Lists.newArrayList(paymentAddress));
        Transaction transaction = new OrdTransactionTemplate().sendOrdinal(this.bapBase, originUtxoList.get(0), paymentUtxoList.get(0)
                , pandaAddress, changeAddress, 50L, null, null, keyProvider);
        parseTx(transaction);
        correctlySpends(transaction);
        String txId = broadcast(transaction);
        System.out.println("you can view your ordinal  tx on https://v3.ordinals.gorillapool.io/api/txos/" + txId + "_0?script=true");
    }

    /**
     * see https://docs.1satordinals.com/readme/test-vectors
     */
    @Test
    public void testTransactionOrdReInscriptionby2ndkeyDeserialize() {
        String ordTx = FileUtil.readString("ord/8e8378.hex", Charsets.UTF_8);
        parseTx(ordTx);

    }

    /**
     * see https://docs.1satordinals.com/readme/test-vectors
     */
    @Test
    public void testOpns() {
        String ordTx = FileUtil.readString("ord/opns_0b8704.hex", Charsets.UTF_8);
        parseTx(ordTx);

    }

    /**
     * see https://docs.1satordinals.com/readme/test-vectors
     */
    @Test
    public void testTransactionOrdSigmaDeserialize() {
        String ordTx = FileUtil.readString("ord/040c6d48c.hex", Charsets.UTF_8);
        Transaction transaction = Transaction.fromBinary(HexUtil.decodeHex(ordTx));
        Sigma sigma = new Sigma(bapBase, transaction);
        boolean b = sigma.verify();
        System.out.println(b);
        System.out.println(transaction.id("hex"));

    }


    /**
     * see https://docs.1satordinals.com/readme/test-vectors
     */
    @Test
    public void testTransactionOrdPngDeserialize() {
        String ordTx = FileUtil.readString("ord/26a81e.hex", Charsets.UTF_8);
        parseTx(ordTx);

    }

    /**
     * see https://docs.1satordinals.com/readme/test-vectors
     */
    @Test
    public void testTransactionOrdMetadataDeserialize() {
        String ordTx = FileUtil.readString("ord/2809d6.hex", Charsets.UTF_8);
        parseTx(ordTx);

    }

    /**
     * see https://docs.1satordinals.com/readme/test-vectors
     */
    @Test
    public void testTransactionOrdDeserialize() {
        String ordTx = FileUtil.readString("ord/ord1.hex", Charsets.UTF_8);
        parseTx(ordTx);

    }

    /**
     * see https://docs.1satordinals.com/readme/test-vectors
     */
    @Test
    public void testTransactionOrdTransferDeserialize() {
        String ordTx = FileUtil.readString("ord/ord-transfer.hex", Charsets.UTF_8);
        parseTx(ordTx);
    }

    /**
     * see https://docs.1satordinals.com/readme/test-vectors
     */
    @Test
    public void testTransactionOrdMarketDeserialize() {
        String ordTx = FileUtil.readString("ord/offchain.hex", Charsets.UTF_8);
        parseTx(ordTx);
    }

    @Test
    public void testSighashType() {
        int sighashType = com.metanet4j.bsv.primitives.TransactionSignature.SIGHASH_SINGLE
                | com.metanet4j.bsv.primitives.TransactionSignature.SIGHASH_FORKID
                | com.metanet4j.bsv.primitives.TransactionSignature.SIGHASH_ANYONECANPAY;
        System.out.println(sighashType);
    }


}
