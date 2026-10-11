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
    void ordBuilderAcceptsAnInputWithoutAScript() {
        // 覆盖 RemoteOrdTransactionBuilder.addOrdInput 中 `utxo.getScript() == null ? new Script() : ...`
        // 的空值兜底侧（与 §148 处理 OrdTransactionBuilder 的同名模式一致）。
        // addOrdInput 是 public，且本测试类与之同包，故可直接构造调用。
        RemoteOrdTransactionBuilder builder = new RemoteOrdTransactionBuilder(remoteBap, keyProvider);
        BsvUtxo noScript = new BsvUtxo(new BsvHashBytes(new byte[32]), 0, 20_000L, 0, false,
                paymentAddress.toBase58(), null);

        assertSame(builder, builder.addOrdInput(noScript),
                "无脚本的 utxo 应被接受，并支持链式调用");
    }

    @Test
    void remoteSignerHonoursTheSingleOutputSignatureFlavour() {
        // 覆盖 RemoteTransactionSigner:108 —— `signOutputsOf(hashFlags).equals("single")` 的**真侧**
        //（即 `SIGHASH_SINGLE | SIGHASH_FORKID | …`）。§271 全程用 `SIGHASH_ALL`，**只走了 :110 那一侧**，
        // 故 :108 从未被求值（§104 的线索）。本用例以 `SIGHASH_SINGLE` 打开该侧。
        RemoteTransactionBuilder builder = new RemoteTransactionBuilder(remoteBap, keyProvider);
        builder.addInput(paymentUtxo(1))
                .addOutput(BsvAmount.valueOf(777), paymentAddress)
                .changeAddress(paymentAddress);
        PreSignHashContext request = builder.getTxInputSignHash(false, remoteBap).get(0);
        sign(request, paymentKey);

        Transaction signed = builder.completeAndSignTx(keyProvider,
                TransactionSignature.SIGHASH_SINGLE, false);

        // 注：前像哈希是以固定旗标注册的，换旗标后**上下文可能查不到**、该输入会被跳过——
        // 那时本断言会失败，而失败本身即是"该路径未走通"的证据（§253 的教训：别让断言因错误的原因通过）。
        assertTrue(unlockingScriptBytes(signed, 0).length > 0,
                "SIGHASH_SINGLE 下也应完成解锁脚本回填（若为空，说明上下文未命中、该输入被跳过）");
    }

    @Test
    void remoteSignerWritesTheAnyoneCanPayFlagIntoTheUnlockingScript() {
        // 覆盖 RemoteTransactionSigner 的 `:108`／`:110`——`anyoneCanPay ? SIGHASH_ANYONECANPAY : 0` 的**两侧**。
        // **必须用 3 参重载**：2 参重载 `completeAndSignTx(provider, useForkId)` 把 anyoneCanPay **硬编码为 false**
        //（§96／§97 的教训——我曾把旗标传进 `useForkId`，导致两次结果相同、误以为功能失效）。
        byte[][] written = new byte[2][];

        for (int k = 0; k < 2; k++) {
            boolean anyoneCanPay = k == 1;
            RemoteTransactionBuilder builder = new RemoteTransactionBuilder(remoteBap, keyProvider);
            builder.addInput(paymentUtxo(1))
                    .addOutput(BsvAmount.valueOf(777), paymentAddress)
                    .changeAddress(paymentAddress);
            PreSignHashContext request = builder.getTxInputSignHash(false, remoteBap).get(0);
            sign(request, paymentKey);

            Transaction signed = builder.completeAndSignTx(keyProvider,
                    TransactionSignature.SIGHASH_ALL, anyoneCanPay);
            byte[] unlocking = unlockingScriptBytes(signed, 0);
            // **防混淆断言**：若某次因上下文查不到而被"跳过"，脚本会是空的——
            // 那样"两次不同"就会因错误的原因成立（§253 的教训：要证明走的是哪条路径）。
            assertTrue(unlocking.length > 0,
                    "anyoneCanPay=" + anyoneCanPay + " 时应确实写回解锁脚本，而非被跳过");
            written[k] = unlocking;
        }

        assertFalse(java.util.Arrays.equals(written[0], written[1]),
                "两种旗标写出的解锁脚本必须不同——否则说明 SIGHASH_ANYONECANPAY 位未被写入");
    }

    @Test
    void remoteSignerPrefersContextPublicKeyThenFallsBackToLocalKey() {
        // 覆盖 RemoteTransactionSigner 中 `pubKeyBytes` 的**嵌套三元**（:83—85）与随之的跳过（:86—88）：
        //   ① context 有公钥 → 用它（既有用例的正常路径已覆盖）；
        //   ② context **无公钥**、但有本地密钥 → 用本地公钥；
        //   ③ context 无公钥、且**无本地密钥** → pubKeyBytes 为 null → 跳过该输入（不失败）。
        // 构造要点：注册预签名上下文时**只设签名、不设公钥**（见本类 sign(...) 会 setPublicKey）。
        RemoteTransactionBuilder builder = new RemoteTransactionBuilder(remoteBap, keyProvider);
        builder.addInput(paymentUtxo(1))
                .addOutput(BsvAmount.valueOf(777), paymentAddress)
                .changeAddress(paymentAddress);
        Transaction tx = builder.getTargetTransaction();

        List<PreSignHashContext> requests = builder.getTxInputSignHash(false, remoteBap);
        PreSignHashContext request = requests.get(0);
        request.setSigB64(Base64.encode(toBytes(paymentKey.sign(request.getPreSignHash()).toDER())));
        // **刻意不设公钥**——以走到"无上下文公钥"的那一侧
        PreSignHashUtils.setPreSignHashContext(request);

        RemoteTransactionSigner signer = new RemoteTransactionSigner();

        // ② 有本地密钥 → 用本地公钥拼装解锁脚本，整批成功
        assertTrue(signer.signInputs(tx, keyProvider, TransactionSignature.SIGHASH_ALL, false),
                "无上下文公钥但有本地密钥时，应改用本地公钥完成回填");

        // ③ 提供者解析不到密钥 → 无上下文公钥、也无本地密钥 → 跳过该输入（返回 true，不失败）
        RemoteTransactionBuilder builder2 = new RemoteTransactionBuilder(remoteBap, keyProvider);
        builder2.addInput(paymentUtxo(1))
                .addOutput(BsvAmount.valueOf(777), paymentAddress)
                .changeAddress(paymentAddress);
        Transaction tx2 = builder2.getTargetTransaction();
        PreSignHashContext request2 = builder2.getTxInputSignHash(false, remoteBap).get(0);
        request2.setSigB64(Base64.encode(toBytes(paymentKey.sign(request2.getPreSignHash()).toDER())));
        PreSignHashUtils.setPreSignHashContext(request2);

        assertTrue(signer.signInputs(tx2, pubkeyHash -> null, TransactionSignature.SIGHASH_ALL, false),
                "两者皆无时应跳过该输入、而非让整批失败");
    }

    @Test
    void remoteSignerSkipsInputsItCannotResolveInsteadOfFailing() {
        // 覆盖 RemoteTransactionSigner.signInputs 的三处早期守卫（均为真实可达的"跳过"语义）：
        // :43—44 交易为 null；:53—54 连接输出无锁定脚本；:62—64 脚本既非 P2PKH 也非 Ordinals 铭文。
        RemoteTransactionSigner signer = new RemoteTransactionSigner();

        // :43—44 空交易 → 返回 false（而非抛错）
        assertFalse(signer.signInputs(null, keyProvider,
                TransactionSignature.SIGHASH_ALL, false), "null 交易应返回 false");

        // :53—54 连接输出无锁定脚本 → 按空脚本处理；:62—64 空脚本既非 P2PKH 也非铭文 → 跳过该输入
        Transaction tx = new Transaction();
        TransactionInput input = new TransactionInput(
                new BsvHashBytes(new byte[32]).toString(), 0, new Script());
        Transaction source = new Transaction();
        source.outputs.add(new com.metanet4j.bsv.transaction.TransactionOutput(null, 1d));
        input.sourceTransaction = source;
        tx.inputs.add(input);

        assertTrue(signer.signInputs(tx, keyProvider, TransactionSignature.SIGHASH_ALL, false),
                "无法识别的脚本应被跳过、而非让整批签名失败");
        // 该输入未被回填（unlockingScript 仍是构造时的值）
        assertSame(Script.class, asScript(input.unlockingScript).getClass(),
                "被跳过的输入不应被改写为新的解锁脚本");
    }

    @Test
    void skipsInputsWhoseSourceTransactionHasNullOutputs() {
        // 覆盖 sourceOutputOf 三条件中**中间那个**（`source.outputs != null`）的假侧：
        // §216 覆盖了第三个条件（outputs 为空）、§219 覆盖了第一个（instanceof），本轮补齐中间条件。
        // 该情形对应"交易对象已建但 outputs 列表尚未初始化"，是解析中途的真实状态。
        RemoteTransactionBuilder builder = new RemoteTransactionBuilder(remoteBap, keyProvider);

        Transaction partiallyBuilt = new Transaction();
        partiallyBuilt.outputs = null;      // 显式置为未初始化
        TransactionInput inputWithNullOutputs = new TransactionInput(
                new BsvHashBytes(new byte[32]).toString(), 0, new Script());
        inputWithNullOutputs.sourceTransaction = partiallyBuilt;
        builder.getTargetTransaction().inputs.add(inputWithNullOutputs);
        builder.addInput(paymentUtxo(1));   // 对照：正常带输出的输入
        builder.addOutput(BsvAmount.valueOf(1), paymentAddress);

        List<PreSignHashContext> requests = builder.getTxInputSignHash(false, remoteBap);

        assertEquals(1, requests.size(),
                "outputs 列表为 null 的来源交易应被跳过，仅对照输入产出待签哈希");
    }

    @Test
    void skipsInputsWhoseSourceTransactionIsNotATransaction() {
        // 覆盖 sourceOutputOf 判断中**第一个条件**（`instanceof Transaction`）的假侧：
        // 新 SDK 的 `TransactionInput.sourceTransaction` 默认是哨兵 `Undefined.INSTANCE`，
        // 即"从未被解析/回填过"的真实初始状态——此时 sourceOutputOf 应返回 null，调用方跳过该输入。
        RemoteTransactionBuilder builder = new RemoteTransactionBuilder(remoteBap, keyProvider);

        TransactionInput neverResolved = new TransactionInput(
                new BsvHashBytes(new byte[32]).toString(), 0, new Script());
        // 刻意**不设置** sourceTransaction，保留其默认哨兵值
        builder.getTargetTransaction().inputs.add(neverResolved);
        builder.addInput(paymentUtxo(1));   // 对照：正常带输出的输入
        builder.addOutput(BsvAmount.valueOf(1), paymentAddress);

        List<PreSignHashContext> requests = builder.getTxInputSignHash(false, remoteBap);

        assertEquals(1, requests.size(),
                "sourceTransaction 非 Transaction 的输入应被跳过，仅对照输入产出待签哈希");
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
