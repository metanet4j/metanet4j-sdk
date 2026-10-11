package com.metanet4j.sdk.output;

import com.metanet4j.bsv.script.Script;

/**
 * 输出持有者（**不继承**，按计划 §13.1 改写）。
 *
 * <p>新 SDK 的 {@code TransactionOutput} **没有 {@code index} 字段**（下标即其在
 * {@code transaction.outputs} 列表中的位置），故本类改为纯持有者，承载
 * 「锁定脚本 + 金额 + 下标」三项；下标由调用方在构造时按下标取值给出。
 *
 * <p>金额用 {@code double} 与 SDK 的 {@code TransactionOutput.satoshis} 保持同一表示，
 * 避免无谓换算。
 */
public class TransactionOutputEnhance {

    private final Script lockingScript;
    private final double satoshis;
    private final int index;

    public TransactionOutputEnhance(Script lockingScript, double satoshis, int index) {
        this.lockingScript = lockingScript;
        this.satoshis = satoshis;
        this.index = index;
    }

    public Script getLockingScript() {
        return lockingScript;
    }

    public double getSatoshis() {
        return satoshis;
    }

    public int getIndex() {
        return index;
    }
}
