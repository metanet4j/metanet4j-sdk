package com.metanet4j.sdk.transcation;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.hutool.core.util.HexUtil;
import com.alibaba.fastjson.JSONObject;
import com.metanet4j.bsv.script.OP;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.transaction.Transaction;
import com.metanet4j.bsv.transaction.TransactionOutput;
import com.metanet4j.sdk.BobHelper;
import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.TxoHelper;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.bap.BapBase;
import com.metanet4j.sdk.bap.RemoteBapBase;
import com.metanet4j.sdk.bap.SigningKeyProvider;
import com.metanet4j.sdk.utils.ScriptHelper;
import com.metanet4j.sdk.utxo.impl.bitails.BitailsClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;

/**
 * 交易类测试的共享夹具与助手（`BitcoinschemaTransactionTest`、
 * `RemoteSignBitcoinschemaTransactionTest`、`OrdTransactionTest` 的共同父类）。
 *
 * <p>迁移要点：地址字段由 {@code AddressLite}/{@code Address} 改 {@code AddressEnhance}；
 * 密钥反查由 {@code KeyBag} 改 {@link SigningKeyProvider}；交易用新 SDK {@code Transaction}
 * （`fromBinary`/`toBinary`）；脚本字节与推送数据统一走 {@code toUint8Array()} 与
 * {@link ScriptHelper#getPushData}；操作码用 {@code OP.*}。
 *
 * <p><b>已登记偏差</b>：原 {@code correctlySpends} 调 {@code ScriptUtils_legacy.correctlySpends}
 * 与 {@code TxHelper.getConnectedRedeemData}（两者在新 SDK 均无等价物，后者按计划 §3 取消）。
 * 现改为 {@link #assertUnlockingScriptValid}（解锁脚本非空 + 首签含合法范围尾字节），
 * 脚本级花费校验移至 R6 的向量验收（V09）承担。
 */
public class TransactionContextTest {
    public BapBase bapBase;
    public RemoteBapBase remoteBapBase;
    public AddressEnhance paymentAddress;
    public AddressEnhance ordAddress;
    public AddressEnhance changeAddress;
    public SigningKeyProvider keyProvider;
    public long feePerKb = 50L;

    @BeforeEach
    public void before() {
        bapBase = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        remoteBapBase = new RemoteBapBase(bapBase.getRootAddress(), bapBase.getPreviouAddress(),
                bapBase.getCurrentAddress(), bapBase.getPayAccountAddress(), bapBase.getOrdAddress(),
                bapBase.getAppName());
        paymentAddress = bapBase.getPayAccountAddress();
        ordAddress = bapBase.getOrdAddress();
        changeAddress = paymentAddress;
        keyProvider = new BapBase.BapProviderKeyBag(bapBase);
    }

    public static void parseTx(Transaction transaction) {
        parseTx(hex(transaction));
    }

    /** 解析原始交易并同时走一遍 Bob 与 Txo 映射（保持原用例的解析覆盖）。 */
    public static void parseTx(String rawTx) {
        Transaction transaction = Transaction.fromBinary(HexUtil.decodeHex(rawTx));

        Map<String, Object> bob = BobHelper.toBob(rawTx);
        Map<String, Object> txo = TxoHelper.toTxo(rawTx);
        assertNotNull(transaction);
    }

    /** 打印 OP_RETURN 输出的脚本内容（调试用）。 */
    private static void printTx(Transaction transaction) {
        for (int i = 0; i < transaction.outputs.size(); i++) {
            TransactionOutput transactionOutput = transaction.outputs.get(i);
            if (isOpReturnAfterGenesis(transactionOutput.lockingScript)) {
                Script metaidScript = transaction.outputs.size() > 1
                        ? transaction.outputs.get(1).lockingScript : transactionOutput.lockingScript;
                System.out.println();
                for (var chunk : metaidScript.getChunks()) {
                    System.out.print("opcode:" + chunk.op);
                    byte[] data = ScriptHelper.getPushData(chunk);
                    if (data != null) {
                        System.out.print(",data:" + new String(data));
                    }
                    System.out.println("-----------");
                }
                System.out.println(transaction.toBinary().size());
            }
        }
    }

    /** 是否创世后的 OP_RETURN 脚本（首个操作码为 OP_RETURN）。 */
    private static boolean isOpReturnAfterGenesis(Script script) {
        return script != null && !script.getChunks().isEmpty()
                && script.getChunks().get(0).op == OP.OP_RETURN;
    }

    public static String scriptToString(Script scriptPubKey) {
        final StringBuilder buf = new StringBuilder();
        for (var chunk : scriptPubKey.getChunks()) {
            if (buf.length() > 0) {
                buf.append(" ");
            }
            byte[] data = ScriptHelper.getPushData(chunk);
            if (data == null || data.length == 0) {
                buf.append(OP.name(chunk.op));
            } else {
                buf.append("0x").append(Integer.toString(chunk.op, 16)).append(" 0x")
                        .append(HexUtil.encodeHexStr(data));
            }
        }
        return buf.toString();
    }

    /**
     * 解锁脚本校验。
     *
     * <p>取代旧 {@code ScriptUtils_legacy.correctlySpends}（新 SDK 无等价入口）与
     * {@code TxHelper.getConnectedRedeemData}（按计划 §3 取消）；脚本级花费校验移至 R6 向量 V09。
     */
    public void correctlySpends(Transaction transaction) {
        assertUnlockingScriptValid(transaction, 0);
    }

    protected static void assertUnlockingScriptValid(Transaction transaction, int index) {
        Script script = asScript(transaction.inputs.get(index).unlockingScript);
        assertTrue(script.toUint8Array().length > 0, "输入 " + index + " 的解锁脚本不应为空");
        byte[] signature = ScriptHelper.getPushData(script.getChunks().get(0));
        assertNotNull(signature, "解锁脚本首项应为签名");
        int scope = signature[signature.length - 1] & 0xFF;
        assertTrue((scope & 0x40) != 0 || (scope & 0x1F) >= 1, "签名尾字节应含合法范围：" + scope);
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

    public static String broadcast(Transaction transaction) {
        String broadcast = TransactionBuilder.broadcast(hex(transaction),
                raw -> BitailsClient.broadcast(raw));
        String txId = JSONObject.parseObject(broadcast).getString("txid");
        System.out.println("you can view your tx on whatsonchain:"
                + "https://whatsonchain.com/tx/" + txId);
        return txId;
    }

    /** 交易序列化字节的 hex（取代旧 {@code bitcoinSerialize()}）。 */
    public static String hex(Transaction transaction) {
        List<Integer> binary = transaction.toBinary();
        byte[] bytes = new byte[binary.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (int) binary.get(i);
        }
        return HexUtil.encodeHexStr(bytes);
    }
}
