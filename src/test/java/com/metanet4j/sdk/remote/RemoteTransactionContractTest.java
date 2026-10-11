package com.metanet4j.sdk.remote;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.hutool.core.codec.Base64;
import com.metanet4j.sdk.PublicKey;
import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.bap.BapBase;
import com.metanet4j.sdk.bap.SigningKeyProvider;
import com.metanet4j.sdk.bap.RemoteBapBase;
import com.metanet4j.sdk.context.PreSignHashContext;
import com.metanet4j.sdk.exception.SignErrorException;
import com.metanet4j.sdk.ordinals.OrdScriptBuilder;
import com.metanet4j.sdk.signers.ExtendForSignTransaction;
import com.metanet4j.sdk.transcation.LockingScriptBuilder;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.base.bsv.BsvHashBytes;
import com.metanet4j.base.bsv.BsvAmount;
import com.metanet4j.base.bsv.BsvUtxo;
import com.metanet4j.bsv.transaction.TransactionInput;
import com.metanet4j.bsv.transaction.TransactionOutput;
import com.metanet4j.bsv.transaction.Transaction;
import com.metanet4j.bsv.primitives.TransactionSignature;
import com.metanet4j.bsv.script.templates.P2PKH;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.script.OP;
import com.metanet4j.sdk.PrivateKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RemoteTransactionContractTest {

    private final BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
    private final RemoteBapBase remoteBap = new RemoteBapBase(bap.getRootAddress(),
            bap.getPreviouAddress(), bap.getCurrentAddress(), bap.getPayAccountAddress(),
            bap.getOrdAddress(), bap.getAppName());
    private final AddressEnhance paymentAddress = bap.getPayAccountAddress();
    private final PrivateKey paymentKey = bap.getPayAccountKey();
    private final SigningKeyProvider keyProvider = new BapBase.BapProviderKeyBag(bap);

    @AfterEach
    void clearSignedHashes() {
        PreSignHashUtils.clear();
    }

    @Test
    void requestsAndAppliesRemotePaymentSignatureInMemory() {
        RemoteTransactionBuilder builder = new RemoteTransactionBuilder(remoteBap, keyProvider);
        builder.addInput(paymentUtxo(1))
                .addOutput(BsvAmount.valueOf(777), paymentAddress)
                .changeAddress(paymentAddress);

        List<PreSignHashContext> requests = builder.getTxInputSignHash(false, remoteBap);
        assertEquals(1, requests.size());
        PreSignHashContext request = requests.get(0);
        assertEquals(RemoteSignType.PAYMENT, request.getRemoteSignType());
        assertEquals(paymentAddress.toBase58(), request.getSignAddress());
        assertEquals(builder.getFee(), request.getFee());
        sign(request, paymentKey);

        Transaction signed = builder.completeAndSignTx(keyProvider, true);
        assertTrue(unlockingScriptBytes(signed, 0).length > 0);
        assertUnlockingScriptValid(signed, 0);
    }

    @Test
    void routesPaymentAndOrdinalHashesToTheirRespectiveAddresses() {
        RemoteTransactionBuilder builder = new RemoteTransactionBuilder(remoteBap, keyProvider);
        builder.addInput(paymentUtxo(2));
        Script inscription = OrdScriptBuilder.buildInscription(bap.getOrdAddress(),
                "prior".getBytes(StandardCharsets.US_ASCII), "text/plain", null);
        Transaction funding = new Transaction();
        funding.outputs.add(new TransactionOutput(inscription, 1d));
        builder.addInput(funding.outputs.get(0));
        builder.addOutput(BsvAmount.valueOf(777), paymentAddress);

        List<PreSignHashContext> requests = builder.getTxInputSignHash(false, remoteBap);

        assertEquals(2, requests.size());
        assertEquals(RemoteSignType.PAYMENT, requests.get(0).getRemoteSignType());
        assertEquals(bap.getPayAccountAddress().toBase58(), requests.get(0).getSignAddress());
        assertEquals(RemoteSignType.ORD, requests.get(1).getRemoteSignType());
        assertEquals(bap.getOrdAddress().toBase58(), requests.get(1).getSignAddress());
    }

    @Test
    void remoteBuilderSkipsLocalSigningPreparation() {
        // 覆盖 RemoteTransactionBuilder 对两参 prepareInputsForSigning 的 **no-op 覆盖**（§76）：
        // 该覆盖用空实现表达「远程签名由 RemoteTransactionSigner 回填」的设计意图，
        // 故其契约是「可被调用、且不触碰交易」。本测试类与本类同包，可直接访问 protected 成员。
        RemoteTransactionBuilder builder = new RemoteTransactionBuilder(remoteBap, keyProvider);
        builder.addInput(paymentUtxo(1));
        Transaction target = builder.getTargetTransaction();
        List<Integer> before = target.toBinary();

        assertDoesNotThrow(() -> builder.prepareInputsForSigning(target, keyProvider),
                "远程构建器的准备步骤应为 no-op，不得抛错");

        assertEquals(before, target.toBinary(), "no-op 覆盖不应改动交易");
    }

    @Test
    void skipsInputsWhoseSourceTransactionHasNoOutputs() {
        // 覆盖 RemoteTransactionBuilder.sourceOutputOf 的三个条件
        // （`instanceof Transaction` && `outputs != null` && `!outputs.isEmpty()`）：
        // 该私有方法经 getTxInputSignHash 间接触达——**输出为空时它返回 null，调用方随即跳过该输入**。
        RemoteTransactionBuilder builder = new RemoteTransactionBuilder(remoteBap, keyProvider);

        // 条件三的假侧：sourceTransaction 是 Transaction，但 outputs 为空
        Transaction withEmptyOutputs = new Transaction();
        TransactionInput emptySource = new TransactionInput(
                new BsvHashBytes(new byte[32]).toString(), 0, new Script());
        emptySource.sourceTransaction = withEmptyOutputs;   // 显式置为「无输出的来源交易」
        // addInput 只接受 TransactionOutput／utxo，故此处直接把自造输入放进目标交易
        builder.getTargetTransaction().inputs.add(emptySource);
        // 对照组：正常带输出的来源交易（走条件全真的一侧）
        builder.addInput(paymentUtxo(1));
        builder.addOutput(BsvAmount.valueOf(1), paymentAddress);

        List<PreSignHashContext> requests = builder.getTxInputSignHash(false, remoteBap);

        assertEquals(1, requests.size(),
                "无输出的来源交易应被跳过，仅带输出的那个输入产出待签哈希");
    }

    @Test
    void treatsAnOutputWithoutLockingScriptAsAnEmptyScript() {
        // 覆盖 RemoteTransactionBuilder 中 `sourceOutput.lockingScript == null ? new Script() : ...`
        // 的空值兜底侧（与 §148 处理 OrdTransactionBuilder 的同名模式一致）。
        RemoteTransactionBuilder builder = new RemoteTransactionBuilder(remoteBap, keyProvider);
        builder.addInput(new TransactionOutput(null, 1d));
        builder.addOutput(BsvAmount.valueOf(1), paymentAddress);

        List<PreSignHashContext> requests =
                assertDoesNotThrow(() -> builder.getTxInputSignHash(false, remoteBap),
                        "锁定脚本为空的连接输出应按空脚本处理，而不应抛错");
        assertEquals(1, requests.size(), "该输入仍应产出一条待签哈希");
    }

    @Test
    void collectsOnlyUnsignedDataHashesAndSkipsDisconnectedInputs() {
        RemoteTransactionBuilder builder = new RemoteTransactionBuilder(remoteBap, keyProvider);
        PreSignHashContext pending = new PreSignHashContext();
        LockingScriptBuilder data = mock(LockingScriptBuilder.class);
        Script script = new Script().writeOpCode(OP.OP_RETURN).writeBin(java.util.List.of(1));
        when(data.getLockingScript()).thenReturn(script);
        when(data.getPreSignHashContext()).thenReturn(pending);

        builder.addDataOutput(data);
        assertSame(pending, builder.getAllBsmSignHash(true, false, remoteBap).get(0));
        when(data.isHaveSign()).thenReturn(true);
        builder.addDataOutput(data);
        assertEquals(1, builder.getAllBsmSignHash(true, false, remoteBap).size());
        assertArrayEquals(script.toUint8Array(), lockingScriptBytes(builder.getTargetTransaction(), 0));

        byte[] hash = new byte[32];
        hash[0] = 3;
        builder.getTargetTransaction().inputs.add(
                new TransactionInput(new BsvHashBytes(hash).toString(), 0, new Script()));
        assertTrue(builder.getTxInputSignHash(false, remoteBap).isEmpty());
    }

    @Test
    void signsTheRequestedSingleAnyoneCanPayHash() {
        RemoteTransactionBuilder builder = new RemoteTransactionBuilder(remoteBap, keyProvider);
        builder.addInput(paymentUtxo(4)).addOutput(BsvAmount.valueOf(777), paymentAddress);
        PreSignHashContext request = builder.getTxInputSignHash(
                TransactionSignature.SIGHASH_SINGLE, true, remoteBap).get(0);
        sign(request, paymentKey);
        ExtendForSignTransaction proposed = new ExtendForSignTransaction(
                builder.getTargetTransaction(), TransactionSignature.SIGHASH_SINGLE, true, true);

        assertTrue(new RemoteTransactionSigner().signInputs(proposed.partialTx, keyProvider,
                TransactionSignature.SIGHASH_SINGLE, true));
        assertUnlockingScriptValid(proposed.partialTx, 0);
    }

    @Test
    void remoteOrdinalBuilderReportsSignerFailure() {
        // 覆盖迁移（migration-rules §23）：旧用例断言「注入的签名器返回失败 → SignErrorException」。
        // 新架构的远程构建器**不在本地签名**（只准备预签名哈希，签名由外部服务返回后回填），
        // 因此不存在「本地签名失败」这一路径。改为断言该设计事实：解析不到密钥时也不产出解锁脚本。
        SigningKeyProvider noKeys = pubkeyHash -> null;
        RemoteOrdTransactionBuilder builder = new RemoteOrdTransactionBuilder(remoteBap, noKeys);
        builder.addInput(paymentUtxo(5));

        assertSame(builder, builder.signTx(noKeys, true));
        assertEquals(0, unlockingScriptBytes(builder.build(), 0).length,
                "远程流程不本地签名，解锁脚本应保持为空");
    }

    @Test
    void remoteOrdinalBuilderForwardsForkId() {
        // 旧用例断言「把 forkId 转发给注入的签名器」；新架构无可注入签名器且远程流程不本地签名。
        // 改为断言**预签名哈希**确实按所请求的范围计算：与独立调用 SigHashExtend 的结果一致。
        RemoteOrdTransactionBuilder builder = new RemoteOrdTransactionBuilder(remoteBap, keyProvider);
        builder.addInput(paymentUtxo(7));

        BsvHashBytes requested = builder.getTxInputSignHash(
                TransactionSignature.SIGHASH_SINGLE, true, remoteBap).get(0).getPreSignHash();
        Transaction tx = builder.getTargetTransaction();
        BsvHashBytes expected = com.metanet4j.sdk.script.SigHashExtend.hashForForkIdSignature(tx, 0,
                sourceOutput(tx, 0).lockingScript.toUint8Array(),
                BsvAmount.fromSdkValue(sourceOutput(tx, 0).satoshis),
                TransactionSignature.SIGHASH_SINGLE, true);

        assertEquals(expected, requested, "预签名哈希应按所请求的范围计算");
    }

    @Test
    void remoteOrdinalOutputCarriesTheRequestedContentAndPendingBsmHash() {
        RemoteOrdTransactionBuilder builder = new RemoteOrdTransactionBuilder(remoteBap, keyProvider);
        builder.addInput(paymentUtxo(6));
        byte[] content = "new inscription".getBytes(StandardCharsets.US_ASCII);

        builder.addOrdOutput(bap.getOrdAddress(), content, "text/plain", null,
                RemoteSignType.CURRENT);

        assertEquals(BsvAmount.SATOSHI,
                BsvAmount.fromSdkValue(builder.build().outputs.get(0).satoshis));
        assertArrayEquals(OrdScriptBuilder.buildInscription(bap.getOrdAddress(), content,
                "text/plain", null).toUint8Array(), lockingScriptBytes(builder.build(), 0));
        assertEquals(1, builder.getAllBsmSignHash(true, false, remoteBap).size());
        assertEquals(RemoteSignType.CURRENT,
                builder.getAllBsmSignHash(true, false, remoteBap).get(0).getRemoteSignType());
    }

    @Test
    void remoteSignerLeavesAnAlreadyDisconnectedInputUntouched() {
        Transaction tx = new Transaction();
        byte[] hash = new byte[32];
        hash[0] = 8;
        tx.inputs.add(new TransactionInput(new BsvHashBytes(hash).toString(), 0, new Script()));
        tx.outputs.add(new TransactionOutput(
                new P2PKH().lock(paymentAddress.toBase58()), 777d));
        ExtendForSignTransaction proposed = new ExtendForSignTransaction(
                tx, TransactionSignature.SIGHASH_ALL, true, false);

        assertTrue(new RemoteTransactionSigner().signInputs(proposed.partialTx, keyProvider,
                TransactionSignature.SIGHASH_ALL, false));
        assertEquals(0, unlockingScriptBytes(proposed.partialTx, 0).length);
    }

    @Test
    void fourArgumentRemoteOrdinalOutputUsesTheDefaultSigningIdentity() {
        RemoteOrdTransactionBuilder builder = new RemoteOrdTransactionBuilder(remoteBap, keyProvider);
        builder.addInput(paymentUtxo(9));
        byte[] content = "default".getBytes(StandardCharsets.US_ASCII);

        assertSame(builder, builder.addOrdOutput(bap.getOrdAddress(), content,
                "text/plain", null));

        assertArrayEquals(OrdScriptBuilder.buildInscription(bap.getOrdAddress(), content,
                        "text/plain", null).toUint8Array(),
                lockingScriptBytes(builder.build(), 0));
        assertEquals(RemoteSignType.CURRENT,
                builder.getAllBsmSignHash(true, false, remoteBap).get(0).getRemoteSignType());
    }

    private BsvUtxo paymentUtxo(int firstHashByte) {
        byte[] hash = new byte[32];
        hash[0] = (byte) firstHashByte;
        return new BsvUtxo(BsvHashBytes.of(hash), 0, 20_000L, 0, false,
                paymentAddress.toBase58(),
                new P2PKH().lock(paymentAddress.toBase58()).toUint8Array());
    }

    private void sign(PreSignHashContext request, PrivateKey key) {
        request.setSigB64(Base64.encode(toBytes(key.sign(request.getPreSignHash()).toDER())));
        request.setPublicKey(key.getPublicKey());
        PreSignHashUtils.setPreSignHashContext(request);
    }

    private static byte[] toBytes(java.util.List<?> values) {
        byte[] bytes = new byte[values.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ((Number) values.get(i)).intValue();
        }
        return bytes;
    }

    private static void assertUnlockingScriptValid(Transaction tx, int index) {
        byte[] signature = com.metanet4j.sdk.utils.ScriptHelper
                .getPushData(asScript(tx.inputs.get(index).unlockingScript).getChunks().get(0));
        assertNotNull(signature, "解锁脚本首项应为签名");
    }

    private static Script asScript(Object unlockingScript) {
        if (unlockingScript instanceof Script script) {
            return script;
        }
        if (unlockingScript instanceof byte[] bytes) {
            return Script.fromBinary(bytes);
        }
        return new Script();
    }

    private static int signatureHashType(Transaction tx, int index) {
        byte[] signature = com.metanet4j.sdk.utils.ScriptHelper.getPushData(
                asScript(tx.inputs.get(index).unlockingScript).getChunks().get(0));
        return signature[signature.length - 1] & 0xFF;
    }

    private static TransactionOutput sourceOutput(Transaction tx, int index) {
        if (tx.inputs.get(index).sourceTransaction instanceof Transaction source
                && source.outputs != null && !source.outputs.isEmpty()) {
            return source.outputs.get(0);
        }
        throw new IllegalStateException("输入 " + index + " 缺少连接输出");
    }

    private static byte[] unlockingScriptBytes(Transaction tx, int index) {
        return asScript(tx.inputs.get(index).unlockingScript).toUint8Array();
    }

    private static byte[] lockingScriptBytes(Transaction tx, int index) {
        return tx.outputs.get(index).lockingScript.toUint8Array();
    }
}
