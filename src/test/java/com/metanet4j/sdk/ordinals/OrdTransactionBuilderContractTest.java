package com.metanet4j.sdk.ordinals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metanet4j.base.bsv.BsvAmount;
import com.metanet4j.base.bsv.BsvHashBytes;
import com.metanet4j.base.bsv.BsvUtxo;
import com.metanet4j.bsv.primitives.TransactionSignature;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.script.templates.P2PKH;
import com.metanet4j.bsv.transaction.Transaction;
import com.metanet4j.bsv.transaction.TransactionInput;
import com.metanet4j.bsv.transaction.TransactionOutput;
import com.metanet4j.sdk.PrivateKey;
import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.bap.BapBase;
import com.metanet4j.sdk.bap.SigningKeyProvider;
import com.metanet4j.sdk.exception.SignErrorException;
import com.metanet4j.sdk.utils.ScriptHelper;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * {@link OrdTransactionBuilder} 的构建与签名契约。
 *
 * <p>迁移说明：旧契约用 {@code KeyBag} + 可注入的 {@code TransactionSigner}（含 mock）验证，
 * 新架构按迁移计划 §3 取消了该抽象——签名改为「按公钥哈希取私钥 → 注入解锁模板 →
 * {@code Transaction.sign()}」。因此原先「把范围转发给 signer」的断言改为**直接验证签名尾字节
 * 编码的 SIGHASH 范围**（更强：验证最终产物而非中间调用）。
 */
class OrdTransactionBuilderContractTest {

    private final BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
    private final SigningKeyProvider keyProvider = new BapBase.BapProviderKeyBag(bap);
    private final AddressEnhance paymentAddress = bap.getPayAccountAddress();
    private final AddressEnhance ordAddress = bap.getOrdAddress();

    @Test
    void constructorsKeepTheSelectedTransactionAndKeyProvider() {
        Transaction existing = new Transaction();
        existing.addOutput(new TransactionOutput(new P2PKH().lock(paymentAddress.getHash160()), 546d));

        OrdTransactionBuilder defaults = new OrdTransactionBuilder(bap, keyProvider);
        OrdTransactionBuilder withExisting = new OrdTransactionBuilder(bap, existing, keyProvider);

        assertNotNull(defaults.build());
        assertTrue(defaults.build().inputs.isEmpty(), "默认构建器应给出空交易");
        assertSame(existing, withExisting.build(), "传入的交易应被沿用而非复制");
        assertSame(keyProvider, defaults.getKeyProvider());
        assertEquals(546L, satoshisOf(withExisting.build().outputs.get(0)));
    }

    @Test
    void signsPaymentAndInscribedInputsInMemory() {
        OrdTransactionBuilder builder = new OrdTransactionBuilder(bap, keyProvider);
        builder.addOrdInput(ordinalUtxo(1, 0)).addInput(paymentUtxo(2, 0, 20_000));
        builder.addOrdOutput(ordAddress, "x".getBytes(StandardCharsets.US_ASCII), "text/plain", null);
        builder.withFeeKb(BsvAmount.valueOf(500)).changeAddress(paymentAddress);

        assertSame(builder, builder.signTx(keyProvider, true));
        Transaction signed = builder.build();
        assertEquals(1L, satoshisOf(signed.outputs.get(0)), "铭文输出金额固定 1 satoshi");
        assertEquals(2, signed.inputs.size());
        assertEquals(inputSum(signed).getValue(),
                outputSum(signed).add(builder.getFee()).getValue(), "输入金额 = 输出金额 + 手续费");
        for (TransactionInput input : signed.inputs) {
            assertTrue(asScript(input.unlockingScript).toUint8Array().length > 0, "解锁脚本不应为空");
        }
    }

    @Test
    void rejectsInputsWithoutAResolvableKey() {
        AddressEnhance unknown = AddressEnhance.fromPrivateKey(PrivateKey.of(BigInteger.valueOf(99)));
        OrdTransactionBuilder builder = new OrdTransactionBuilder(bap, keyProvider);
        builder.addInput(utxoFor(unknown, 9, 0, 20_000L));

        assertThrows(SignErrorException.class, () -> builder.signTx(keyProvider, true),
                "密钥提供者不认识该地址时应报签名错误");
    }

    @Test
    void appliesRequestedSignatureScopeToTheSignature() {
        OrdTransactionBuilder builder = new OrdTransactionBuilder(bap, keyProvider);
        builder.addInput(paymentUtxo(4, 0, 20_000));

        builder.signTx(keyProvider, TransactionSignature.SIGHASH_SINGLE, true, true);

        assertEquals(TransactionSignature.SIGHASH_SINGLE | TransactionSignature.SIGHASH_ANYONECANPAY
                        | TransactionSignature.SIGHASH_FORKID,
                signatureHashType(builder.build().inputs.get(0)), "签名尾字节应编码所选范围");
    }

    @Test
    void simpleSignatureOverloadDefaultsToAll() {
        OrdTransactionBuilder builder = new OrdTransactionBuilder(bap, keyProvider);
        builder.addInput(paymentUtxo(5, 0, 20_000));

        builder.signTx(keyProvider, false);

        assertEquals(TransactionSignature.SIGHASH_ALL | TransactionSignature.SIGHASH_FORKID,
                signatureHashType(builder.build().inputs.get(0)), "便捷重载应使用 SIGHASH_ALL");
    }

    /** 余额支付输入的 UTXO（锁定脚本为标准 P2PKH）。 */
    private BsvUtxo paymentUtxo(int hashByte, long index, long satoshis) {
        return utxoFor(paymentAddress, hashByte, index, satoshis);
    }

    /** 铭文输入的 UTXO（锁定脚本为铭文脚本）。 */
    private BsvUtxo ordinalUtxo(int hashByte, long index) {
        Script inscription = OrdScriptBuilder.buildInscription(ordAddress,
                "prior".getBytes(StandardCharsets.US_ASCII), "text/plain", null);
        return new BsvUtxo(hash(hashByte), index, 1L, 0, false, null, inscription.toUint8Array());
    }

    private BsvUtxo utxoFor(AddressEnhance address, int hashByte, long index, long satoshis) {
        return new BsvUtxo(hash(hashByte), index, satoshis, 0, false, address.toBase58(),
                new P2PKH().lock(address.getHash160()).toUint8Array());
    }

    private BsvHashBytes hash(int firstByte) {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) firstByte;
        return BsvHashBytes.of(bytes);
    }

    /** 解锁脚本字段可能是 {@code Script}、{@code byte[]} 或字节列表，统一为 Script。 */
    private static Script asScript(Object unlockingScript) {
        if (unlockingScript instanceof Script script) {
            return script;
        }
        if (unlockingScript instanceof byte[] bytes) {
            return Script.fromBinary(bytes);
        }
        if (unlockingScript instanceof java.util.List<?> list) {
            byte[] bytes = new byte[list.size()];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) ((Number) list.get(i)).intValue();
            }
            return Script.fromBinary(bytes);
        }
        return new Script();
    }

    /** 取解锁脚本中第一个推送数据（DER 签名 + 范围尾字节）的最后一个字节。 */
    private static int signatureHashType(TransactionInput input) {
        var chunks = asScript(input.unlockingScript).getChunks();
        assertTrue(chunks.size() >= 1, "解锁脚本应至少含一个推送");
        byte[] signature = ScriptHelper.getPushData(chunks.get(0));
        assertNotNull(signature, "解锁脚本首项应为签名");
        return signature[signature.length - 1] & 0xFF;
    }

    private static long satoshisOf(TransactionOutput output) {
        return output.satoshis instanceof Number number ? number.longValue() : 0L;
    }

    private static BsvAmount inputSum(Transaction transaction) {
        BsvAmount sum = BsvAmount.ZERO;
        for (TransactionInput input : transaction.inputs) {
            if (input.sourceTransaction instanceof Transaction source && source.outputs != null
                    && !source.outputs.isEmpty()) {
                sum = sum.add(BsvAmount.valueOf(satoshisOf(source.outputs.get(0))));
            }
        }
        return sum;
    }

    private static BsvAmount outputSum(Transaction transaction) {
        BsvAmount sum = BsvAmount.ZERO;
        for (TransactionOutput output : transaction.outputs) {
            sum = sum.add(BsvAmount.valueOf(satoshisOf(output)));
        }
        return sum;
    }
}
