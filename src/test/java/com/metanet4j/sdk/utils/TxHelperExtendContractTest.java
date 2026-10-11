package com.metanet4j.sdk.utils;

import static com.metanet4j.bsv.script.OP.OP_NOP;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.hutool.core.codec.Base64;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.script.ScriptChunk;
import com.metanet4j.bsv.script.templates.P2PKH;
import com.metanet4j.bsv.transaction.Transaction;
import com.metanet4j.bsv.transaction.TransactionOutput;
import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.bap.BapBase;
import com.metanet4j.sdk.ordinals.OrdScriptBuilder;
import com.metanet4j.sdk.ordinals.OrdScriptBuilder.ORDMap;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link TxHelperExtend} 的 Ordinals 脚本识别与地址解析契约。
 *
 * <p>迁移要点：脚本与地址全用新 SDK 类型（{@code Script}/{@code AddressEnhance}）；
 * P2PKH 前缀由 {@code new P2PKH().lock(hash160)} 生成；chunk 访问用 {@code op} 字段与
 * {@code ScriptHelper.getPushData}；{@code getConnectedRedeemDataExtend} 的入参由旧库 outpoint 改为
 * **连接输出**（{@code TransactionOutput}）与显式 {@code SigningKeyProvider}（计划 §3）。
 */
class TxHelperExtendContractTest {

    @Test
    void extractsDestinationFromInscriptionWithOptionalMapMetadata() {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        AddressEnhance destination = bap.getOrdAddress();
        ORDMap metadata = new ORDMap();
        metadata.put("app", "test");
        metadata.put("type", "image");
        Script script = OrdScriptBuilder.buildInscription(destination,
                "body".getBytes(StandardCharsets.UTF_8), "text/plain", metadata);
        String encoded = Base64.encode(script.toUint8Array());

        assertTrue(TxHelperExtend.isSentToOrdinals(script));
        assertArrayEquals(destination.getHash160(), TxHelperExtend.getPubKeyHashByOrdinalScript(script));
        assertArrayEquals(destination.getHash160(), TxHelperExtend.getPubKeyHashByOrdinalScript(encoded));
        assertEquals(destination.toBase58(), TxHelperExtend.getAddressByOrdinalScript(script));
        assertEquals(destination.toBase58(), TxHelperExtend.getAddressByOrdinalScript(encoded));
    }

    @Test
    void rejectsOrdinaryAndIncompleteInscriptionScripts() {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        AddressEnhance destination = bap.getOrdAddress();
        Script payment = new P2PKH().lock(destination.toBase58());
        Script inscription = OrdScriptBuilder.buildInscription(destination,
                new byte[] {1}, "application/octet-stream", null);

        assertFalse(TxHelperExtend.isSentToOrdinals(payment));
        assertArrayEquals(new byte[0], TxHelperExtend.getPubKeyHashByOrdinalScript(payment));
        assertFalse(TxHelperExtend.isSentToOrdinals(new Script()));

        List<ScriptChunk> valid = inscription.getChunks();
        for (int invalidPosition = 0; invalidPosition < 13; invalidPosition++) {
            List<ScriptChunk> changed = new ArrayList<>(valid.size());
            for (int i = 0; i < valid.size(); i++) {
                if (i == invalidPosition) {
                    if (i == 2) {
                        changed.add(new ScriptChunk(19, toBoxed(new byte[19])));
                    } else if (i == 7) {
                        changed.add(new ScriptChunk(5,
                                toBoxed("other".getBytes(StandardCharsets.US_ASCII))));
                    } else {
                        changed.add(new ScriptChunk(OP_NOP));
                    }
                } else {
                    changed.add(valid.get(i));
                }
            }
            Script malformed = new Script(changed);
            assertFalse(TxHelperExtend.isSentToOrdinals(malformed), "chunk " + invalidPosition);
            assertArrayEquals(new byte[0], TxHelperExtend.getPubKeyHashByOrdinalScript(malformed));
        }
    }

    @Test
    void resolvesConnectedInscriptionAndOrdinaryPaymentThroughKeyProvider() {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        BapBase.BapProviderKeyBag bag = new BapBase.BapProviderKeyBag(bap);
        AddressEnhance destination = bap.getOrdAddress();
        Script inscription = OrdScriptBuilder.buildInscription(destination,
                new byte[] {1, 2}, "image/png", null);

        RedeemDataExtend ordinal = TxHelperExtend.getConnectedRedeemDataExtend(output(inscription), bag);
        assertNotNull(ordinal);
        assertArrayEquals(inscription.toUint8Array(), ordinal.redeemScript.toUint8Array(),
                "赎回脚本应回填连接输出的锁定脚本");
        assertEquals(bap.getOrdPrivateKey().getPrivKey(), ordinal.getFullKey().getPrivKey());

        Script payment = new P2PKH().lock(destination.toBase58());
        RedeemDataExtend ordinary = TxHelperExtend.getConnectedRedeemDataExtend(output(payment), bag);
        assertNotNull(ordinary);
        assertArrayEquals(payment.toUint8Array(), ordinary.redeemScript.toUint8Array());
        assertEquals(bap.getOrdPrivateKey().getPrivKey(), ordinary.getFullKey().getPrivKey());
    }

    @Test
    void rejectsDisconnectedOutputAndUnknownInscriptionKey() {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        BapBase.BapProviderKeyBag bag = new BapBase.BapProviderKeyBag(bap);

        assertNull(TxHelperExtend.getConnectedRedeemDataExtend(null, bag),
                "无连接输出时应返回 null 而非抛错（新签名以 TransactionOutput 表达连接输出）");

        byte[] unknownHash = new byte[20];
        Arrays.fill(unknownHash, (byte) 7);
        AddressEnhance unknown = AddressEnhance.fromPubKeyHash(unknownHash);
        Script inscription = OrdScriptBuilder.buildInscription(unknown, new byte[] {1}, "text/plain", null);
        assertNull(TxHelperExtend.getConnectedRedeemDataExtend(output(inscription), bag),
                "密钥提供者不认识该地址时应返回 null");
    }

    /** 由脚本构造连接输出（新签名用 TransactionOutput 表达「连接输出」）。 */
    private static TransactionOutput output(Script script) {
        Transaction funding = new Transaction();
        funding.outputs.add(new TransactionOutput(script, 546d));
        return funding.outputs.get(0);
    }

    private static List<Integer> toBoxed(byte[] data) {
        List<Integer> out = new ArrayList<>(data.length);
        for (byte b : data) {
            out.add(b & 0xFF);
        }
        return out;
    }
}
