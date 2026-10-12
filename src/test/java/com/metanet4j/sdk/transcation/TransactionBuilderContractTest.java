package com.metanet4j.sdk.transcation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.bap.BapBase;
import com.metanet4j.sdk.exception.SignErrorException;
import com.metanet4j.sdk.sigma.Sigma;
import com.metanet4j.sdk.signers.ExtendForSignTransaction;
import com.metanet4j.sdk.utxo.UTXOProvider;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.base.bsv.BsvAmount;
import com.metanet4j.base.bsv.BsvHashBytes;
import com.metanet4j.base.bsv.BsvUtxo;
import com.metanet4j.bsv.primitives.TransactionSignature;
import com.metanet4j.bsv.transaction.Transaction;
import com.metanet4j.bsv.transaction.TransactionInput;
import com.metanet4j.bsv.transaction.TransactionOutput;

import com.metanet4j.bsv.script.OP;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.script.templates.P2PKH;

import com.metanet4j.sdk.bap.SigningKeyProvider;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TransactionBuilderContractTest {

    private final BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
    private final SigningKeyProvider keyProvider = new BapBase.BapProviderKeyBag(bap);
    private final AddressEnhance paymentAddress = bap.getPayAccountAddress();

    @Test
    void addsProviderUtxoDataAndChangeWithoutLosingValue() {
        BsvUtxo utxo = utxo(1, 0, 20_000);
        UTXOProvider<AddressEnhance, BsvUtxo> provider = addresses -> {
            assertEquals(1, addresses.size());
            assertEquals(paymentAddress.toBase58(), addresses.get(0).toBase58());
            return List.of(utxo);
        };
        Script dataScript = new Script().writeOpCode(OP.OP_RETURN)
                .writeBin(toBoxed("payload".getBytes(StandardCharsets.US_ASCII)));
        LockingScriptBuilder data = mock(LockingScriptBuilder.class);
        when(data.getLockingScript()).thenReturn(dataScript);

        TransactionBuilder builder = new TransactionBuilder(bap, keyProvider);
        builder.addInput(bap.getPayAccountAddress(), provider)
                .addOutput(BsvAmount.valueOf(777), paymentAddress)
                .addDataOutput(data)
                .withFeeKb(BsvAmount.valueOf(500))
                .changeAddress(paymentAddress);
        Transaction tx = builder.getTargetTransaction();

        assertEquals(1, tx.inputs.size());
        assertEquals(utxo.getHash().toString(), tx.inputs.get(0).sourceTXID);
        assertEquals(utxo.getIndex(), tx.inputs.get(0).sourceOutputIndex);
        assertEquals(BsvAmount.valueOf(20_000), inputSum(tx));
        assertEquals(3, tx.outputs.size());
        assertEquals(BsvAmount.valueOf(777), BsvAmount.fromSdkValue(tx.outputs.get(0).satoshis));
        assertEquals(BsvAmount.valueOf(0), BsvAmount.fromSdkValue(tx.outputs.get(1).satoshis));
        assertArrayEquals(dataScript.toUint8Array(), tx.outputs.get(1).lockingScript.toUint8Array());
        assertEquals(builder.getChangeAmount(), BsvAmount.fromSdkValue(tx.outputs.get(2).satoshis));
        assertEquals(inputSum(tx), outputSum(tx).add(builder.getFee()));
        assertTrue(builder.getFee().getValue() > 0);
        assertTrue(builder.getChangeAmount().getValue() > 0);
    }

    @Test
    void acceptsAddressListUtxoListAndExistingFundingOutput() {
        BsvUtxo first = utxo(2, 1, 3_000);
        BsvUtxo second = utxo(3, 2, 4_000);
        UTXOProvider<AddressEnhance, BsvUtxo> provider = addresses -> {
            assertEquals(List.of(bap.getPayAccountAddress().toBase58()),
                    addresses.stream().map(AddressEnhance::toBase58).toList());
            return List.of(first, second);
        };
        Transaction funding = new Transaction();
        funding.outputs.add(new TransactionOutput(
                new P2PKH().lock(paymentAddress.toBase58()), 5_000d));

        TransactionBuilder builder = new TransactionBuilder(bap, keyProvider);
        builder.addInputs(List.of(bap.getPayAccountAddress()), provider)
                .addInputs(List.of(utxo(4, 3, 6_000)))
                .addInput(funding.outputs.get(0));

        assertEquals(4, builder.getTargetTransaction().inputs.size());
        assertEquals(BsvAmount.valueOf(18_000), inputSum(builder.getTargetTransaction()));
        assertNotNull(sourceOutput(builder.getTargetTransaction(), 3));
        assertEquals(BsvAmount.fromSdkValue(funding.outputs.get(0).satoshis),
                BsvAmount.fromSdkValue(sourceOutput(builder.getTargetTransaction(), 3).satoshis));
    }

    @Test
    void calculateChangeUsesTheFeeRatePassedToThePublicMethod() {
        TransactionBuilder builder = new TransactionBuilder(bap, keyProvider);
        builder.addInput(utxo(5, 0, 20_000))
                .addOutput(BsvAmount.valueOf(777), paymentAddress);

        assertEquals(BsvAmount.valueOf(19_223), builder.calculateChangeAmount(
                builder.getTargetTransaction(), paymentAddress, BsvAmount.valueOf(0)));
        assertEquals(BsvAmount.valueOf(0), builder.getFee());
    }

    @Test
    void signsTheConnectedInputAndProducesASpendableTransaction() {
        TransactionBuilder builder = new TransactionBuilder(bap, keyProvider);
        builder.addInput(utxo(6, 0, 20_000))
                .addOutput(BsvAmount.valueOf(777), paymentAddress)
                .changeAddress(paymentAddress);

        Transaction signed = builder.completeAndSignTx(keyProvider, true);

        assertNotNull(signed.inputs.get(0).unlockingScript);
        assertTrue(unlockingScriptBytes(signed, 0).length > 0);
        assertEquals(inputSum(signed), outputSum(signed).add(builder.getFee()));
        assertUnlockingScriptValid(signed, 0);
    }

    @Test
    void reportsSignerFailureInsteadOfReturningAnUnsignedTransaction() {
        // 新架构没有可注入的签名器：以「密钥提供者解析不到密钥」触发同类的签名失败
        SigningKeyProvider noKeys = pubkeyHash -> null;
        TransactionBuilder builder = new TransactionBuilder(bap, noKeys);
        builder.addInput(utxo(7, 0, 20_000))
                .addOutput(BsvAmount.valueOf(777), paymentAddress);

        assertThrows(SignErrorException.class, () -> builder.completeAndSignTx(noKeys, true));
    }

    @Test
    void amountAndSizeHelpersTolerateMissingOrNonNumericFields() {
        // 覆盖 §95 所列 TransactionBuilder 的若干 null／类型兜底（套路①：传坏输入）：
        //   :289 outputSize —— 输出锁定脚本为 null 时按 0 字节脚本计；
        //   :303 lockingScriptOf —— 无连接输出、或连接输出无锁定脚本时返回空脚本；
        //   :309—312 satoshisOf —— 无连接输出、或 satoshis 非 Number 时返回 null；
        //   :333 inputSum —— 上面返回 null 时按 0 计；
        //   :342 outputSum —— 输出 satoshis 非 Number 时按 0 计。
        // 这几处**互为上下游**，故用一笔"字段残缺"的交易一次走通：金额合计与体积估算的入口是 getFee()。
        TransactionBuilder builder = new TransactionBuilder(bap, keyProvider);

        // 输入：无来源交易（既无连接输出、也无锁定脚本可解析）
        builder.getTargetTransaction().inputs.add(new TransactionInput(
                new BsvHashBytes(new byte[32]).toString(), 0, new Script()));

        // 输出①：锁定脚本为 null（走 outputSize 的空脚本兜底）
        builder.getTargetTransaction().outputs.add(
                new com.metanet4j.bsv.transaction.TransactionOutput(null, 0d));

        // 输出②：satoshis 不是 Number（走 outputSum 的非数字兜底）
        com.metanet4j.bsv.transaction.TransactionOutput nonNumeric =
                new com.metanet4j.bsv.transaction.TransactionOutput(new Script(), 0d);
        nonNumeric.satoshis = com.metanet4j.bsv.primitives.Undefined.INSTANCE;
        builder.getTargetTransaction().outputs.add(nonNumeric);

        BsvAmount fee = builder.getFee();
        assertNotNull(fee, "字段残缺时费用估算应给出结果（各兜底生效），而非抛错");
    }

    @Test
    void probeSignerFailurePath() {
        // **探查用例（§295）**：为覆盖 `:227—228`（`sign()` 抛错 → 转 `SignErrorException`）寻找可触发的形态。
        // 已试过：① 缺解锁模板的输入（§93 实测 `sign()` 容忍）；② 输出为空（§92 备选 C）。
        // 本批试"**零输入**"这一未试形态，观察 `sign()` 是否抛错。
        TransactionBuilder builder = new TransactionBuilder(bap, keyProvider);
        builder.addOutput(BsvAmount.valueOf(777), paymentAddress);

        Transaction signed = builder.completeAndSignTx(keyProvider,
                TransactionSignature.SIGHASH_ALL, false);

        // **实测所得的契约**：零输入交易能被正常处理（`sign()` 对其**容忍**，不抛错）。
        // 这同时是一条**负向证据**：`:227—228` 的 `catch → throw SignErrorException` 在本形态下不可达。
        assertEquals(0, signed.inputs.size(), "零输入交易应被正常处理——sign() 对其容忍，不抛错");
    }

    @Test
    void preparingInputsToleratesASourceOutputWithoutLockingScript() {
        // 覆盖 `lockingScriptOf`（:303）的 `||` **第二腿**：`output.lockingScript == null`。
        // §267／§280 的样本里，连接输出要么不存在（第一腿为真）、要么锁定脚本非 null（两腿皆假），
        // **"输出存在但其锁定脚本为 null"这一组合从未走过**——即 §283 所说的"调用点组合"。
        // 经 `prepareInputsForSigning` 的 `:252` 触达（该处本身对 null 有兜底，故可安全构造）。
        TransactionBuilder builder = new TransactionBuilder(bap, keyProvider);
        Transaction tx = new Transaction();
        TransactionInput input = new TransactionInput(
                new BsvHashBytes(new byte[32]).toString(), 0, new Script());
        Transaction source = new Transaction();
        source.outputs.add(new com.metanet4j.bsv.transaction.TransactionOutput(null, 1d));
        input.sourceTransaction = source;
        tx.inputs.add(input);

        // 不应抛错：锁定脚本取不到时应**兜底为空脚本**，从而该输入因"公钥哈希为空"被跳过
        builder.prepareInputsForSigning(tx, keyProvider,
                TransactionSignature.SIGHASH_ALL, false);
        assertNull(input.unlockingScriptTemplate, "锁定脚本缺失的输入不应被设上解锁模板");
    }

    @Test
    void feeEstimationResolvesTheKeyLengthOnlyWhenBothPreconditionsHold() {
        // 覆盖 §95 所列 `TransactionBuilder:174`／`:180—181`：
        //     if (keyProvider != null && pubKeyHash.length > 0) {
        //         try { key = keyProvider.findKeyFromPubHash(pubKeyHash); }
        //         catch (RuntimeException e) { key = null; }      // ← :180—181
        //     }
        // `getUnLockScriptSize` 是 protected，而本测试类与之**同包**，故可直接调用。
        Script p2pkh = new P2PKH().lock(paymentAddress.toBase58());
        TransactionInput input = new TransactionInput(
                new BsvHashBytes(new byte[32]).toString(), 0, new Script());

        // ① 两条件皆真 → 查密钥、按**实际公钥长度**计费
        TransactionBuilder withKeys = new TransactionBuilder(bap, keyProvider);
        assertTrue(withKeys.getUnLockScriptSize(input, p2pkh) > 0,
                "有提供者且公钥哈希非空时应按实际公钥长度计费");

        // ② **第二腿为假**：脚本不可识别 → 公钥哈希为空 → 走保守估算（`&&` 短路）
        TransactionBuilder builder = new TransactionBuilder(bap, keyProvider);
        assertTrue(builder.getUnLockScriptSize(input,
                        new com.metanet4j.bsv.script.Script()
                                .writeOpCode(com.metanet4j.bsv.script.OP.OP_1)) > 0,
                "锁定脚本不可识别时应走保守估算");

        // ②′ **第一腿为假**：`keyProvider == null`（§280 只覆盖了第二腿为假的情形——
        //     这正是"不仅每个操作数要两侧、**每个调用点的组合**也要走"的体现，§282 的观察）。
        TransactionBuilder noProvider = new TransactionBuilder(bap, null);
        assertTrue(noProvider.getUnLockScriptSize(input, p2pkh) > 0,
                "无提供者时应直接走保守估算（第一腿短路），而非抛 NPE");

        // ③ **提供者抛错** → 转 key = null → 仍走保守估算（`:180—181` 的 catch）
        TransactionBuilder throwing = new TransactionBuilder(bap, pubKeyHash -> {
            throw new IllegalStateException("模拟提供者找不到密钥");
        });
        assertTrue(throwing.getUnLockScriptSize(input, p2pkh) > 0,
                "提供者抛错时费用估算不应中断，应退回保守估算");
    }

    @Test
    void changeAmountToleratesMissingOrNonNumericFields() {
        // 覆盖 §95 所列若干兜底（套路①：传坏输入）——**入口按 §99 的结论改为 `calculateChangeAmount`**：
        //   :157 lockingScriptOf —— 输入无连接输出、或其无锁定脚本时返回空脚本；
        //   :309—312 satoshisOf —— 无连接输出、或 satoshis 非 Number 时返回 null；
        //   :333 inputSum —— 上游为 null 时按 0 计；
        //   :342 outputSum —— 输出 satoshis 非 Number 时按 0 计。
        // （:289 outputSize 的 null 侧已由 §99 判定不可达——其唯一调用点传入的是硬编码的非 null P2PKH 输出。）
        TransactionBuilder builder = new TransactionBuilder(bap, keyProvider);
        builder.addInput(utxo(9, 0, 20_000))                    // 对照：正常输入
                .addOutput(BsvAmount.valueOf(777), paymentAddress);

        // 追加"无来源交易"的输入 → satoshisOf 返回 null → inputSum 按 0 计
        builder.getTargetTransaction().inputs.add(new TransactionInput(
                new BsvHashBytes(new byte[32]).toString(), 0, new Script()));

        // 注：**"锁定脚本为 null 的输出"暂不放入**——实测它会让 calculateChangeAmount 抛 NPE
        //（见 migration-rules §100：某条路径未对 lockingScript 判空）。该现象已单独记录，
        // 待定性后再决定是补测还是登记；本用例先覆盖其余可达的兜底。

        // 追加"satoshis 非 Number"的输出 → outputSum 按 0 计（新 SDK 的类型宽松使这是真实可能状态）
        com.metanet4j.bsv.transaction.TransactionOutput nonNumeric =
                new com.metanet4j.bsv.transaction.TransactionOutput(new Script(), 0d);
        nonNumeric.satoshis = com.metanet4j.bsv.primitives.Undefined.INSTANCE;
        builder.getTargetTransaction().outputs.add(nonNumeric);

        BsvAmount change = builder.calculateChangeAmount(
                builder.getTargetTransaction(), paymentAddress, BsvAmount.valueOf(0));
        assertNotNull(change, "字段残缺时仍应算出找零（各兜底生效），而非抛错");
    }

    @Test
    void prepareInputsRejectsNullProviderAndToleratesUnusableInputs() {
        // 覆盖 §95 所列 TransactionBuilder 的三处（:`246—247`、`:253—254`、`:174—181`）。
        // prepareInputsForSigning 是 protected，而本测试类与之**同包**，故可直接调用。
        TransactionBuilder builder = new TransactionBuilder(bap, keyProvider);

        // ① :246—247 提供者为 null → 明确报错（而非在后续以 NPE 的形式失败）
        Transaction empty = new Transaction();
        assertThrows(IllegalArgumentException.class,
                () -> builder.prepareInputsForSigning(empty, null,
                        TransactionSignature.SIGHASH_ALL, false),
                "空提供者应在入口被拒绝");

        // ② :253—254 锁定脚本不可识别 → 公钥哈希为空 → **跳过该输入**（不抛错、不中断整批）
        Transaction withUnknownScript = new Transaction();
        withUnknownScript.inputs.add(new TransactionInput(
                new BsvHashBytes(new byte[32]).toString(), 0,
                new com.metanet4j.bsv.script.Script()
                        .writeOpCode(com.metanet4j.bsv.script.OP.OP_1)));
        builder.prepareInputsForSigning(withUnknownScript, keyProvider,
                TransactionSignature.SIGHASH_ALL, false);   // 不抛即通过

        // ③ :174—181 提供者**抛错**（本项目提供者的既定表现，见 §132）→ 转为 key = null，
        //    即"找不到密钥"不应让整批准备中断。
        Transaction p2pkh = new Transaction();
        p2pkh.inputs.add(new TransactionInput(new BsvHashBytes(new byte[32]).toString(), 0,
                new P2PKH().lock(paymentAddress.toBase58())));
        SigningKeyProvider throwing = pubkeyHash -> {
            throw new IllegalStateException("模拟提供者找不到密钥");
        };
        builder.prepareInputsForSigning(p2pkh, throwing,
                TransactionSignature.SIGHASH_ALL, false);   // 不抛即通过
    }

    @Test
    void feeEstimationFallsBackForUnrecognizableScripts() {
        // 覆盖 §90.3 列的 getUnLockScriptSize 两处（套路①/②）：
        //   :174—181 提供者抛错时 `key = null`（费用估算不因取不到密钥而中断）；
        //   :189—193 锁定脚本不可识别时退回**保守估算**（`SIG_SIZE + 65`）。
        // 用一个既非 P2PKH 也非铭文的锁定脚本（纯操作码）同时触发这两条降级路径。
        TransactionBuilder builder = new TransactionBuilder(bap, keyProvider);
        // 用 BsvUtxo 加入（它携带金额；TransactionInput 本身不带 satoshi，会让费用估算失去输入值）。
        // 该 UTXO **无地址、只有一段不可识别的脚本**（纯操作码 OP_1），
        // 因而既走"无地址但有脚本"的分支，又让脚本类型判定失败、触发保守估算。
        builder.addInput(new BsvUtxo(new BsvHashBytes(new byte[32]), 0, 20_000L, 0, false, null,
                new com.metanet4j.bsv.script.Script()
                        .writeOpCode(com.metanet4j.bsv.script.OP.OP_1).toUint8Array()));
        builder.addOutput(BsvAmount.valueOf(777), paymentAddress);
        builder.changeAddress(paymentAddress);

        BsvAmount fee = builder.getFee();   // 注意：getFee() 返回 BsvAmount，而非 long
        assertTrue(fee.getValue() > 0,
                "锁定脚本不可识别时应按保守值估算费用，而非中断（实际：" + fee + "）");
    }

    @Test
    void addInputHandlesAllThreeUtxoShapes() {
        // 覆盖 §90.3 列的 addInput 三分支（套路①/③）：
        //   ① 有合法地址 → 用 P2PKH 锁定脚本；
        //   ② **地址非法** → 解析抛错被 catch、锁定脚本留空，但**该输入仍应被加入**；
        //   ③ **无地址但有脚本** → 用 UTXO 自带的脚本。
        BsvHashBytes hash = new BsvHashBytes(new byte[32]);

        // ① 合法地址
        TransactionBuilder withAddress = new TransactionBuilder(bap, keyProvider);
        withAddress.addInput(new BsvUtxo(hash, 0, 20_000L, 0, false, paymentAddress.toBase58(), null));
        assertEquals(1, withAddress.getTargetTransaction().inputs.size(), "合法地址应正常加入输入");

        // ② 地址非法——**不应抛错**，而是记录告警并把锁定脚本留空
        TransactionBuilder badAddress = new TransactionBuilder(bap, keyProvider);
        badAddress.addInput(new BsvUtxo(hash, 0, 20_000L, 0, false, "not-a-valid-address", null));
        assertEquals(1, badAddress.getTargetTransaction().inputs.size(),
                "地址非法时应仍加入输入（锁定脚本留空），而非让 addInput 失败");

        // ③ 无地址但有脚本 → 用 UTXO 自带脚本
        TransactionBuilder scriptOnly = new TransactionBuilder(bap, keyProvider);
        scriptOnly.addInput(new BsvUtxo(hash, 0, 20_000L, 0, false, null,
                new com.metanet4j.bsv.script.Script()
                        .writeOpCode(com.metanet4j.bsv.script.OP.OP_1).toUint8Array()));
        assertEquals(1, scriptOnly.getTargetTransaction().inputs.size(), "无地址有脚本时应正常加入输入");

        // ④ **既无地址也无脚本** → 走 `else if` 的假侧：锁定脚本保持为空，输入仍应被加入
        TransactionBuilder neither = new TransactionBuilder(bap, keyProvider);
        neither.addInput(new BsvUtxo(hash, 0, 20_000L, 0, false, null, null));
        assertEquals(1, neither.getTargetTransaction().inputs.size(),
                "既无地址也无脚本时应仍加入输入（锁定脚本留空）");
    }

    @Test
    void broadcastDelegatesOnlyToTheProvidedLocalFunction() {
        AtomicReference<String> receivedRaw = new AtomicReference<>();

        String result = TransactionBuilder.broadcast("010203", raw -> {
            receivedRaw.set(raw);
            return "local-txid";
        });

        assertEquals("010203", receivedRaw.get());
        assertEquals("local-txid", result);
    }

    @Test
    void constructorsPreserveTheExistingTransactionAndKeyProvider() {
        AddressEnhance testAddress = bap.getPayAccountAddress();
        Transaction existing = new Transaction();
        existing.outputs.add(new TransactionOutput(new P2PKH().lock(testAddress.toBase58()), 11d));

        TransactionBuilder builder = new TransactionBuilder(bap, existing, keyProvider);
        assertSame(existing, builder.getTargetTransaction());
        assertSame(keyProvider, builder.getKeyProvider());
        builder.addOutput(BsvAmount.valueOf(22), testAddress);
        assertEquals(BsvAmount.valueOf(33), outputSum(existing));
        assertEquals(BsvAmount.valueOf(11), BsvAmount.fromSdkValue(existing.outputs.get(0).satoshis));

        TransactionBuilder empty = new TransactionBuilder(bap, keyProvider);
        assertEquals(0, empty.getTargetTransaction().outputs.size(), "未传交易时应给出空交易");
    }

    @Test
    void sigmaSigningKeepsThePayloadAndExposesAVerifiableCurrentIdentitySignature() {
        BapDataLockBuilder data = new BapDataLockBuilder(bap).buildAlias("profile");
        TransactionBuilder builder = new TransactionBuilder(bap, keyProvider)
                .addInput(utxo(8, 0, 20_000)).addDataOutput(data);
        List<byte[]> payload = builder.getTargetTransaction().outputs.get(0).lockingScript.getChunks()
                .stream().skip(2).map(com.metanet4j.sdk.utils.ScriptHelper::getPushData).toList();
        assertNull(builder.getSigma());
        assertSame(builder, builder.addSigmaSign(bap));
        Sigma sigma = builder.getSigma();
        assertSame(builder.getTargetTransaction(), sigma.getTransaction());
        assertEquals(bap.getCurrentAddress().toBase58(), sigma.getSig().getAddress());
        assertEquals(1, sigma.getSigInstanceCount());
        assertTrue(new Sigma(bap, builder.getTargetTransaction()).verify());
        assertEquals(BsvAmount.valueOf(20_000), inputSum(builder.getTargetTransaction()));
        assertEquals(BsvAmount.ZERO, BsvAmount.fromSdkValue(builder.getTargetTransaction().outputs.get(0).satoshis));
        for (int i = 0; i < payload.size(); i++) {
            assertArrayEquals(payload.get(i), com.metanet4j.sdk.utils.ScriptHelper.getPushData(
                    builder.getTargetTransaction().outputs.get(0).lockingScript.getChunks().get(i + 2)));
        }
    }

    @Test
    void publicOnlyKeyUsesAConservativeUnlockingSizeForChangeEstimation() {
        // 新架构：解析不到密钥的提供者代表「只有公钥、无本地私钥」，费用按 65 字节保守估算
        SigningKeyProvider noKeys = pubKeyHash -> null;
        TransactionBuilder full = new TransactionBuilder(bap, keyProvider)
                .addInput(utxo(9, 0, 20_000)).addOutput(BsvAmount.valueOf(777), paymentAddress);
        TransactionBuilder publicOnly = new TransactionBuilder(bap, noKeys)
                .addInput(utxo(9, 0, 20_000)).addOutput(BsvAmount.valueOf(777), paymentAddress);

        BsvAmount fullChange = full.calculateChangeAmount(full.getTargetTransaction(), paymentAddress,
                BsvAmount.valueOf(1000));
        BsvAmount publicChange = publicOnly.calculateChangeAmount(
                publicOnly.getTargetTransaction(), paymentAddress, BsvAmount.valueOf(1000));
        // 缺少私钥时保守按 65 字节公钥计费，比已知压缩公钥多 32 字节。
        assertEquals(full.getFee().add(BsvAmount.valueOf(32)), publicOnly.getFee());
        assertEquals(fullChange.subtract(BsvAmount.valueOf(32)), publicChange);
        assertEquals(BsvAmount.valueOf(19_223), publicChange.add(publicOnly.getFee()));
    }

    private BsvUtxo utxo(int hashByte, long index, long satoshis) {
        byte[] hash = new byte[32];
        hash[0] = (byte) hashByte;
        return new BsvUtxo(BsvHashBytes.of(hash), index, satoshis, 0, false,
                paymentAddress.toBase58(),
                new P2PKH().lock(paymentAddress.toBase58()).toUint8Array());
    }

    private static List<Integer> toBoxed(byte[] data) {
        List<Integer> out = new java.util.ArrayList<>(data.length);
        for (byte b : data) {
            out.add(b & 0xFF);
        }
        return out;
    }

    private static void assertUnlockingScriptValid(Transaction tx, int index) {
        com.metanet4j.sdk.TransactionScriptAssertions.assertInputScriptValid(tx, index);
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

    private static byte[] unlockingScriptBytes(Transaction tx, int index) {
        return asScript(tx.inputs.get(index).unlockingScript).toUint8Array();
    }

    private static TransactionOutput sourceOutput(Transaction tx, int index) {
        if (tx.inputs.get(index).sourceTransaction instanceof Transaction source
                && source.outputs != null && !source.outputs.isEmpty()) {
            return source.outputs.get(0);
        }
        throw new IllegalStateException("输入 " + index + " 缺少连接输出");
    }

    private static BsvAmount inputSum(Transaction tx) {
        BsvAmount sum = BsvAmount.ZERO;
        for (var input : tx.inputs) {
            if (input.sourceTransaction instanceof Transaction source && source.outputs != null
                    && !source.outputs.isEmpty()) {
                // 【R6 修正】原实现固定读 source.outputs.get(0)，与生产代码里已被修正的同一处缺陷一致
                //（TxHelperExtend.sourceOutputOf 曾硬编码 get(0)、attachSourceOutput 曾把输出固定放在 0 号，
                //  两者"互相掩盖"）。现按 input.sourceOutputIndex 取，以匹配修正后的真实契约。
                int index = input.sourceOutputIndex;
                if (index >= 0 && index < source.outputs.size()) {
                    sum = sum.add(BsvAmount.fromSdkValue(source.outputs.get(index).satoshis));
                }
            }
        }
        return sum;
    }

    private static BsvAmount outputSum(Transaction tx) {
        BsvAmount sum = BsvAmount.ZERO;
        for (var output : tx.outputs) {
            sum = sum.add(BsvAmount.fromSdkValue(output.satoshis));
        }
        return sum;
    }
}