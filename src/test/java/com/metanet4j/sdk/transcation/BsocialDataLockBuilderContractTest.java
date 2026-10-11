package com.metanet4j.sdk.transcation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metanet4j.base.aip.AipHelper;
import com.metanet4j.base.model.B;
import com.metanet4j.base.model.BPP;
import com.metanet4j.base.model.Bsocial;
import com.metanet4j.base.model.MAP;
import com.metanet4j.base.type.BsocialTypeEnum;
import com.metanet4j.sdk.EcKeyLiteExtend;
import com.metanet4j.sdk.bap.SpecifyBapBase;
import com.metanet4j.sdk.context.PreSignHashContext;
import com.metanet4j.sdk.crypto.Ecies;
import com.metanet4j.sdk.remote.PreSignHashUtils;
import com.metanet4j.sdk.remote.RemoteSignType;
import com.metanet4j.bsv.script.ScriptChunk;
import com.metanet4j.sdk.utils.ScriptHelper;
import com.metanet4j.sdk.PrivateKey;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BsocialDataLockBuilderContractTest {

    private static final String B_PROTOCOL = "19HxigV4QyBv3tHpQVcUEQyq1pzZVdoAut";
    private static final String MAP_PROTOCOL = "1PuQa7K62MiKCtssSLKy1kh56WWU7MtUR5";

    private final SpecifyBapBase bap = new SpecifyBapBase(
            PrivateKey.of(BigInteger.TWO), PrivateKey.of(BigInteger.valueOf(3)),
            PrivateKey.of(BigInteger.valueOf(4)), "identity", "unit-app");

    @AfterEach
    void clearRemoteSignatures() {
        PreSignHashUtils.clear();
    }

    @Test
    void encryptedPostsRemainDecryptableWithoutChangingTheCallerContentType() throws Exception {
        B content = content("私密内容", "text/plain", 1);
        content.setEncrypted(true);
        byte[] plainText = content.getByteBuffer().array().clone();

        BsocialDataLockBuilder first = new BsocialDataLockBuilder(bap).buildPost(List.of(content));
        assertArrayEquals(plainText,
                Ecies.decrypt(ScriptHelper.getPushData(first.getLockingScript().getChunks().get(3)), bap.getEncryptKey().getKey(), null));
        assertEquals("application/bitcoin-ecies; content-type=text/plain",
                new String(ScriptHelper.getPushData(first.getLockingScript().getChunks().get(4)), StandardCharsets.UTF_8));
        assertEquals("text/plain", content.getContentType());
        assertArrayEquals(plainText, content.getByteBuffer().array());

        BsocialDataLockBuilder second = new BsocialDataLockBuilder(bap).buildPost(List.of(content));
        assertEquals("application/bitcoin-ecies; content-type=text/plain",
                new String(ScriptHelper.getPushData(second.getLockingScript().getChunks().get(4)), StandardCharsets.UTF_8));
        assertArrayEquals(plainText,
                Ecies.decrypt(ScriptHelper.getPushData(second.getLockingScript().getChunks().get(3)), bap.getEncryptKey().getKey(), null));
    }

    @Test
    void relationshipsAndReactionsUseTheirProtocolFieldNames() {
        assertMap(new BsocialDataLockBuilder(bap).buildFollow("identity-peer"),
                "follow", "idKey", "identity-peer");
        assertMap(new BsocialDataLockBuilder(bap).buildUnFollow("identity-peer"),
                "unfollow", "idKey", "identity-peer");
        assertMap(new BsocialDataLockBuilder(bap).buildLike("tx-reference"),
                "like", "context", "tx", "tx", "tx-reference");
        assertMap(new BsocialDataLockBuilder(bap).buildLike("tx-reference", ""),
                "like", "context", "tx", "tx", "tx-reference");
        assertMap(new BsocialDataLockBuilder(bap).buildLike("tx-reference", "👍"),
                "like", "context", "tx", "tx", "tx-reference", "emoji", "👍");
        assertMap(new BsocialDataLockBuilder(bap).buildUnLike("tx-reference"),
                "unlike", "context", "tx", "tx", "tx-reference");
        assertMap(new BsocialDataLockBuilder(bap).buildReply(List.of(), "tx-reference"),
                "post", "context", "tx", "tx", "tx-reference");
        assertMap(new BsocialDataLockBuilder(bap).buildRepost(List.of(), "tx-reference"),
                "repost", "tx", "tx-reference");
    }

    @Test
    void tipsIncludeCurrencyAndAmountOnlyWhenBothArePresent() {
        assertMap(new BsocialDataLockBuilder(bap).buildTip("tx-reference", "BSV", "0.01"),
                "tip", "context", "tx", "tx", "tx-reference", "currency", "BSV", "amount", "0.01");
        assertMap(new BsocialDataLockBuilder(bap).buildTip("tx-reference", null, "0.01"),
                "tip", "context", "tx", "tx", "tx-reference");
        assertMap(new BsocialDataLockBuilder(bap).buildTip("tx-reference", "BSV", ""),
                "tip", "context", "tx", "tx", "tx-reference");
        assertMap(new BsocialDataLockBuilder(bap).buildTip("tx-reference", "", null),
                "tip", "context", "tx", "tx", "tx-reference");
    }

    @Test
    void postsSupportTextExtensionsAndSeparateTagMaps() {
        B text = content("正文", "text/plain", 1);
        text.setEncoding("UTF-8");
        List<String> post = List.of(B_PROTOCOL, "正文", "text/plain", "UTF-8", "|",
                MAP_PROTOCOL, "SET", "app", "unit-app", "type", "post");
        assertEquals(post, fields(new BsocialDataLockBuilder(bap).buildPost(List.of(text))));
        List<String> extended = new ArrayList<>(post);
        extended.addAll(List.of("title", "标题"));
        assertEquals(extended, fields(new BsocialDataLockBuilder(bap)
                .buildPost(List.of(text), List.of("title", "标题"))));
        extended.addAll(List.of("|", MAP_PROTOCOL, "ADD", "tags", "java", "bitcoin"));
        assertEquals(extended, fields(new BsocialDataLockBuilder(bap)
                .buildPost(List.of(text), List.of("title", "标题"), List.of("java", "bitcoin"))));
        assertEquals(List.of(MAP_PROTOCOL, "SET", "app", "unit-app", "type", "post",
                        "|", MAP_PROTOCOL, "ADD", "tags"),
                fields(new BsocialDataLockBuilder(bap).buildPost(List.of(), null, List.of())));
    }

    @Test
    void mixedContentAndPaymentsAreSortedByIndexBeforeTheirMap() {
        B later = content("later", "text/plain", 3);
        B first = content("first", "text/plain", 1);
        first.setEncoding("UTF-8");
        BPP payment = new BPP();
        payment.setIndex(2);
        payment.setAction("payment");
        payment.setCurrency("BSV");
        payment.setAddress("recipient-address");
        payment.setApiEndpoint("https://example.invalid/payment");
        Bsocial social = social(map(BsocialTypeEnum.POST, "SET", "app", "unit-app", "type", "post"));
        social.setBList(List.of(later, first));
        social.setBppList(List.of(payment));
        BsocialDataLockBuilder builder = new BsocialDataLockBuilder(bap);
        assertSame(builder, builder.build(social));
        assertEquals(List.of(B_PROTOCOL, "first", "text/plain", "UTF-8", "|",
                "BPP", "payment", "BSV", "recipient-address", "https://example.invalid/payment", "|",
                B_PROTOCOL, "later", "text/plain", "|",
                MAP_PROTOCOL, "SET", "app", "unit-app", "type", "post"), fields(builder));
    }

    @Test
    void binaryContentWithoutEncodingKeepsItsBytesAndFieldBoundaries() {
        byte[] payload = new byte[] {0, (byte) 0xff, (byte) 0x80, 0x7c};
        B binary = content("unused", "application/octet-stream", 1);
        binary.setByteBuffer(ByteBuffer.wrap(payload));
        BsocialDataLockBuilder builder = new BsocialDataLockBuilder(bap).buildPost(List.of(binary));
        List<ScriptChunk> chunks = builder.getLockingScript().getChunks();
        assertEquals(12, chunks.size());
        assertArrayEquals(payload, ScriptHelper.getPushData(chunks.get(3)));
        assertEquals("application/octet-stream", new String(ScriptHelper.getPushData(chunks.get(4)), StandardCharsets.UTF_8));
        assertEquals("|", new String(ScriptHelper.getPushData(chunks.get(5)), StandardCharsets.UTF_8));
        assertEquals(MAP_PROTOCOL, new String(ScriptHelper.getPushData(chunks.get(6)), StandardCharsets.UTF_8));
    }

    @Test
    void paymentOnlyContentSupportsMultipleMapsWithoutATrailingSeparator() {
        BPP payment = new BPP();
        payment.setIndex(1);
        payment.setAction("payment");
        payment.setCurrency("BSV");
        payment.setAddress("recipient-address");
        payment.setApiEndpoint("https://example.invalid/payment");
        Bsocial social = social(map(BsocialTypeEnum.PAYMENT, "SET", "type", "payment"),
                map(BsocialTypeEnum.TAGS, "ADD", "tags", "paid"));
        social.setBList(List.of());
        social.setBppList(List.of(payment));
        assertEquals(List.of("BPP", "payment", "BSV", "recipient-address", "https://example.invalid/payment", "|",
                MAP_PROTOCOL, "SET", "type", "payment", "|", MAP_PROTOCOL, "ADD", "tags", "paid"),
                fields(new BsocialDataLockBuilder(bap).build(social)));
    }

    @Test
    void callersCanSupplyAnAlreadyEncryptedPayloadWithoutAnEncryptionKey() throws Exception {
        byte[] plaintext = "外部加密".getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = bap.encrypt(plaintext);
        SpecifyBapBase external = new SpecifyBapBase(bap.getCurrentPrivateKey(), bap.getRootPrivateKey(),
                "identity", "unit-app");
        B content = content("unused", "text/plain", 1);
        content.setEncrypted(true);
        content.setByteBuffer(ByteBuffer.wrap(ciphertext));
        BsocialDataLockBuilder builder = new BsocialDataLockBuilder(external).buildPost(List.of(content));
        assertArrayEquals(ciphertext, ScriptHelper.getPushData(builder.getLockingScript().getChunks().get(3)));
        assertArrayEquals(plaintext,
                Ecies.decrypt(ScriptHelper.getPushData(builder.getLockingScript().getChunks().get(3)), bap.getEncryptKey().getKey(), null));
        assertEquals("application/bitcoin-ecies; content-type=text/plain",
                new String(ScriptHelper.getPushData(builder.getLockingScript().getChunks().get(4)), StandardCharsets.UTF_8));
    }

    @Test
    void suppliedProtocolPrefixIsKeptAndRemotePostSignatureVerifies() {
        List<ByteBuffer> prefix = new ArrayList<>(List.of(ByteBuffer.wrap("prefix".getBytes(StandardCharsets.UTF_8))));
        BsocialDataLockBuilder prefixed = new BsocialDataLockBuilder(prefix, bap).buildFollow("peer");
        assertEquals(List.of("prefix", MAP_PROTOCOL, "SET", "app", "unit-app", "type", "follow", "idKey", "peer"),
                fields(prefixed));

        BsocialDataLockBuilder remote = new BsocialDataLockBuilder(bap, true, RemoteSignType.CURRENT)
                .buildPost(List.of(content("正文", "text/plain", 1)));
        assertSame(bap, remote.getBapBase());
        assertTrue(remote.isRemoteSign());
        assertEquals(RemoteSignType.CURRENT, remote.getRemoteSignType());
        assertFalse(remote.isHaveSign());
        PreSignHashContext request = remote.getPreSignHashContext();
        request.setSigB64(EcKeyLiteExtend.fromPrivate(bap.getCurrentPrivateKey().getPrivKey())
                .signHash(request.getPreSignHash()));
        PreSignHashUtils.setPreSignHashContext(request);
        remote.sign();
        List<ScriptChunk> chunks = remote.getLockingScript().getChunks();
        String signature = Base64.getEncoder().encodeToString(ScriptHelper.getPushData(chunks.getLast()));
        byte[] message = ("j" + B_PROTOCOL + "正文text/plain|" + MAP_PROTOCOL
                + "SETappunit-apptypepost|").getBytes(StandardCharsets.UTF_8);
        assertTrue(AipHelper.verifySign(bap.getCurrentAddress().toBase58(), signature, message));
        assertTrue(remote.isHaveSign());
    }

    @Test
    void malformedContentAndMapsAreRejected() {
        Bsocial noMaps = new Bsocial();
        assertThrows(IllegalArgumentException.class, () -> new BsocialDataLockBuilder(bap).build(noMaps));
        noMaps.setMapList(List.of());
        assertThrows(IllegalArgumentException.class, () -> new BsocialDataLockBuilder(bap).build(noMaps));
        assertThrows(IllegalArgumentException.class, () -> new BsocialDataLockBuilder(bap)
                .buildPost(List.of(content("data", "text/plain", 0))));
        B noBytes = content("data", "text/plain", 1);
        noBytes.setByteBuffer(null);
        assertThrows(IllegalArgumentException.class, () -> new BsocialDataLockBuilder(bap).buildPost(List.of(noBytes)));
        assertThrows(IllegalArgumentException.class, () -> new BsocialDataLockBuilder(bap)
                .buildPost(List.of(content("data", "", 1))));
        assertThrows(IllegalArgumentException.class, () -> new BsocialDataLockBuilder(bap)
                .build(social(map(null, "SET"))));
        assertThrows(IllegalArgumentException.class, () -> new BsocialDataLockBuilder(bap)
                .build(social(map(BsocialTypeEnum.POST))));
        assertThrows(IllegalArgumentException.class, () -> new BsocialDataLockBuilder(bap)
                .buildPost(List.of(), List.of(), null));
    }

    @Test
    void malformedMapIsRejectedWhenItAppearsAfterAValidMap() {
        MAP valid = map(BsocialTypeEnum.POST, "SET", "app", "unit-app", "type", "post");
        MAP missingType = map(null, "SET", "type", "post");
        assertThrows(IllegalArgumentException.class,
                () -> new BsocialDataLockBuilder(bap).build(social(valid, missingType)));
        MAP missingFields = map(BsocialTypeEnum.TAGS);
        assertThrows(IllegalArgumentException.class,
                () -> new BsocialDataLockBuilder(bap).build(social(valid, missingFields)));
    }

    private static void assertMap(BsocialDataLockBuilder builder, String type, String... extension) {
        List<String> expected = new ArrayList<>(List.of(MAP_PROTOCOL, "SET", "app", "unit-app", "type", type));
        expected.addAll(Arrays.asList(extension));
        assertEquals(expected, fields(builder));
        assertEquals(0, builder.getLockingScript().getChunks().get(0).op);
        assertEquals(0x6a, builder.getLockingScript().getChunks().get(1).op);
    }

    private static List<String> fields(BsocialDataLockBuilder builder) {
        return builder.getLockingScript().getChunks().stream().skip(2)
                .map(chunk -> new String(ScriptHelper.getPushData(chunk), StandardCharsets.UTF_8)).toList();
    }

    private static Bsocial social(MAP... maps) {
        Bsocial social = new Bsocial();
        social.setMapList(Arrays.asList(maps));
        return social;
    }

    private static MAP map(BsocialTypeEnum type, String... fields) {
        MAP map = new MAP();
        map.setBsocialTypeEnum(type);
        map.setMapDataList(Arrays.asList(fields));
        return map;
    }

    private static B content(String text, String contentType, int index) {
        B b = new B();
        b.setByteBuffer(ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8)));
        b.setContentType(contentType);
        b.setIndex(index);
        return b;
    }
}
