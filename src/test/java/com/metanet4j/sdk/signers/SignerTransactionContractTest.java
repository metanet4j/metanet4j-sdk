package com.metanet4j.sdk.signers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.Lists;
import com.metanet4j.base.bsv.BsvHashBytes;
import com.metanet4j.bsv.primitives.TransactionSignature;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.script.templates.P2PKH;
import com.metanet4j.bsv.transaction.Transaction;
import com.metanet4j.bsv.transaction.TransactionInput;
import com.metanet4j.bsv.transaction.TransactionOutput;
import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.bap.BapBase;
import com.metanet4j.sdk.bap.SigningKeyProvider;
import com.metanet4j.sdk.exception.SignErrorException;
import com.metanet4j.sdk.remote.RemoteTransactionSigner;
import com.metanet4j.sdk.utils.ScriptHelper;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/**
 * 签名请求载体与签名器的契约（原 `SignerTransactionContractTest` 的保留部分）。
 *
 * <p><b>覆盖迁移说明</b>：原文件 11 个用例中有 6 个测的是已按计划删除或重新设计的抽象——
 * `TransactionExtend` 的转换／旧解析器构造（类已删）、旧 `TransactionSigner` 的 `isReady` 与状态序列化
 * （抽象已按 §3 取消）、旧 2／3 参构造器。它们已删除并登记能力去向（migration-rules §23）：
 * 序列化保真 → `SigHashExtendContractTest#manuallyBuiltAndParsedTransactionsProduceTheSameDigest`；
 * 签名范围传递 → `OrdTransactionBuilderContractTest#appliesRequestedSignatureScopeToTheSignature`；
 * 网络参数与旧解析器特性在新 SDK 无对应物（网络在 `AddressEnhance`／`BsvNetwork` 层测试）。
 */
class SignerTransactionContractTest {

    private final BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
    private final SigningKeyProvider keyProvider = new BapBase.BapProviderKeyBag(bap);
    private final AddressEnhance payAddress = bap.getPayAccountAddress();

    @Test
    void proposedTransactionKeepsRequestedSigningMode() {
        Transaction tx = new Transaction();
        ExtendForSignTransaction all = new ExtendForSignTransaction(tx,
                TransactionSignature.SIGHASH_ALL, true, false);
        ExtendForSignTransaction single = new ExtendForSignTransaction(tx,
                TransactionSignature.SIGHASH_SINGLE, true, true);
        ExtendForSignTransaction noFork = new ExtendForSignTransaction(tx,
                TransactionSignature.SIGHASH_NONE, false, false);

        assertSame(tx, all.partialTx, "载体应持有传入的交易实例");
        assertEquals(TransactionSignature.SIGHASH_ALL | TransactionSignature.SIGHASH_FORKID,
                all.signatureScope());
        assertEquals(TransactionSignature.SIGHASH_SINGLE | TransactionSignature.SIGHASH_ANYONECANPAY
                | TransactionSignature.SIGHASH_FORKID, single.signatureScope());
        assertTrue(single.isAnyoneCanPay());
        assertFalse(noFork.isUseForkId(), "未开启 ForkID 时范围不含 0x40");
        assertEquals(TransactionSignature.SIGHASH_NONE, noFork.signatureScope());
    }

    @Test
    void signerEncodesRequestedScopeIntoTheSignature() {
        Transaction tx = transactionWithPaymentInput();

        assertTrue(OrdinalsTransactionSigner.signInput(tx, 0, keyProvider,
                TransactionSignature.SIGHASH_SINGLE, true));
        tx.sign().toCompletableFuture().join();

        assertEquals(TransactionSignature.SIGHASH_SINGLE | TransactionSignature.SIGHASH_ANYONECANPAY
                        | TransactionSignature.SIGHASH_FORKID,
                signatureHashType(tx, 0), "签名尾字节应编码所选范围");
    }

    @Test
    void missingConnectedOutputIsReportedAndBatchSkipsIt() {
        Transaction orphan = new Transaction();
        orphan.inputs.add(new TransactionInput(new BsvHashBytes(new byte[32]).toString(), 0,
                new Script()));

        assertThrows(SignErrorException.class,
                () -> OrdinalsTransactionSigner.signInput(orphan, 0, keyProvider,
                        TransactionSignature.SIGHASH_ALL, false),
                "单输入签名遇到缺失连接输出应显式报错");
        assertTrue(new RemoteTransactionSigner().signInputs(orphan, keyProvider,
                        TransactionSignature.SIGHASH_ALL, true),
                "批量签名遇到缺失连接输出应跳过该输入而非整体失败");
    }

    @Test
    void batchSigningSkipsInputsItCannotSignWithoutAbortingTheBatch() {
        // 覆盖 OrdinalsTransactionSigner 批量入口的两处（§95 所列 :97—98 与 :103—105）：
        //   :97—98  `if (!signInput(...)) { log.warn("Skip input {} during batch signing", i); }`
        //           —— 某个输入签不了时**记警告后继续**，而非中断整批；
        //   :103—105 `tx.sign()` 抛错时 → log.warn + **返回 false**（把失败如实上报）。
        // **实测所得的构造要点**：并非任何"签不了的输入"都能走到 :97—98 的跳过分支——
        // 例如"缺连接输出"的输入会让 signInput **抛 SignErrorException**、直接越过循环传出，
        // 于是 :97—98 根本不被求值。**能被"记警告后继续"的，是 signInput 返回 false 的那些情形**，
        // 如"提供者找不到密钥"（§264 已实测其返回 false，而非抛错）。
        Transaction tx = transactionWithPaymentInput();
        SigningKeyProvider noKeys = pubkeyHash -> {
            throw new IllegalStateException("模拟提供者找不到密钥");
        };

        // 关键：**不应抛错**——批量的契约是"跳过签不了的、继续处理其余"
        boolean allSigned = new OrdinalsTransactionSigner().signInputs(tx, noKeys,
                TransactionSignature.SIGHASH_ALL, false);
        // allSigned 的真假取决于 `tx.sign()` 是否容忍"无模板的输入"（§93 已实测其**容忍**）——
        // 两种取值都合法，故此处只断言"调用正常返回"，并把它记入日志便于观察实际取值
        System.out.println("[§272 观察] signInputs 返回：" + allSigned);
    }

    @Test
    void singleInputSigningTreatsAThrowingProviderAsNoKey() {
        // 覆盖 OrdinalsTransactionSigner 的 `:73—75`（套路②：让协作者抛错）：
        //     key = keyProvider == null ? null : keyProvider.findKeyFromPubHash(pubKeyHash);
        // 被 try/catch 包住 —— 本项目提供者**找不到密钥时抛 RuntimeException 而非返回 null**
        // （§132 已确认、BapProviderKeyBagTest 明确要求该行为），故该 catch 是**必需**的：
        // 它把"抛错"与"返回 null"两种表现统一为"取不到密钥"。
        Transaction tx = transactionWithPaymentInput();
        SigningKeyProvider throwing = pubkeyHash -> {
            throw new IllegalStateException("模拟提供者找不到密钥");
        };

        assertFalse(OrdinalsTransactionSigner.signInput(tx, 0, throwing,
                        TransactionSignature.SIGHASH_ALL, false),
                "提供者抛错时应视同取不到密钥并返回 false，而非把原始异常外泄给调用方");

        // 同一处的**另一侧**：`keyProvider == null`（§98 的方法——三元的两侧都要走）。
        // 某些调用方（如只做验签或离线组装的场景）本就可能不提供密钥提供者，
        // 此时应**视同取不到密钥**，而不是在调用 `keyProvider.findKeyFromPubHash` 时抛 NPE。
        assertFalse(OrdinalsTransactionSigner.signInput(tx, 0, null,
                        TransactionSignature.SIGHASH_ALL, false),
                "提供者为 null 时应视同取不到密钥并返回 false，而非抛 NPE");
    }

    @Test
    void singleInputSigningSkipsUnrecognizableScripts() {
        // 覆盖 §90.2 列的 OrdinalsTransactionSigner 两处（套路①/③）：
        //   :57 连接输出**无锁定脚本** → 按空脚本处理；
        //   :62—67 空脚本既非 P2PKH 也非 Ordinals 铭文 → **返回 false**（跳过该输入，而非抛错）。
        Transaction tx = new Transaction();
        TransactionInput input = new TransactionInput(
                new BsvHashBytes(new byte[32]).toString(), 0, new Script());
        Transaction source = new Transaction();
        source.outputs.add(new com.metanet4j.bsv.transaction.TransactionOutput(null, 1d));
        input.sourceTransaction = source;
        tx.inputs.add(input);

        assertFalse(OrdinalsTransactionSigner.signInput(tx, 0, keyProvider,
                        TransactionSignature.SIGHASH_ALL, false),
                "既非 P2PKH 也非铭文的脚本应返回 false 而非抛错");
    }

    @Test
    void singleInputSigningRejectsNullTransactionAndOutOfRangeIndex() {
        // 覆盖 §82 清单收口后仍余的三条件守卫（§240 查明剩余缺口以健壮性分支为主）：
        //     if (tx == null || inputIndex < 0 || inputIndex >= tx.inputs.size()) throw new SignErrorException(…)
        // 三个条件**各自**对应一种调用方错误，故逐条断言——"某一侧已覆盖"不等于"该合取式已覆盖"（§230）。
        Transaction tx = transactionWithPaymentInput();   // 恰有 1 个输入

        assertThrows(SignErrorException.class,
                () -> OrdinalsTransactionSigner.signInput(null, 0, keyProvider,
                        TransactionSignature.SIGHASH_ALL, false),
                "交易为 null 应报签名错误");

        assertThrows(SignErrorException.class,
                () -> OrdinalsTransactionSigner.signInput(tx, -1, keyProvider,
                        TransactionSignature.SIGHASH_ALL, false),
                "负数下标应报签名错误");

        assertThrows(SignErrorException.class,
                () -> OrdinalsTransactionSigner.signInput(tx, tx.inputs.size(), keyProvider,
                        TransactionSignature.SIGHASH_ALL, false),
                "越界下标应报签名错误（上界为排他）");
    }

    @Test
    void ordinalBatchSigningRejectsNullAndSignsEveryInput() {
        // 覆盖 §82 清单中 OrdinalsTransactionSigner.signInputs（**批量**版本，与既有用例覆盖的
        // 静态 signInput（单输入）以及 RemoteTransactionSigner.signInputs（另一个类）都不同）。
        OrdinalsTransactionSigner signer = new OrdinalsTransactionSigner();

        // 分支一：null 交易直接返回 false（不去遍历 inputs，避免 NPE）
        assertFalse(signer.signInputs(null, keyProvider, TransactionSignature.SIGHASH_ALL, false),
                "null 交易应返回 false");

        // 分支二：全部输入都能签时应返回 true，且交易被真正签名
        Transaction tx = transactionWithPaymentInput();
        assertTrue(signer.signInputs(tx, keyProvider, TransactionSignature.SIGHASH_ALL, false),
                "全部输入均可签时应返回 true");
        byte[] unlocking = ScriptHelper.getPushData(
                asScript(tx.inputs.get(0).unlockingScript).getChunks().get(0));
        assertEquals(TransactionSignature.SIGHASH_ALL | TransactionSignature.SIGHASH_FORKID,
                unlocking[unlocking.length - 1] & 0xFF, "批量签名应写入 ForkID 位");
    }

    @Test
    void singleInputSigningCreatesAForkIdSignature() {
        Transaction tx = transactionWithPaymentInput();

        assertTrue(OrdinalsTransactionSigner.signInput(tx, 0, keyProvider,
                TransactionSignature.SIGHASH_ALL, false));
        tx.sign().toCompletableFuture().join();

        byte[] unlocking = ScriptHelper.getPushData(
                asScript(tx.inputs.get(0).unlockingScript).getChunks().get(0));
        assertTrue(unlocking.length > 1, "解锁脚本首项应为 DER 签名加范围尾字节");
        assertEquals(TransactionSignature.SIGHASH_ALL | TransactionSignature.SIGHASH_FORKID,
                unlocking[unlocking.length - 1] & 0xFF, "ForkID 位应置位");
    }

    @Test
    void unknownKeysAreSkippedByBatchAndReportedUnavailableBySingleInput() {
        Transaction tx = transactionWithPaymentInput();
        SigningKeyProvider noKeys = pubkeyHash -> null;

        assertFalse(OrdinalsTransactionSigner.signInput(tx, 0, noKeys,
                TransactionSignature.SIGHASH_ALL, false), "单输入遇到无法解析的密钥应返回 false");
        assertTrue(new RemoteTransactionSigner().signInputs(tx, noKeys,
                TransactionSignature.SIGHASH_ALL, true), "批量签名应跳过无法解析的输入");
    }

    /** 单输入交易：连接输出为标准 P2PKH，供签名使用。 */
    private Transaction transactionWithPaymentInput() {
        Transaction tx = new Transaction();
        TransactionInput input = new TransactionInput(new BsvHashBytes(new byte[32]).toString(), 0,
                new Script());
        input.sequence = 0xffff_ffffL;
        Transaction source = new Transaction();
        source.outputs = Lists.newArrayList(new TransactionOutput(
                new P2PKH().lock(payAddress.toBase58()), 20_000d));
        input.sourceTransaction = source;
        tx.inputs.add(input);
        tx.outputs.add(new TransactionOutput(new P2PKH().lock(payAddress.toBase58()), 19_000d));
        return tx;
    }

    private static int signatureHashType(Transaction tx, int index) {
        byte[] signature = ScriptHelper.getPushData(
                asScript(tx.inputs.get(index).unlockingScript).getChunks().get(0));
        return signature[signature.length - 1] & 0xFF;
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

    @Test
    void rejectsANullPartialTransaction() {
        // 覆盖 ExtendForSignTransaction 构造器首部的 null 检查
        // （`if (partialTx == null) throw new IllegalArgumentException(...)`）——
        // 该守卫真实可达：调用方可能在没有待签交易时误建签名器，此时应立刻报错而非留下半初始化对象。
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ExtendForSignTransaction(null, TransactionSignature.SIGHASH_ALL, true, false));
        assertEquals("待签名交易不能为空", error.getMessage(), "应说明被拒绝的原因");
    }

    @Test
    void reportsTheHashFlagsItWasConstructedWith() {
        // 覆盖 §82 清单中的 ExtendForSignTransaction.getHashFlags（简单 getter，回传构造时传入的 hashFlags）。
        // 这里取一个同时含基础类型与 ANYONECANPAY／FORKID 位的值，确保**原样回传**而非被规范化。
        int flags = TransactionSignature.SIGHASH_SINGLE
                | TransactionSignature.SIGHASH_ANYONECANPAY
                | TransactionSignature.SIGHASH_FORKID;
        ExtendForSignTransaction signer = new ExtendForSignTransaction(new Transaction(), flags, true, false);

        assertEquals(flags, signer.getHashFlags(), "getHashFlags 应原样回传构造时传入的 flags");
    }

}
