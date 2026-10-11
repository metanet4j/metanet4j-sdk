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

}
