package com.metanet4j.sdk.script;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.metanet4j.bsv.primitives.Hash;
import com.metanet4j.bsv.script.OP;
import com.metanet4j.bsv.primitives.TransactionSignature;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.script.templates.P2PKH;
import com.metanet4j.sdk.PrivateKey;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.ordinals.OrdScriptBuilder;
import com.metanet4j.sdk.utils.ScriptHelper;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

/**
 * {@link ScriptExtend} 的脚本判定、签名位填充与花费尺寸契约（migration-rules §11 冻结的 9 条断言）。
 *
 * <p>迁移要点：{@code ScriptExtend} 已由「继承旧 Script 的实例类」改为工具类，故
 * 构造脚本改用新 SDK 的 {@code Script.writeOpCode/writeBin} 与 {@code P2PKH.lock}；
 * 花费尺寸与签名填充改为静态入口；{@code SIG_SIZE} 由 {@code ScriptExtend.SIG_SIZE} 提供（75）。
 */
class ScriptExtendContractTest {

    private static final PrivateKey KEY = PrivateKey.of(BigInteger.TWO);
    private static final AddressEnhance KEY_ADDRESS = AddressEnhance.fromPrivateKey(KEY);
    private static final byte[] SIGNATURE = {1, 2, 3};

    @Test
    void mapsEveryBaseSignatureScopeIncludingNone() {
        // §91 把 SIGHASH 映射集中到 signOutputsOf；其中 case 2（SIGHASH_NONE）此前从未被任何用例覆盖，
        // 属正常业务路径上的功能分支缺失（非防御冗余），故此处逐一断言三种基础范围。
        assertEquals("all", ScriptExtend.signOutputsOf(TransactionSignature.SIGHASH_ALL));
        assertEquals("none", ScriptExtend.signOutputsOf(TransactionSignature.SIGHASH_NONE));
        assertEquals("single", ScriptExtend.signOutputsOf(TransactionSignature.SIGHASH_SINGLE));

        // 基础范围须与 ANYONECANPAY／FORKID 位解耦：低位才是基础类型
        assertEquals("single", ScriptExtend.signOutputsOf(TransactionSignature.SIGHASH_SINGLE
                | TransactionSignature.SIGHASH_ANYONECANPAY | TransactionSignature.SIGHASH_FORKID));
        assertEquals("none", ScriptExtend.signOutputsOf(TransactionSignature.SIGHASH_NONE
                | TransactionSignature.SIGHASH_ANYONECANPAY));
    }

    @Test
    void estimatesSpendSizeForCommonAndOrdinalScripts() {
        Script address = new P2PKH().lock(KEY_ADDRESS.toBase58());
        Script rawKey = rawKeyScript();
        Script multiSig = multiSigScript();
        Script p2sh = p2shScript(multiSig);
        Script ordinal = OrdScriptBuilder.buildInscription(KEY_ADDRESS, new byte[] {1}, "text/plain", null);

        assertEquals(1234L, ScriptExtend.of(address.toUint8Array(), 1234L).getCreationTimeSeconds());

        int sigSize = ScriptExtend.SIG_SIZE;
        assertEquals(sigSize + 33, ScriptExtend.getNumberOfBytesRequiredToSpend(address, 33, null));
        assertEquals(sigSize + 65, ScriptExtend.getNumberOfBytesRequiredToSpend(address, 0, null));
        assertEquals(sigSize, ScriptExtend.getNumberOfBytesRequiredToSpend(rawKey, 33, null));
        assertEquals(sigSize + 1, ScriptExtend.getNumberOfBytesRequiredToSpend(multiSig, 33, null));
        assertThrows(IllegalArgumentException.class,
                () -> ScriptExtend.getNumberOfBytesRequiredToSpend(p2sh, 33, null));
        assertEquals(sigSize + multiSig.toUint8Array().length,
                ScriptExtend.getNumberOfBytesRequiredToSpend(p2sh, 33, multiSig));
        assertEquals(sigSize + 33, ScriptExtend.getNumberOfBytesRequiredToSpend(ordinal, 33, null));
        assertEquals(sigSize + 65, ScriptExtend.getNumberOfBytesRequiredToSpend(ordinal, 0, null));
        assertThrows(IllegalStateException.class,
                () -> ScriptExtend.getNumberOfBytesRequiredToSpend(
                        new Script().writeOpCode(OP.OP_1), 33, null));
    }

    @Test
    void insertsSignaturesAtTheCorrectPositionForEachScriptType() {
        Script address = new P2PKH().lock(KEY_ADDRESS.toBase58());
        Script rawKey = rawKeyScript();
        Script multiSig = multiSigScript();
        Script p2sh = p2shScript(multiSig);
        Script ordinal = OrdScriptBuilder.buildInscription(KEY_ADDRESS, new byte[] {1}, "text/plain", null);

        Script addressInput = new Script().writeOpCode(OP.OP_0).writeBin(toBoxed(KEY.getPubKey()));
        Script signedAddress = ScriptExtend.getScriptSigWithSignature(address, addressInput, SIGNATURE, 0);
        assertArrayEquals(SIGNATURE, pushData(signedAddress, 0));
        assertArrayEquals(KEY.getPubKey(), pushData(signedAddress, 1));

        Script multiInput = new Script().writeOpCode(OP.OP_0).writeOpCode(OP.OP_0);
        Script signedMulti = ScriptExtend.getScriptSigWithSignature(multiSig, multiInput, SIGNATURE, 0);
        assertEquals(2, signedMulti.getChunks().size());
        assertArrayEquals(SIGNATURE, pushData(signedMulti, 1));

        Script p2shInput = new Script().writeOpCode(OP.OP_0).writeOpCode(OP.OP_0)
                .writeBin(toBoxed(multiSig.toUint8Array()));
        Script signedP2sh = ScriptExtend.getScriptSigWithSignature(p2sh, p2shInput, SIGNATURE, 0);
        assertArrayEquals(SIGNATURE, pushData(signedP2sh, 1));
        assertArrayEquals(multiSig.toUint8Array(), pushData(signedP2sh, 2));

        Script signedRaw = ScriptExtend.getScriptSigWithSignature(rawKey,
                new Script().writeOpCode(OP.OP_0), SIGNATURE, 0);
        assertArrayEquals(SIGNATURE, pushData(signedRaw, 0));

        Script signedOrdinal = ScriptExtend.getScriptSigWithSignature(ordinal, addressInput, SIGNATURE, 0);
        assertArrayEquals(SIGNATURE, pushData(signedOrdinal, 0));

        Script signedUnknown = ScriptExtend.getScriptSigWithSignature(new Script().writeOpCode(OP.OP_1),
                new Script().writeOpCode(OP.OP_0), SIGNATURE, 0);
        assertArrayEquals(SIGNATURE, pushData(signedUnknown, 0));
    }

    /** 裸公钥脚本：&lt;公钥&gt; OP_CHECKSIG。 */
    private static Script rawKeyScript() {
        return new Script().writeBin(toBoxed(KEY.getPubKey())).writeOpCode(OP.OP_CHECKSIG);
    }

    /** 1-of-1 多签脚本：OP_1 &lt;公钥&gt; OP_1 OP_CHECKMULTISIG。 */
    private static Script multiSigScript() {
        return new Script().writeOpCode(OP.OP_1).writeBin(toBoxed(KEY.getPubKey()))
                .writeOpCode(OP.OP_1).writeOpCode(OP.OP_CHECKMULTISIG);
    }

    /** P2SH 锁定脚本：OP_HASH160 &lt;hash160( redeemScript )&gt; OP_EQUAL。 */
    private static Script p2shScript(Script redeemScript) {
        return new Script().writeOpCode(OP.OP_HASH160)
                .writeBin(Hash.hash160(redeemScript.toUint8Array(), null))
                .writeOpCode(OP.OP_EQUAL);
    }

    private static byte[] pushData(Script script, int index) {
        return ScriptHelper.getPushData(script.getChunks().get(index));
    }

    private static List<Integer> toBoxed(byte[] data) {
        List<Integer> out = new ArrayList<>(data.length);
        for (byte b : data) {
            out.add(b & 0xFF);
        }
        return out;
    }

    // ---- §94：覆盖 ScriptExtend 的脚本形态识别谓词（全部为 public API） ----
    // 一律用 **Script.fromBinary(原始字节)** 构造：`new Script(List<ScriptChunk>)` 会**重新序列化**，
    // 精确 chunk 形态未必保真（§179 的教训），而这些谓词判定的正是 chunk 形态。

    private static byte[] p2pkhBytes() {
        byte[] h = new byte[20];
        java.util.Arrays.fill(h, (byte) 7);
        byte[] out = new byte[25];
        out[0] = 0x76; out[1] = (byte) 0xa9; out[2] = 0x14;   // OP_DUP OP_HASH160 <push 20>
        System.arraycopy(h, 0, out, 3, 20);
        out[23] = (byte) 0x88; out[24] = (byte) 0xac;          // OP_EQUALVERIFY OP_CHECKSIG
        return out;
    }

    private static byte[] p2shBytes() {
        byte[] h = new byte[20];
        java.util.Arrays.fill(h, (byte) 9);
        byte[] out = new byte[23];
        out[0] = (byte) 0xa9; out[1] = 0x14;                   // OP_HASH160 <push 20>
        System.arraycopy(h, 0, out, 2, 20);
        out[22] = (byte) 0x87;                                 // OP_EQUAL
        return out;
    }

    private static byte[] multisigBytes() {
        byte[] pk = new byte[33];
        pk[0] = 0x02;
        java.util.Arrays.fill(pk, 1, 33, (byte) 3);
        byte[] out = new byte[1 + 34 + 34 + 1 + 1];
        int i = 0;
        out[i++] = 0x52;                                       // OP_2（需 2 个签名）
        out[i++] = 0x21; System.arraycopy(pk, 0, out, i, 33); i += 33;
        out[i++] = 0x21; System.arraycopy(pk, 0, out, i, 33); i += 33;
        out[i++] = 0x52;                                       // OP_2（共 2 把公钥）
        out[i] = (byte) 0xae;                                  // OP_CHECKMULTISIG
        return out;
    }

    @Test
    void recognizesEveryStandardScriptShape() {
        Script p2pkh = Script.fromBinary(p2pkhBytes());
        Script p2sh = Script.fromBinary(p2shBytes());
        Script multisig = Script.fromBinary(multisigBytes());

        // 前提确认：这些谓词按**段数**先行短路，故送入的脚本必须是 5 段，
        // 否则各 `&&` 的右操作数根本不会被求值（本断言同时把该前提固定在测试里）。
        assertEquals(5, p2pkh.getChunks().size(), "P2PKH 原始字节应解析为 5 段");
        assertTrue(ScriptExtend.isSentToAddress(p2pkh), "应识别 P2PKH");
        assertFalse(ScriptExtend.isSentToAddress(p2sh), "P2SH 不应被判为 P2PKH");

        assertTrue(ScriptExtend.isPayToScriptHash(p2sh), "应识别 P2SH");
        assertFalse(ScriptExtend.isPayToScriptHash(p2pkh), "P2PKH 不应被判为 P2SH");

        assertTrue(ScriptExtend.isSentToMultiSig(multisig), "应识别多签脚本");
        assertFalse(ScriptExtend.isSentToMultiSig(p2pkh), "P2PKH 不应被判为多签");

        assertEquals(2, ScriptExtend.getNumberOfSignaturesRequiredToSpend(multisig),
                "OP_2 起步的多签应要求 2 个签名");
        assertThrows(IllegalArgumentException.class,
                () -> ScriptExtend.getNumberOfSignaturesRequiredToSpend(p2pkh),
                "非多签脚本应被拒绝，而非返回无意义的数字");
    }


    @Test
    void rejectsMultiSigShapedScriptsThatFailItsThreeInternalChecks() {
        // 覆盖 §94 所列 isSentToMultiSig 的**三道内部校验**（:108—116）——
        // 它们只判"像不像多签"，故**形状对但内容不合规**的脚本必须被判否，
        // 否则后续按"多签"处理会得到错误的签名数或错误的位序。
        byte[] pk33 = new byte[33];
        pk33[0] = 0x02;
        java.util.Arrays.fill(pk33, 1, 33, (byte) 3);

        // ① 首段（应签名数）越出 OP_1..OP_16 —— 用 OP_0
        byte[] badRequired = new byte[1 + 34 + 34 + 1 + 1];
        int i = 0;
        badRequired[i++] = 0x00;                                   // ← OP_0，不在 OP_1..OP_16
        badRequired[i++] = 0x21; System.arraycopy(pk33, 0, badRequired, i, 33); i += 33;
        badRequired[i++] = 0x21; System.arraycopy(pk33, 0, badRequired, i, 33); i += 33;
        badRequired[i++] = 0x52; badRequired[i] = (byte) 0xae;
        assertFalse(ScriptExtend.isSentToMultiSig(Script.fromBinary(badRequired)),
                "应签名数不在 OP_1..OP_16 内时应判否");

        // ② 倒数第二段（公钥数）越出 OP_1..OP_16
        byte[] badTotal = badRequired.clone();
        badTotal[0] = 0x52;                                        // 首段改回合法
        badTotal[2 + 34 + 34] = 0x00;                              // ← 公钥数位置改 OP_0
        assertFalse(ScriptExtend.isSentToMultiSig(Script.fromBinary(badTotal)),
                "公钥数不在 OP_1..OP_16 内时应判否");

        // ③ 内部一段不是标准公钥（用 20 字节而非 33/65）
        byte[] h20 = new byte[20];
        java.util.Arrays.fill(h20, (byte) 5);
        byte[] badPubKey = new byte[1 + 1 + 20 + 1 + 1];
        badPubKey[0] = 0x52; badPubKey[1] = 0x14;
        System.arraycopy(h20, 0, badPubKey, 2, 20);
        badPubKey[22] = 0x52; badPubKey[23] = (byte) 0xae;
        assertFalse(ScriptExtend.isSentToMultiSig(Script.fromBinary(badPubKey)),
                "内部公钥长度既非 33 也非 65 时应判否");
    }


    @Test
    void replacesTheSignatureInPlaceAndPicksThePushOpCodeByLength() {
        // 覆盖 §94 所列最后两块：updateScriptWithSignature 的**下标守卫**（:142—144）与**插入分支**（:152—155），
        // 以及经它触达的 pushDataOpCode 的**四档长度选择**（:222—231）。
        // 脚本用原始字节构造：OP_0 | 占位签名(push 1 字节) | 后缀(redeemScript)
        byte[] redeem = p2shBytes();
        byte[] raw = new byte[1 + 2 + 1 + redeem.length];
        int i = 0;
        raw[i++] = 0x00;                       // 前置：OP_0
        raw[i++] = 0x01; raw[i++] = (byte) 0xAA;   // 待替换的签名位（1 字节占位）
        raw[i++] = (byte) redeem.length;       // 后缀：把 redeemScript 推入
        System.arraycopy(redeem, 0, raw, i, redeem.length);
        Script scriptSig = Script.fromBinary(raw);

        // ① 下标越界：负值与"可用位数的上界"各一次（上界取 size-prefix-suffix，验证其为排他）
        int usable = scriptSig.getChunks().size() - 1 - 1;
        assertThrows(IllegalArgumentException.class,
                () -> ScriptExtend.updateScriptWithSignature(scriptSig, new byte[71], -1, 1, 1),
                "负下标应被拒绝");
        assertThrows(IllegalArgumentException.class,
                () -> ScriptExtend.updateScriptWithSignature(scriptSig, new byte[71], usable, 1, 1),
                "越界下标应被拒绝（上界为排他）");

        // ② 插入分支 + 四档推送长度：分别用 10／76／256／65536 字节的签名
        assertPushOpCode(scriptSig, 10, 10);            // < OP_PUSHDATA1 → 直接用长度本身
        assertPushOpCode(scriptSig, 76, 0x4c);          // OP_PUSHDATA1
        assertPushOpCode(scriptSig, 256, 0x4d);         // OP_PUSHDATA2
        assertPushOpCode(scriptSig, 65536, 0x4e);       // OP_PUSHDATA4
    }

    /** 用给定长度的"签名"替换下标 0，并断言写回的 chunk 使用了期望的推送操作码。 */
    private static void assertPushOpCode(Script scriptSig, int sigLen, int expectedOp) {
        Script updated = ScriptExtend.updateScriptWithSignature(scriptSig, new byte[sigLen], 0, 1, 1);
        int op = updated.getChunks().get(1).op;   // 下标 0 是前置 OP_0，签名位紧随其后
        assertEquals(expectedOp, op,
                sigLen + " 字节的签名应使用操作码 0x" + Integer.toHexString(expectedOp)
                        + "，实际 0x" + Integer.toHexString(op));
    }


    @Test
    void rejectsScriptsThatHaveTheRightShapeButOneWrongOpCodeOrLength() {
        // §98 的结论：各谓词是多条件合取，**只覆盖两端是不够的**——
        // 必须补上"**段数正确、但某一操作码或长度不符**"这一中间情形，否则各 `&&` 的短路右侧永远不走。
        // 本用例逐个谓词给出该情形，与上一用例（真侧）互为对照。
        byte[] h20 = new byte[20];
        java.util.Arrays.fill(h20, (byte) 7);

        // ① isSentToAddress：段数为 5，但第 2 段长度为 19（≠20）
        byte[] shortHash = new byte[24];
        shortHash[0] = 0x76; shortHash[1] = (byte) 0xa9; shortHash[2] = 0x13;
        System.arraycopy(h20, 0, shortHash, 3, 19);
        shortHash[22] = (byte) 0x88; shortHash[23] = (byte) 0xac;
        assertFalse(ScriptExtend.isSentToAddress(Script.fromBinary(shortHash)),
                "第 2 段长度为 19 时不应判为 P2PKH");

        // ② isSentToAddress：段数为 5、长度对，但第 3 段是 OP_EQUAL 而非 OP_EQUALVERIFY
        byte[] wrongOp = new byte[25];
        System.arraycopy(p2pkhBytes(), 0, wrongOp, 0, 25);
        wrongOp[23] = (byte) 0x87;                      // OP_EQUAL（应为 OP_EQUALVERIFY）
        assertFalse(ScriptExtend.isSentToAddress(Script.fromBinary(wrongOp)),
                "第 3 段不是 OP_EQUALVERIFY 时不应判为 P2PKH");

        // ③ isPayToScriptHash：段数为 3，但第 1 段长度为 19
        byte[] shortHash160 = new byte[22];
        shortHash160[0] = (byte) 0xa9; shortHash160[1] = 0x13;
        System.arraycopy(h20, 0, shortHash160, 2, 19);
        shortHash160[21] = (byte) 0x87;
        assertFalse(ScriptExtend.isPayToScriptHash(Script.fromBinary(shortHash160)),
                "第 1 段长度为 19 时不应判为 P2SH");

        // ④ isPayToScriptHash：段数与长度都对，但第 2 段不是 OP_EQUAL
        byte[] wrongTail = p2shBytes();
        wrongTail[wrongTail.length - 1] = (byte) 0xac;  // OP_CHECKSIG（应为 OP_EQUAL）
        assertFalse(ScriptExtend.isPayToScriptHash(Script.fromBinary(wrongTail)),
                "第 2 段不是 OP_EQUAL 时不应判为 P2SH");

        // ⑤ isSentToRawPubKey：段数为 2，但"公钥"只有 32 字节（既非 33 也非 65）
        byte[] shortPubKey = new byte[1 + 32 + 1];
        shortPubKey[0] = 0x20;                          // push 32 字节
        System.arraycopy(h20, 0, shortPubKey, 1, 20);
        shortPubKey[33] = (byte) 0xac;                  // OP_CHECKSIG
        assertFalse(ScriptExtend.isSentToRawPubKey(Script.fromBinary(shortPubKey)),
                "公钥长度既非 33 也非 65 时不应判为 P2PK");
    }


    @Test
    void walksEveryConditionOfEachPredicateIndividually() {
        // §98 的方法：对多条件合取，**每个 `&&` 都要有"前面条件为真、本条为假"的样本**，
        // 否则该条的短路右侧永不被求值。上一批覆盖了"长度／尾段不符"，本批覆盖**首段、次段与末段**。
        byte[] h20 = new byte[20];
        java.util.Arrays.fill(h20, (byte) 7);

        // ① isSentToAddress：段数 5、其余皆对，但**第 0 段不是 OP_DUP**
        byte[] bad0 = p2pkhBytes(); bad0[0] = 0x51;                 // OP_1
        assertFalse(ScriptExtend.isSentToAddress(Script.fromBinary(bad0)), "第 0 段不符应判否");

        // ② 同前，但**第 1 段不是 OP_HASH160**
        byte[] bad1 = p2pkhBytes(); bad1[1] = 0x51;
        assertFalse(ScriptExtend.isSentToAddress(Script.fromBinary(bad1)), "第 1 段不符应判否");

        // ③ 同前，但**第 4 段不是 OP_CHECKSIG**（前四条皆真、仅末条假）
        byte[] bad4 = p2pkhBytes(); bad4[24] = 0x51;
        assertFalse(ScriptExtend.isSentToAddress(Script.fromBinary(bad4)), "第 4 段不符应判否");

        // ④ isPayToScriptHash：段数 3、第 1 段长度对，但**第 0 段不是 OP_HASH160**
        byte[] badHash = p2shBytes(); badHash[0] = 0x51;
        assertFalse(ScriptExtend.isPayToScriptHash(Script.fromBinary(badHash)), "第 0 段不符应判否");

        // ⑤ isSentToRawPubKey：段数 2、长度合规，但**第 1 段不是 OP_CHECKSIG**
        byte[] pk33 = new byte[33]; pk33[0] = 0x02; java.util.Arrays.fill(pk33, 1, 33, (byte) 3);
        byte[] badTail = new byte[1 + 33 + 1];
        badTail[0] = 0x21; System.arraycopy(pk33, 0, badTail, 1, 33); badTail[34] = 0x51;
        assertFalse(ScriptExtend.isSentToRawPubKey(Script.fromBinary(badTail)), "第 1 段不符应判否");

        // ⑥ isStandardPubKey 的两侧：33 与 65 字节**各自为真**（经 P2PK 判定间接走到）
        byte[] pk65 = new byte[65]; pk65[0] = 0x04;
        java.util.Arrays.fill(pk65, 1, 65, (byte) 4);
        byte[] ok33 = new byte[1 + 33 + 1];
        ok33[0] = 0x21; System.arraycopy(pk33, 0, ok33, 1, 33); ok33[34] = (byte) 0xac;
        byte[] ok65 = new byte[1 + 65 + 1];
        ok65[0] = 0x41; System.arraycopy(pk65, 0, ok65, 1, 65); ok65[66] = (byte) 0xac;
        assertTrue(ScriptExtend.isSentToRawPubKey(Script.fromBinary(ok33)), "33 字节公钥应被接受");
        assertTrue(ScriptExtend.isSentToRawPubKey(Script.fromBinary(ok65)), "65 字节公钥应被接受");

        // ⑦ isSentToMultiSig：首段越**上界**（> OP_16）——与 §255 的 OP_0（越下界）合成 `||` 的两侧
        byte[] overMax = new byte[1 + 34 + 34 + 1 + 1];
        int i = 0;
        overMax[i++] = 0x61;                                        // OP_17（超出 OP_16）
        overMax[i++] = 0x21; System.arraycopy(pk33, 0, overMax, i, 33); i += 33;
        overMax[i++] = 0x21; System.arraycopy(pk33, 0, overMax, i, 33); i += 33;
        overMax[i++] = 0x52; overMax[i] = (byte) 0xae;
        assertFalse(ScriptExtend.isSentToMultiSig(Script.fromBinary(overMax)),
                "应签名数超出 OP_16 时应判否");
    }


    @Test
    void replacesASignatureThatIsNotTheFirstAvailableSlot() {
        // 覆盖 updateScriptWithSignature 插入循环的**假侧**（:152）与随之的 else 分支（:155）：
        // 上一批用的 index=0 使 `i == prefix+index` **首次迭代即成立**，
        // 故"**先原样拷贝若干段、再在目标位替换**"这条路径从未走到。
        // 本批用 index=1（可用位有 2 个），迫使循环先在 i=1 走 else、再到 i=2 才替换。
        byte[] redeem = p2shBytes();
        // 原始脚本：OP_0 | 占位签名A | 占位签名B | redeemScript
        byte[] raw = new byte[1 + 2 + 2 + 1 + redeem.length];
        int i = 0;
        raw[i++] = 0x00;
        raw[i++] = 0x01; raw[i++] = (byte) 0xAA;
        raw[i++] = 0x01; raw[i++] = (byte) 0xBB;
        raw[i++] = (byte) redeem.length;
        System.arraycopy(redeem, 0, raw, i, redeem.length);
        Script scriptSig = Script.fromBinary(raw);
        assertEquals(4, scriptSig.getChunks().size(), "构造的脚本应为 4 段");

        byte[] newSig = new byte[71];
        java.util.Arrays.fill(newSig, (byte) 0x5A);
        Script updated = ScriptExtend.updateScriptWithSignature(scriptSig, newSig, 1, 1, 1);

        // 前置与后缀原样保留；**第 1 个可用位（下标 1）保持原样**，第 2 个（下标 2）被替换
        assertEquals(0x00, updated.getChunks().get(0).op, "前置 OP_0 应原样保留");
        assertEquals(0xAA, updated.getChunks().get(1).data.get(0).intValue(), "非目标的占位签名应原样保留");
        assertEquals(0x5A, updated.getChunks().get(2).data.get(0).intValue(), "目标位应被写入新签名");
        assertEquals(newSig.length, updated.getChunks().get(2).data.size(), "写入长度应与新签名一致");
        assertEquals(redeem.length, updated.getChunks().get(3).data.size(), "后缀 redeemScript 应原样保留");
    }


    @Test
    void walksTheDataNullLegAndTheUpperBoundOfEachRangeCheck() {
        // 覆盖 §98 方法下仍缺的两类"另一侧"：
        //   ① `:77`／`:87` 的 `chunks.get(n).data != null` —— **该合取的第一腿**（数据为 null 的推送段）；
        //   ② `:111` 的 `op > OP.OP_16` —— 公钥数**越上界**（§255 只用了 OP_0 越下界）。
        byte[] h20 = new byte[20];
        java.util.Arrays.fill(h20, (byte) 7);

        // ① isSentToAddress：段数 5、前后操作码皆对，但**第 2 段是无数据的操作码**（OP_1）
        //    注意这与"长度为 19"不同：那里 data 非 null、只是长度不符；这里 data 本身为 null。
        byte[] noData = {0x76, (byte) 0xa9, 0x51, (byte) 0x88, (byte) 0xac};
        assertFalse(ScriptExtend.isSentToAddress(Script.fromBinary(noData)),
                "第 2 段无数据（data 为 null）时不应判为 P2PKH");

        // ② isPayToScriptHash：段数 3、第 0 与第 2 段皆对，但**第 1 段是无数据的操作码**
        byte[] noDataP2sh = {(byte) 0xa9, 0x51, (byte) 0x87};
        assertFalse(ScriptExtend.isPayToScriptHash(Script.fromBinary(noDataP2sh)),
                "第 1 段无数据（data 为 null）时不应判为 P2SH");

        // ③ isSentToMultiSig：公钥数（倒数第二段）**越上界**——用 OP_17
        byte[] pk33 = new byte[33];
        pk33[0] = 0x02;
        java.util.Arrays.fill(pk33, 1, 33, (byte) 3);
        byte[] overTotal = new byte[1 + 34 + 34 + 1 + 1];
        int i = 0;
        overTotal[i++] = 0x52;                                     // OP_2（应签名数，合法）
        overTotal[i++] = 0x21; System.arraycopy(pk33, 0, overTotal, i, 33); i += 33;
        overTotal[i++] = 0x21; System.arraycopy(pk33, 0, overTotal, i, 33); i += 33;
        overTotal[i++] = 0x61;                                     // OP_17（公钥数，越上界）
        overTotal[i] = (byte) 0xae;
        assertFalse(ScriptExtend.isSentToMultiSig(Script.fromBinary(overTotal)),
                "公钥数超出 OP_16 时应判否");
    }


    @Test
    void walksTheDataNullLegOfTheStandardPubKeyCheck() {
        // 覆盖 `:217`（`isStandardPubKey`）的 `data != null` 这一腿的**假侧**。
        // 经 `isSentToRawPubKey` 触达：它是 `chunks.size() == 2 && isStandardPubKey(chunks.get(0).data) && …`，
        // 故只要第一段是**无数据的纯操作码**（如 OP_1），就能把 `null` 送进 `isStandardPubKey`。
        // 注：这与"公钥长度不是 33/65"不同——那里 data 非 null、只是长度不符（§261 已覆盖）。
        byte[] noDataThenChecksig = {0x51, (byte) 0xac};   // OP_1 OP_CHECKSIG
        assertFalse(ScriptExtend.isSentToRawPubKey(Script.fromBinary(noDataThenChecksig)),
                "首段无数据（data 为 null）时不应判为 P2PK");
    }

}
