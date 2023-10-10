package com.metanet4j.sdk.transcation;

import cn.hutool.core.io.FileUtil;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.metanet4j.base.model.B;
import com.metanet4j.sdk.SignType;
import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.bap.BapBase.BapProviderKeyBag;
import com.metanet4j.sdk.exception.SignErrorException;
import com.metanet4j.sdk.utxo.impl.bitails.BitailsUtxoProvider;
import io.bitcoinsv.bitcoinjsv.core.Address;
import io.bitcoinsv.bitcoinjsv.core.Coin;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.params.MainNetParams;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;


public class BitcoinschemaTransactionTest extends TransactionContextTest {


    @Test
    public void testBapRootTransaction() {

        try {
            BapDataLockBuilder bapDataLockBuilder = new BapDataLockBuilder(bapBase);
            buildTxThenBroadcast(bapDataLockBuilder.buildId());
            System.out.println("you can view your bitcoin schema tx on https://blockpost.network/profile/" + bapBase.getIdentityKey());
        } catch (SignErrorException e) {
            e.printStackTrace();
        }
    }


    @Test
    public void testBapAliasTransaction()  {

        try {
            BapDataLockBuilder bapDataLockBuilder = new BapDataLockBuilder(bapBase);
            buildTxThenBroadcast(bapDataLockBuilder.buildAlias(FileUtil.readString("identity.json",
                    Charsets.UTF_8)).sign(SignType.CURRENT));
            System.out.println("you can view your bitcoin schema tx on https://blockpost.network/profile/" + bapBase.getIdentityKey());
        } catch (SignErrorException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testBsocialUnFollowTransaction(){
        try {
            BsocialDataLockBuilder bsocialDataLockBuilder = new BsocialDataLockBuilder(bapBase);
            String identityKey = "TFXCK2wPgfBAB1VKWUPyjKQ6FQy";
            buildTxThenBroadcast(bsocialDataLockBuilder.buildUnFollow(identityKey).sign());
            System.out.println("you can view your bitcoin schema tx on https://blockpost.network/profile/" + bapBase.getIdentityKey());
        } catch (SignErrorException e) {
            e.printStackTrace();
        }
    }


    @Test
    public void testBsocialFollowTransaction(){
        try {
            BsocialDataLockBuilder bsocialDataLockBuilder = new BsocialDataLockBuilder(bapBase);
            String identityKey = "TFXCK2wPgfBAB1VKWUPyjKQ6FQy";
            buildTxThenBroadcast(bsocialDataLockBuilder.buildFollow(identityKey).sign());
            System.out.println("you can view your bitcoin schema tx on https://blockpost.network/profile/" + bapBase.getIdentityKey());
        } catch (SignErrorException e) {
            e.printStackTrace();
        }
    }



    @Test
    public void testBsocialPostTransaction(){
        try {
            B b = new B();
            b.setContent("BAP、BitcoinSchema、1sat ordinals、sigma protocol support for java");
            b.setByteBuffer(ByteBuffer.wrap(b.getContent().getBytes(Charsets.UTF_8)));
            b.setContentType("text/markdown");
            b.setEncoding("UTF-8");
            b.setIndex(1);

            BsocialDataLockBuilder bsocialDataLockBuilder = new BsocialDataLockBuilder(bapBase);
            buildTxThenBroadcast(bsocialDataLockBuilder.buildPost(Lists.newArrayList(b)).sign());
            System.out.println("you can view your bitcoin schema tx on https://blockpost.network/profile/" + bapBase.getIdentityKey());
        } catch (SignErrorException e) {
            e.printStackTrace();
        }
    }


    @Test
    public void testBsocialRePostTransaction(){
        try {
            B b = new B();
            b.setContent("it is  metanet4j-sdk");
            b.setByteBuffer(ByteBuffer.wrap(b.getContent().getBytes(Charsets.UTF_8)));
            b.setContentType("text/markdown");
            b.setEncoding("UTF-8");
            b.setIndex(1);

            String txId = "a1f3a65d5830d0018cdbe28436eca380a171d11c1799a8a322da74575e1bc1f0";
            BsocialDataLockBuilder bsocialDataLockBuilder = new BsocialDataLockBuilder(bapBase);
            buildTxThenBroadcast(bsocialDataLockBuilder.buildRepost(Lists.newArrayList(b), txId).sign());
            System.out.println("you can view your bitcoin schema tx on https://blockpost.network/profile/" + bapBase.getIdentityKey());
        } catch (SignErrorException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testBsocialReply(){
        try {
            B b = new B();
            b.setContent("it will be published on 2023-10-10");
            b.setByteBuffer(ByteBuffer.wrap(b.getContent().getBytes(Charsets.UTF_8)));
            b.setContentType("text/markdown");
            b.setEncoding("UTF-8");
            b.setIndex(1);

            String txId = "ff5299b196dc5170db17ecf1bb7387d295e02e08a0fbcb8bf96873c9eccbbf2a";
            BsocialDataLockBuilder bsocialDataLockBuilder = new BsocialDataLockBuilder(bapBase);
            buildTxThenBroadcast(bsocialDataLockBuilder.buildReply(Lists.newArrayList(b), txId).sign());
            System.out.println("you can view your bitcoin schema tx on https://blockpost.network/profile/" + bapBase.getIdentityKey());
        } catch (SignErrorException e) {
            e.printStackTrace();
        }
    }

    /**
     * 994b08e0aea2afa475c27ff48ac7c39fe5d49d50bbce38481ac931bdec2f1eb7
     *
     * @throws IOException
     */
    @Test
    public void testBsocialImapgePostTransaction() throws IOException {
        try {
            B bText = new B();
            bText.setContent("image test from metanet4j-sdk. date 2023-10-08");
            bText.setByteBuffer(ByteBuffer.wrap(bText.getContent().getBytes(Charsets.UTF_8)));
            bText.setContentType("text/markdown");
            bText.setEncoding("UTF-8");
            bText.setIndex(1);

            B bImage = new B();
            bImage.setContentType("image/jpeg");
            bImage.setFilename("bsv1.jpeg");
            bImage.setByteBuffer(ByteBuffer.wrap(FileUtil.getInputStream("bsv1.jpeg").readAllBytes()));
            bImage.setIndex(1);
            BsocialDataLockBuilder bsocialDataLockBuilder = new BsocialDataLockBuilder(bapBase);
            buildTxThenBroadcast(bsocialDataLockBuilder.buildPost(Lists.newArrayList(bImage)).sign());
            System.out.println("you can view your bitcoin schema tx on https://blockpost.network/profile/" + bapBase.getIdentityKey());
        } catch (SignErrorException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testBsocialLikeTransaction() throws IOException {
        try {
            String txId = "8e3faaf18418440fed963f5ce01fbe6d82195302d38daa30f8d1473b6197b418";
            BsocialDataLockBuilder bsocialDataLockBuilder = new BsocialDataLockBuilder(bapBase);
            buildTxThenBroadcast(bsocialDataLockBuilder.buildLike(txId, "").sign());
            System.out.println("you can view your bitcoin schema tx on https://blockpost.network/profile/" + bapBase.getIdentityKey());
        } catch (SignErrorException e) {
            e.printStackTrace();
        }
    }


    @Test
    public void testSendUtxo() {
        TransactionBuilder transactionBuilder = new TransactionBuilder(new BapProviderKeyBag(TestData.masterPrivateKey
                , 100));
        Transaction transaction = transactionBuilder
                .addInputs(Lists.newArrayList(paymentAddress),
                        new BitailsUtxoProvider())

                .withFeeKb(Coin.valueOf(500L))
                .changeAddress(Address.fromBase58(MainNetParams.get(), bapBase.getRootAddress()))
                .completeAndSignTx(transactionBuilder.getKeyBag(), true);

        correctlySpends(transaction);
        broadcast(transaction);
        parseTx(transaction);
    }


    @Test
    public void testSendUtxos() throws SignErrorException {
        TransactionBuilder transactionBuilder = new TransactionBuilder(new BapProviderKeyBag(TestData.masterPrivateKey
                , 100));
        Transaction transaction = transactionBuilder
                .addInputs(Lists.newArrayList(paymentAddress),
                        new BitailsUtxoProvider())
                .withFeeKb(Coin.valueOf(500L))
                .addOutput(Coin.valueOf(777L), Address.fromBase58(MainNetParams.get(), bapBase.getRootAddress()))
                .addOutput(Coin.valueOf(888L), Address.fromBase58(MainNetParams.get(), bapBase.getRootAddress()))
                .addOutput(Coin.valueOf(999L), Address.fromBase58(MainNetParams.get(), bapBase.getRootAddress()))
                .changeAddress(Address.fromBase58(MainNetParams.get(), bapBase.getRootAddress()))
                .completeAndSignTx(transactionBuilder.getKeyBag(), true);
        correctlySpends(transaction);
        broadcast(transaction);
        parseTx(transaction);

    }

    private void buildTxThenBroadcast(UnSpendableDataLockBuilder dataLockBuilder) throws SignErrorException {
        TransactionBuilder transactionBuilder = new TransactionBuilder(new BapProviderKeyBag(TestData.masterPrivateKey
                , 100));
        Transaction transaction = transactionBuilder
                .addInputs(Lists.newArrayList(paymentAddress),
                        new BitailsUtxoProvider())
                .addDataOutput(dataLockBuilder)
                .withFeeKb(Coin.valueOf(500L))
                .changeAddress(Address.fromBase58(MainNetParams.get(), bapBase.getRootAddress()))
                .completeAndSignTx(transactionBuilder.getKeyBag(), true);
        correctlySpends(transaction);
        broadcast(transaction);
        parseTx(transaction);


    }
}
