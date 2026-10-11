package com.metanet4j.sdk.input;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.metanet4j.base.bsv.BsvHashBytes;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.script.templates.P2PKH;
import com.metanet4j.sdk.PrivateKey;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.output.TransactionOutputEnhance;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 两个「持有者」类的契约（按计划 §13.1 改写后）。
 *
 * <p><b>覆盖迁移说明</b>：原用例还构造并断言了 {@code TransactionInputEnhance}
 * （旧库 {@code TransactionInput} 的子类，构造时把 scriptBytes 置空）。
 * 该类按 §13.1 第 451 行**已被删除**——新 SDK 的 {@code TransactionInput.unlockingScript}
 * 默认即空（哨兵 {@code Undefined}），不存在"需要子类置空"的场景，
 * 原断言「input.getScriptBytes() 为空」在新架构下由 SDK 默认值保证，**非迁移遗漏**。
 */
class TransactionEnhanceContractTest {

    @Test
    void preservesExplicitOutpointHashAndConnectedAddressOutput() {
        AddressEnhance recipient = AddressEnhance.fromPrivateKey(PrivateKey.of(BigInteger.TWO));
        Script lockingScript = new P2PKH().lock(recipient.toBase58());
        TransactionOutputEnhance output = new TransactionOutputEnhance(lockingScript, 546d, 3);

        String knownTxid = new BsvHashBytes(new byte[32]).toString();
        // 持有者的 connectedOutput 用 SDK 的 TransactionOutput（生产侧实际持有的类型）
        com.metanet4j.bsv.transaction.TransactionOutput connected =
                new com.metanet4j.bsv.transaction.TransactionOutput(lockingScript, 546d);
        TransactionOutPointEnhance outpoint = new TransactionOutPointEnhance(knownTxid, 3, connected);

        assertEquals(3, output.getIndex());
        assertEquals(546d, output.getSatoshis());
        assertArrayEquals(lockingScript.toUint8Array(), output.getLockingScript().toUint8Array());

        assertEquals(knownTxid, outpoint.getSourceTXID(), "显式给定的来源交易号应原样保留");
        assertEquals(3, outpoint.getIndex());
        assertSame(connected, outpoint.getConnectedOutput(), "应保留连接输出");
    }

    @Test
    void keepsRawOutputScriptAndDetachedIndex() {
        Script lockingScript = new Script().writeBin(toBoxed(new byte[] {1, 2, 3}));
        TransactionOutputEnhance output = new TransactionOutputEnhance(lockingScript, 1d, 7);

        assertEquals(7, output.getIndex(), "下标独立于锁定脚本保存");
        assertArrayEquals(lockingScript.toUint8Array(), output.getLockingScript().toUint8Array());
    }

    private static List<Integer> toBoxed(byte[] data) {
        List<Integer> out = new ArrayList<>(data.length);
        for (byte b : data) {
            out.add(b & 0xFF);
        }
        return out;
    }
}
