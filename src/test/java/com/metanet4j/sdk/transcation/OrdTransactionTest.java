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
import io.bitcoinsv.bitcoinjsv.core.Address;
import io.bitcoinsv.bitcoinjsv.core.UTXO;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.params.MainNetParams;
import io.bitcoinsv.bitcoinjsv.params.Net;
import io.bitcoinsv.bitcoinjsv.script.SigHash;
import org.junit.Test;

import java.util.List;

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
                keyBag
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
                keyBag
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

        List<UTXO> originUtxoList = new OrdinalsOriginGorillaUtxoProvider().listUxtos(Lists.newArrayList(origin));
        List<UTXO> paymentUtxoList = new BitailsUtxoProvider().listUxtos(Lists.newArrayList(paymentAddress));
        Transaction transaction = new OrdTransactionTemplate().sendOrdinal(this.bapBase, originUtxoList.get(0), paymentUtxoList.get(0)
                , ordAddress, changeAddress, 50L, null, null, keyBag);
        parseTx(transaction);
        correctlySpends(transaction);
        String txId = broadcast(transaction);
        System.out.println("you can view your ordinal  tx on https://v3.ordinals.gorillapool.io/api/txos/" + txId + "_0?script=true");
    }

    @Test
    public void testSendOrdinalBySendUtxo() {
        String origin = "c51f95b21c4b0f6373f8ada9db85da469a86d5c564cc1c64183d964b78cdeadb_0";
        Address pandaAddress = Address.fromBase58(MainNetParams.get(), "1PHhmDqXuwrX2kpA8DApVaMC98LrgNo9uF");
        List<UTXO> originUtxoList = new OrdinalsOriginGorillaUtxoProvider().listUxtos(Lists.newArrayList(origin));
        List<UTXO> paymentUtxoList = new BitailsUtxoProvider().listUxtos(Lists.newArrayList(paymentAddress));
        Transaction transaction = new OrdTransactionTemplate().sendOrdinal(this.bapBase, originUtxoList.get(0), paymentUtxoList.get(0)
                , pandaAddress, changeAddress, 50L, null, null, keyBag);
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
        Transaction transaction = new Transaction(Net.MAINNET, HexUtil.decodeHex(ordTx));
        Sigma sigma = new Sigma(bapBase, transaction);
        boolean b = sigma.verify();
        System.out.println(b);
        System.out.println(transaction.getHashAsString());

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
        int sighashType = SigHash.Flags.SINGLE.value | SigHash.Flags.FORKID.value | SigHash.Flags.ANYONECANPAY.value;
        System.out.println(sighashType);
    }


}
