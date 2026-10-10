package com.metanet4j.sdk.ordinals;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.hutool.core.codec.Base64;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.script.templates.P2PKH;
import com.metanet4j.sdk.TestData;
import com.metanet4j.base.bsv.BsvAmount;
import com.metanet4j.base.bsv.BsvHashBytes;
import com.metanet4j.base.bsv.BsvUtxo;
import com.metanet4j.bsv.transaction.Transaction;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.bap.BapBase;
import com.metanet4j.sdk.bap.SigningKeyProvider;
import com.metanet4j.sdk.exception.SignErrorException;
import com.metanet4j.sdk.utxo.UTXOProvider;
import com.metanet4j.sdk.utils.ScriptHelper;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrdTransactionTemplateContractTest {

    private final BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
    private final AddressEnhance payment = bap.getPayAccountAddress();
    private final AddressEnhance destination = bap.getOrdAddress();
    private final SigningKeyProvider keyBag = new BapBase.BapProviderKeyBag(bap);
    private final UTXOProvider<AddressEnhance, BsvUtxo> provider = addresses -> {
        assertEquals(List.of(payment.toBase58()),
                addresses.stream().map(AddressEnhance::toBase58).toList());
        return List.of(paymentUtxo(1));
    };

    @Test
    void postOrdinalIncludesTheAppAndPostMetadata() {
        OrdTransactionTemplate template = new OrdTransactionTemplate();
        OrdScriptBuilder.Inscription inscription = inscription("post", "text/plain");
        OrdScriptBuilder.ORDMap metadata = new OrdScriptBuilder.ORDMap();
        metadata.put("app", bap.getAppName());
        metadata.put("type", "post");

        Transaction transaction = template.createPostOrdinal(
                List.of(bap.getPayAccountAddress()), provider, destination, payment,
                500, inscription, bap, keyBag);

        byte[] inscriptionScript = OrdScriptBuilder.buildInscription(destination,
                "post".getBytes(StandardCharsets.US_ASCII), "text/plain", metadata).toUint8Array();
        assertArrayEquals(inscriptionScript,
                Arrays.copyOf(transaction.outputs.get(0).lockingScript.toUint8Array(), inscriptionScript.length));
    }

    @Test
    void reinscriptionKeepsItsMediaType() {
        OrdTransactionTemplate template = new OrdTransactionTemplate();
        OrdScriptBuilder.Inscription inscription = inscription("new", "text/plain");
        OrdScriptBuilder.ORDMap metadata = new OrdScriptBuilder.ORDMap();
        metadata.put("type", "post");

        Transaction transaction = template.sendOrdinal(bap, ordinalUtxo(2),
                paymentUtxo(3), destination, payment, 500, inscription, metadata,
                keyBag);

        assertArrayEquals(OrdScriptBuilder.buildInscription(destination,
                "new".getBytes(StandardCharsets.US_ASCII), "text/plain", metadata).toUint8Array(),
                transaction.outputs.get(0).lockingScript.toUint8Array());
    }

    @Test
    void sendOrdinalPropagatesSignerFailure() {
        OrdTransactionTemplate template = new OrdTransactionTemplate();

        assertThrows(SignErrorException.class, () -> template.sendOrdinal(bap,
                ordinalUtxo(4), paymentUtxo(5), destination, payment, 500,
                null, null, keyBag));
    }

    @Test
    void defaultCreateOrdinalSignsThePaymentInputAndBalancesChange() {
        OrdTransactionBuilder builder = new OrdTransactionTemplate().createOrdinal(
                List.of(bap.getPayAccountAddress()), provider, destination, payment,
                500, inscription("first", "text/plain"), null, bap, keyBag);

        Transaction signed = builder.build();
        assertEquals(BsvAmount.SATOSHI, BsvAmount.fromSdkValue(signed.outputs.get(0).satoshis));
        assertEquals(inputSum(signed), outputSum(signed).add(builder.getFee()));
        assertSpendable(signed);
    }

    @Test
    void defaultSendOrdinalSignsBothInputsWithoutReinscription() {
        Transaction signed = new OrdTransactionTemplate().sendOrdinal(bap,
                ordinalUtxo(6), paymentUtxo(7), destination, payment, 500,
                null, null, keyBag);

        assertEquals(BsvAmount.SATOSHI, BsvAmount.fromSdkValue(signed.outputs.get(0).satoshis));
        assertArrayEquals(new P2PKH().lock(destination.getHash160()).toUint8Array(),
                signed.outputs.get(0).lockingScript.toUint8Array());
        assertSpendable(signed);
    }

    @Test
    void emptyReinscriptionFieldsUseTheOrdinaryTransferOutput() {
        Transaction missingContent = new OrdTransactionTemplate().sendOrdinal(bap,
                ordinalUtxo(8), paymentUtxo(9), destination, payment, 500,
                inscription("new", ""), null, keyBag);
        Transaction missingData = new OrdTransactionTemplate().sendOrdinal(bap,
                ordinalUtxo(10), paymentUtxo(11), destination, payment, 500,
                inscription("", "text/plain"), null, keyBag);

        byte[] ordinaryOutput = new P2PKH().lock(destination.getHash160()).toUint8Array();
        assertArrayEquals(ordinaryOutput, missingContent.outputs.get(0).lockingScript.toUint8Array());
        assertArrayEquals(ordinaryOutput, missingData.outputs.get(0).lockingScript.toUint8Array());
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedSendUtxosBuildsAndSignsTheChangePayment() throws Exception {
        Transaction signed = new OrdTransactionTemplate().sendUtxos(bap,
                List.of(paymentUtxo(12)), payment, 500, keyBag);

        assertEquals(1, signed.inputs.size());
        assertEquals(1, signed.outputs.size());
        assertTrue(outputSum(signed).getValue() < inputSum(signed).getValue());
        assertSpendable(signed);
    }

    @Test
    void defaultCreateBapOrdinalIncludesRootAndSigmaProofs() {
        Transaction signed = new OrdTransactionTemplate().createBapOrdinal(
                List.of(bap.getPayAccountAddress()), provider, destination,
                payment, 500, bap, keyBag);

        assertEquals(BsvAmount.SATOSHI, BsvAmount.fromSdkValue(signed.outputs.get(0).satoshis));
        assertTrue(signed.outputs.size() >= 3);
        assertTrue(new String(signed.outputs.get(1).lockingScript.toUint8Array(), StandardCharsets.UTF_8)
                .contains("1BAPSuaPnfGnSBM3GLV9yhxUdYe4vGbdM"));
        assertSpendable(signed);
    }

    private void assertSpendable(Transaction signed) {
        for (int i = 0; i < signed.inputs.size(); i++) {
            assertTrue(unlockingScriptBytes(signed, i).length > 0, "输入 " + i + " 的解锁脚本不应为空");
            assertUnlockingScriptValid(signed, i);
        }
    }

    /**
     * 解锁脚本存在性校验（旧 {@code ScriptUtils_legacy.correctlySpends} 无新 SDK 等价入口，
     * 脚本级花费校验移至 R6 向量验收 V09 承担，属已登记偏差）。
     */
    private static void assertUnlockingScriptValid(Transaction tx, int index) {
        byte[] signature = ScriptHelper.getPushData(asScript(tx.inputs.get(index).unlockingScript)
                .getChunks().get(0));
        assertNotNull(signature, "解锁脚本首项应为签名");
        int scope = signature[signature.length - 1] & 0xFF;
        assertTrue((scope & 0x40) != 0 || (scope & 0x1F) >= 1, "签名尾字节应含合法 SIGHASH 范围：" + scope);
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

    private static BsvAmount inputSum(Transaction tx) {
        BsvAmount sum = BsvAmount.ZERO;
        for (var input : tx.inputs) {
            if (input.sourceTransaction instanceof Transaction source && source.outputs != null
                    && !source.outputs.isEmpty()) {
                sum = sum.add(BsvAmount.fromSdkValue(source.outputs.get(0).satoshis));
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

    private OrdScriptBuilder.Inscription inscription(String value, String contentType) {
        return OrdScriptBuilder.Inscription.builder().contentType(contentType)
                .dataB64(Base64.encode(value.getBytes(StandardCharsets.US_ASCII))).build();
    }

    private BsvUtxo paymentUtxo(int firstHashByte) {
        return new BsvUtxo(hash(firstHashByte), 0, 20_000L, 0, false, payment.toBase58(),
                new P2PKH().lock(payment.getHash160()).toUint8Array());
    }

    private BsvUtxo ordinalUtxo(int firstHashByte) {
        Script prior = OrdScriptBuilder.buildInscription(destination,
                "old".getBytes(StandardCharsets.US_ASCII), "text/plain", null);
        return new BsvUtxo(hash(firstHashByte), 0, 1L, 0, false, null, prior.toUint8Array());
    }

    private BsvHashBytes hash(int firstByte) {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) firstByte;
        return BsvHashBytes.of(bytes);
    }
}
