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
    void singleInputSigningCreatesAForkIdSignature() {
        Transaction tx = transactionWithPaymentInput();

        assertTrue(OrdinalsTransactionSigner.signInput(tx, 0, keyProvider,
                TransactionSignature.SIGHASH_ALL, false));

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
}
