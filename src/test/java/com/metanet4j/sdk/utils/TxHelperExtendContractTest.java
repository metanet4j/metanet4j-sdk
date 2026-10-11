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
import com.metanet4j.bsv.script.OP;
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
        // 恢复基线语义（6efd289 该用例第 119—120 行）：密钥提供者不认识该地址时**抛出** RuntimeException，
        // 而不是返回 null。第 82 轮迁移时曾误改为 assertNull，属改写过头，此处按基线纠正。
        assertThrows(RuntimeException.class,
                () -> TxHelperExtend.getConnectedRedeemDataExtend(output(inscription), bag));
    }

    @Test
    void coversDefensiveBranchesAndThePublicHashListAccessor() {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        BapBase.BapProviderKeyBag bag = new BapBase.BapProviderKeyBag(bap);

        // 防御分支：无连接输出（connectedOutput == null）应返回 null 而非抛错
        assertNull(TxHelperExtend.getConnectedRedeemDataExtend(null, bag),
                "无连接输出时返回 null");

        // 公共访问器 pubKeyHashList：其定位是「按 Ordinals 铭文脚本取公钥哈希」，
        // 对非铭文脚本（含空脚本）返回空列表——覆盖该方法体与装箱循环。
        assertTrue(TxHelperExtend.pubKeyHashList(new Script()).isEmpty(),
                "非铭文脚本应得到空列表");
    }

    @Test
    void coversPushdataEncodingVariantsAndTheOrdinalHashLookup() {
        // isPushData 的末位操作数为 `op < OP_PUSHDATA1`，其**假侧**只有在 push 采用
        // PUSHDATA1／2／4 编码时才会走到（writeBin 按数据长度自动选择编码：
        // <76 直接推送、<256 用 PUSHDATA1、<65536 用 PUSHDATA2）。
        List<Integer> hash20 = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            hash20.add(9);
        }
        List<Integer> ord = List.of(0x6F, 0x72, 0x64);

        for (int payloadSize : new int[] {3, 100, 300}) {
            List<Integer> payload = new ArrayList<>();
            for (int i = 0; i < payloadSize; i++) {
                payload.add(i & 0xFF);
            }
            Script script = new Script();
            script.writeOpCode(OP.OP_DUP).writeOpCode(OP.OP_HASH160).writeBin(hash20)
                    .writeOpCode(OP.OP_EQUALVERIFY).writeOpCode(OP.OP_CHECKSIG)
                    .writeOpCode(OP.OP_0).writeOpCode(OP.OP_IF).writeBin(ord)
                    .writeOpCode(OP.OP_1).writeBin(payload)
                    .writeOpCode(OP.OP_0).writeBin(payload)
                    .writeOpCode(OP.OP_ENDIF);

            assertTrue(TxHelperExtend.isSentToOrdinals(script),
                    payloadSize + " 字节负载（不同 push 编码）仍应判为铭文");
            // 同一脚本走公钥哈希提取路径——覆盖 getPubKeyHashByOrdinalScript 内部
            // 的 chunksOf 调用与 pubKeyHashList 的循环
            assertEquals(hash20, TxHelperExtend.pubKeyHashList(script),
                    "ordinal 脚本应能取出第 2 段的公钥哈希");
        }

        // 第 9 段改用纯操作码（chunk.data == null 且 op == 0）→ 覆盖 isPushData 左侧为假的两种情形
        Script opcodeSlot = new Script();
        opcodeSlot.writeOpCode(OP.OP_DUP).writeOpCode(OP.OP_HASH160).writeBin(hash20)
                .writeOpCode(OP.OP_EQUALVERIFY).writeOpCode(OP.OP_CHECKSIG)
                .writeOpCode(OP.OP_0).writeOpCode(OP.OP_IF).writeBin(ord)
                .writeOpCode(OP.OP_1).writeOpCode(OP.OP_0)
                .writeOpCode(OP.OP_0).writeBin(List.of(1, 2, 3))
                .writeOpCode(OP.OP_ENDIF);
        assertFalse(TxHelperExtend.isSentToOrdinals(opcodeSlot), "第 9 段非推送数据时不应判为铭文");

        // 第 9 段：data 为非空列表但 op == 0 → 覆盖 isPushData 中 op != 0 为假的一侧
        List<ScriptChunk> withZeroOp = new ArrayList<>();
        withZeroOp.add(new ScriptChunk(OP.OP_DUP));
        withZeroOp.add(new ScriptChunk(OP.OP_HASH160));
        withZeroOp.add(new ScriptChunk(20, hash20));
        withZeroOp.add(new ScriptChunk(OP.OP_EQUALVERIFY));
        withZeroOp.add(new ScriptChunk(OP.OP_CHECKSIG));
        withZeroOp.add(new ScriptChunk(OP.OP_0));
        withZeroOp.add(new ScriptChunk(OP.OP_IF));
        withZeroOp.add(new ScriptChunk(3, ord));
        withZeroOp.add(new ScriptChunk(OP.OP_1));
        withZeroOp.add(new ScriptChunk(0, List.of(1, 2, 3)));
        withZeroOp.add(new ScriptChunk(OP.OP_0));
        withZeroOp.add(new ScriptChunk(3, List.of(1, 2, 3)));
        withZeroOp.add(new ScriptChunk(OP.OP_ENDIF));
        assertFalse(TxHelperExtend.isSentToOrdinals(new Script(withZeroOp)),
                "第 9 段 op 为 0 时不应判为铭文");

        // 第 9 段：data 为空列表 → 覆盖 isPushData 中 !data.isEmpty() 为假的一侧
        List<ScriptChunk> withEmptyData = new ArrayList<>(withZeroOp);
        withEmptyData.set(9, new ScriptChunk(OP.OP_PUSHDATA1, List.of()));
        assertFalse(TxHelperExtend.isSentToOrdinals(new Script(withEmptyData)),
                "第 9 段推送空数据时不应判为铭文");
    }

    @Test
    void coversChunkShapesThatSurviveOnlyAsRawBytes() {
        // 以**原始字节**构造脚本：new Script(List<ScriptChunk>) 会重新序列化，
        // 「空推送」「纯操作码」这类精确形态只有直接给字节才能保真。
        byte[] head = {(byte) 0x76, (byte) 0xa9, 0x14, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9,
                9, 9, (byte) 0x88, (byte) 0xac, 0x00, 0x63, 0x03, 0x6f, 0x72, 0x64, 0x51};
        byte[] tail = {0x00, 0x03, 1, 2, 3, (byte) 0x68};

        // 第 9 段 = OP_PUSHDATA1 且长度 0 → data 为空列表，覆盖 !data.isEmpty() 为假的一侧
        byte[] emptyPush = new byte[head.length + 2 + tail.length];
        System.arraycopy(head, 0, emptyPush, 0, head.length);
        emptyPush[head.length] = 0x4c;
        emptyPush[head.length + 1] = 0x00;
        System.arraycopy(tail, 0, emptyPush, head.length + 2, tail.length);
        assertFalse(TxHelperExtend.isSentToOrdinals(Script.fromBinary(emptyPush)),
                "第 9 段为空推送时不应判为铭文");

        // 第 9 段 = 纯操作码 OP_1 → data 为 null，覆盖 data != null 为假的一侧
        byte[] opcodePush = new byte[head.length + 1 + tail.length];
        System.arraycopy(head, 0, opcodePush, 0, head.length);
        opcodePush[head.length] = 0x51;
        System.arraycopy(tail, 0, opcodePush, head.length + 1, tail.length);
        assertFalse(TxHelperExtend.isSentToOrdinals(Script.fromBinary(opcodePush)),
                "第 9 段为纯操作码时不应判为铭文");

        // 第 2 段 = 纯操作码（无数据）但其余各段齐备 → 覆盖 dataOf(chunks.get(2)) != null 为假
        byte[] noHash = {(byte) 0x76, (byte) 0xa9, 0x51, (byte) 0x88, (byte) 0xac, 0x00, 0x63, 0x03, 0x6f, 0x72,
                0x64, 0x51, 0x03, 1, 2, 3, 0x00, 0x03, 1, 2, 3, (byte) 0x68};
        assertFalse(TxHelperExtend.isSentToOrdinals(Script.fromBinary(noHash)),
                "第 2 段无数据时不应判为铭文");

        // 对照：由原始字节拼出的合法脚本应判为铭文
        byte[] full = new byte[noHash.length + 20];
        System.arraycopy(noHash, 0, full, 0, 2);
        full[2] = 0x14;
        for (int i = 0; i < 20; i++) {
            full[3 + i] = 9;
        }
        System.arraycopy(noHash, 3, full, 23, noHash.length - 3);
        assertTrue(TxHelperExtend.isSentToOrdinals(Script.fromBinary(full)),
                "由原始字节拼出的合法脚本应判为铭文");
    }

    @Test
    void rejectsMissingConnectedOutputWhenResolvingSpendInfo() {
        // 覆盖 getConnectedRedeemDataExtend 中「连接输出为空」的短路分支
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        assertNull(TxHelperExtend.getConnectedRedeemDataExtend(
                        null, new BapBase.BapProviderKeyBag(bap)),
                "连接输出为空时应返回 null 而非抛错");
        // 合法 P2PKH（第 3 段为 20 字节哈希）→ 覆盖 `length == ADDRESS_LENGTH` 为真的一侧，
        // 使 pubKeyHashOfP2pkh 走到 return 分支
        List<Integer> hash20 = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            hash20.add(11);
        }
        List<ScriptChunk> validP2pkh = new ArrayList<>();
        validP2pkh.add(new ScriptChunk(OP.OP_DUP));
        validP2pkh.add(new ScriptChunk(OP.OP_HASH160));
        validP2pkh.add(new ScriptChunk(20, hash20));
        validP2pkh.add(new ScriptChunk(OP.OP_EQUALVERIFY));
        validP2pkh.add(new ScriptChunk(OP.OP_CHECKSIG));
        // 注意：pubKeyHashList 只走**铭文**路径，对纯 P2PKH 返回空（§159 已确认的既定语义），
        // 故此处必须经 getConnectedRedeemDataExtend 触发 pubKeyHashOfP2pkh 的为真分支——
        // 哈希提取发生在密钥查找之前，故即便该地址不属于本密钥包也足以覆盖。
        assertThrows(RuntimeException.class,
                () -> TxHelperExtend.getConnectedRedeemDataExtend(
                        new com.metanet4j.bsv.transaction.TransactionOutput(
                                new Script(validP2pkh), 1d),
                        new BapBase.BapProviderKeyBag(bap)),
                "合法 P2PKH 会走到密钥查找；该地址不属于本密钥包时提供者抛错");

        // 5 段 P2PKH 形状但第 3 段数据长度 19 ≠ 20 → 覆盖 pubKeyHashOfP2pkh 中
        // `dataOf(...).length == ADDRESS_LENGTH` 为假的一侧
        List<Integer> hash19 = new ArrayList<>();
        for (int i = 0; i < 19; i++) {
            hash19.add(7);
        }
        List<ScriptChunk> shortHashP2pkh = new ArrayList<>();
        shortHashP2pkh.add(new ScriptChunk(OP.OP_DUP));
        shortHashP2pkh.add(new ScriptChunk(OP.OP_HASH160));
        shortHashP2pkh.add(new ScriptChunk(19, hash19));
        shortHashP2pkh.add(new ScriptChunk(OP.OP_EQUALVERIFY));
        shortHashP2pkh.add(new ScriptChunk(OP.OP_CHECKSIG));
        assertNull(TxHelperExtend.getConnectedRedeemDataExtend(
                        new com.metanet4j.bsv.transaction.TransactionOutput(
                                new Script(shortHashP2pkh), 1d),
                        new BapBase.BapProviderKeyBag(bap)),
                "公钥哈希长度为 19 时不应判为 P2PKH");

        // 输出非空但其锁定脚本为 null → 覆盖 || 右侧为真的分支
        assertNull(TxHelperExtend.getConnectedRedeemDataExtend(
                        new com.metanet4j.bsv.transaction.TransactionOutput(null, 1d),
                        new BapBase.BapProviderKeyBag(bap)),
                "锁定脚本为空时应返回 null 而非抛错");
    }

    @Test
    void walksTheWholeOrdinalLockingScriptChainAndItsFailureSides() {
        // isSentToOrdinals 是一条 13 段判定链；本用例构造**合法**的 Ordinals 锁定脚本
        // 使其全部为真，再逐段破坏以覆盖各短路分支，另覆盖 catch 分支。
        List<Integer> hash20 = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            hash20.add(9);
        }
        List<Integer> ord = List.of(0x6F, 0x72, 0x64);   // "ord"
        List<Integer> payload = List.of(1, 2, 3);

        java.util.function.BiFunction<Integer, List<Integer>, Script> build = (brokenAt, _ignored) -> {
            Script script = new Script();
            for (int i = 0; i < 13; i++) {
                switch (i) {
                    case 0 -> script.writeOpCode(OP.OP_DUP);
                    case 1 -> script.writeOpCode(OP.OP_HASH160);
                    case 2 -> script.writeBin(hash20);
                    case 3 -> script.writeOpCode(OP.OP_EQUALVERIFY);
                    case 4 -> script.writeOpCode(OP.OP_CHECKSIG);
                    case 5 -> script.writeOpCode(OP.OP_0);
                    case 6 -> script.writeOpCode(OP.OP_IF);
                    case 7 -> script.writeBin(ord);
                    case 8 -> script.writeOpCode(OP.OP_1);
                    case 9 -> script.writeBin(payload);
                    case 10 -> script.writeOpCode(OP.OP_0);
                    case 11 -> script.writeBin(payload);
                    default -> script.writeOpCode(OP.OP_ENDIF);
                }
            }
            return script;
        };

        assertTrue(TxHelperExtend.isSentToOrdinals(build.apply(-1, null)),
                "完整的 13 段 Ordinals 脚本应判定为铭文");

        // null 入参触发 catch 分支
        assertFalse(TxHelperExtend.isSentToOrdinals(null), "null 脚本应安全返回 false");

        // 逐段破坏（把某一段换成 OP_1 或改写其数据）——覆盖各条件为假的分支
        // 注意：第 8 段本就应为 OP_1，用 OP_1 替换等于未破坏，故不列入；
        // 第 9／11 段是 pushdata，其变体由下面的 isPushData 用例覆盖。
        for (int slot : new int[] {0, 1, 3, 4, 5, 6, 10, 12}) {
            Script script = new Script();
            for (int i = 0; i < 13; i++) {
                if (i == slot) {
                    script.writeOpCode(OP.OP_1);
                } else {
                    switch (i) {
                        case 0 -> script.writeOpCode(OP.OP_DUP);
                        case 1 -> script.writeOpCode(OP.OP_HASH160);
                        case 2 -> script.writeBin(hash20);
                        case 3 -> script.writeOpCode(OP.OP_EQUALVERIFY);
                        case 4 -> script.writeOpCode(OP.OP_CHECKSIG);
                        case 5 -> script.writeOpCode(OP.OP_0);
                        case 6 -> script.writeOpCode(OP.OP_IF);
                        case 7 -> script.writeBin(ord);
                        case 8 -> script.writeOpCode(OP.OP_1);
                        case 9 -> script.writeBin(payload);
                        case 10 -> script.writeOpCode(OP.OP_0);
                        case 11 -> script.writeBin(payload);
                        default -> script.writeOpCode(OP.OP_ENDIF);
                    }
                }
            }
            assertFalse(TxHelperExtend.isSentToOrdinals(script), "第 " + slot + " 段被破坏后不应判为铭文");
        }

        // 第 2 段改为纯操作码（dataOf 为 null）→ 覆盖 dataOf(...) != null 为假的一侧
        Script noHashSlot = new Script();
        for (int i = 0; i < 13; i++) {
            switch (i) {
                case 0 -> noHashSlot.writeOpCode(OP.OP_DUP);
                case 1 -> noHashSlot.writeOpCode(OP.OP_HASH160);
                case 2 -> noHashSlot.writeOpCode(OP.OP_1);
                case 3 -> noHashSlot.writeOpCode(OP.OP_EQUALVERIFY);
                case 4 -> noHashSlot.writeOpCode(OP.OP_CHECKSIG);
                case 5 -> noHashSlot.writeOpCode(OP.OP_0);
                case 6 -> noHashSlot.writeOpCode(OP.OP_IF);
                case 7 -> noHashSlot.writeBin(ord);
                case 8 -> noHashSlot.writeOpCode(OP.OP_1);
                case 9 -> noHashSlot.writeBin(payload);
                case 10 -> noHashSlot.writeOpCode(OP.OP_0);
                case 11 -> noHashSlot.writeBin(payload);
                default -> noHashSlot.writeOpCode(OP.OP_ENDIF);
            }
        }
        assertFalse(TxHelperExtend.isSentToOrdinals(noHashSlot), "第 2 段无数据时不应判为铭文");

        // 第 2 段数据长度为 19（≠20）→ 覆盖 length == ADDRESS_LENGTH 为假的一侧
        List<Integer> hash19 = new ArrayList<>();
        for (int i = 0; i < 19; i++) {
            hash19.add(9);
        }
        Script shortHash = new Script();
        shortHash.writeOpCode(OP.OP_DUP).writeOpCode(OP.OP_HASH160).writeBin(hash19)
                .writeOpCode(OP.OP_EQUALVERIFY).writeOpCode(OP.OP_CHECKSIG)
                .writeOpCode(OP.OP_0).writeOpCode(OP.OP_IF).writeBin(ord)
                .writeOpCode(OP.OP_1).writeBin(payload)
                .writeOpCode(OP.OP_0).writeBin(payload)
                .writeOpCode(OP.OP_ENDIF);
        assertFalse(TxHelperExtend.isSentToOrdinals(shortHash), "公钥哈希长度为 19 时不应判为铭文");

        // 第 7 段数据不是 "ord" → 覆盖 Arrays.equals 为假的一侧
        Script wrongTag = new Script();
        for (int i = 0; i < 13; i++) {
            switch (i) {
                case 0 -> wrongTag.writeOpCode(OP.OP_DUP);
                case 1 -> wrongTag.writeOpCode(OP.OP_HASH160);
                case 2 -> wrongTag.writeBin(hash20);
                case 3 -> wrongTag.writeOpCode(OP.OP_EQUALVERIFY);
                case 4 -> wrongTag.writeOpCode(OP.OP_CHECKSIG);
                case 5 -> wrongTag.writeOpCode(OP.OP_0);
                case 6 -> wrongTag.writeOpCode(OP.OP_IF);
                case 7 -> wrongTag.writeBin(List.of(1, 2, 3));
                case 8 -> wrongTag.writeOpCode(OP.OP_1);
                case 9 -> wrongTag.writeBin(payload);
                case 10 -> wrongTag.writeOpCode(OP.OP_0);
                case 11 -> wrongTag.writeBin(payload);
                default -> wrongTag.writeOpCode(OP.OP_ENDIF);
            }
        }
        assertFalse(TxHelperExtend.isSentToOrdinals(wrongTag), "标记不是 ord 时不应判为铭文");
    }

    @Test
    void coversMalformedScriptBranchesOfTheOrdinalHelpers() {
        // 「chunk 数不足 13」→ isSentToOrdinals 的短路分支
        assertFalse(TxHelperExtend.isSentToOrdinals(new Script()));

        // 13 个 chunk 但首段不是 OP_DUP → 第二段短路分支
        List<ScriptChunk> wrongFirst = new ArrayList<>();
        wrongFirst.add(new ScriptChunk(OP.OP_1));
        for (int i = 1; i < 13; i++) {
            wrongFirst.add(new ScriptChunk(OP.OP_1));
        }
        assertFalse(TxHelperExtend.isSentToOrdinals(new Script(wrongFirst)));

        // pubKeyHashOfP2pkh 的五段结构判定只能经 getConnectedRedeemDataExtend 触发
        // （pubKeyHashList 只走铭文路径，对纯 P2PKH 返回空——这是该访问器的既定语义）。
        BapBase owner = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        BapBase.BapProviderKeyBag bag = new BapBase.BapProviderKeyBag(owner);
        Script p2pkh = new P2PKH().lock(owner.getPayAccountAddress().toBase58());
        List<ScriptChunk> ok = new ArrayList<>(p2pkh.getChunks());
        assertNotNull(TxHelperExtend.getConnectedRedeemDataExtend(output(p2pkh), bag),
                "合法 P2PKH 且密钥可解析时应能得到赎回数据");

        // 逐位破坏五段结构，覆盖各条件为假的分支（同时覆盖「公钥哈希为空 → 返回 null」分支）
        for (int i : new int[] {0, 1, 3, 4}) {
            List<ScriptChunk> broken = new ArrayList<>(ok);
            broken.set(i, new ScriptChunk(OP.OP_1));
            assertNull(TxHelperExtend.getConnectedRedeemDataExtend(output(new Script(broken)), bag),
                    "第 " + i + " 段被破坏后不应判为 P2PKH");
        }
        // 第 2 段 data 为 null → 覆盖 dataOf 的 null 分支
        List<ScriptChunk> nullData = new ArrayList<>(ok);
        nullData.set(2, new ScriptChunk(20));
        assertNull(TxHelperExtend.getConnectedRedeemDataExtend(output(new Script(nullData)), bag),
                "公钥哈希段无数据时不应判为 P2PKH");
        // chunk 数不等于 5 → 首个短路分支
        assertNull(TxHelperExtend.getConnectedRedeemDataExtend(
                output(new Script(List.of(new ScriptChunk(OP.OP_DUP)))), bag));
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
