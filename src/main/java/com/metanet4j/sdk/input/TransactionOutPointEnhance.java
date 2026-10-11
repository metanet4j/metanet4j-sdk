package com.metanet4j.sdk.input;

import com.metanet4j.bsv.transaction.TransactionOutput;

/**
 * OutPoint 持有者（**不继承**，按计划 §13.1 改写）。
 *
 * <p>新 SDK 把 outpoint 拆成 {@code TransactionInput.sourceTXID} 与
 * {@code TransactionInput.sourceOutputIndex} 两个字段，旧库的 {@code TransactionOutPoint}
 * 抽象随之消失，故本类改为纯持有者，承载「来源交易号 + 输出下标 + 连接输出」三项，
 * 供调用方在需要成组传递 outpoint 信息时使用。
 *
 * <p>{@code sourceTXID} 为**显示序 hex**（与新 SDK 的字段约定一致）。
 */
public class TransactionOutPointEnhance {

    private final String sourceTXID;
    private final int index;
    private final TransactionOutput connectedOutput;

    public TransactionOutPointEnhance(String sourceTXID, int index, TransactionOutput connectedOutput) {
        this.sourceTXID = sourceTXID;
        this.index = index;
        this.connectedOutput = connectedOutput;
    }

    public String getSourceTXID() {
        return sourceTXID;
    }

    public int getIndex() {
        return index;
    }

    public TransactionOutput getConnectedOutput() {
        return connectedOutput;
    }
}
