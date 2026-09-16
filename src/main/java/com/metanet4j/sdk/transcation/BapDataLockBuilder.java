package com.metanet4j.sdk.transcation;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.metanet4j.base.constants.ProtocolConstant;
import com.metanet4j.base.type.BapTypeEnum;
import com.metanet4j.sdk.SignType;
import com.metanet4j.sdk.bap.BapBaseCore;
import com.metanet4j.sdk.remote.RemoteSignType;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class BapDataLockBuilder extends UnSpendableDataLockBuilder<BapDataLockBuilder> {


    private BapBaseCore bapBase;


    public BapDataLockBuilder(BapBaseCore bapBase) {
        super(Lists.newArrayList(), bapBase);
        this.bapBase = bapBase;

    }

    public BapDataLockBuilder(BapBaseCore bapBase, boolean remoteSign, RemoteSignType remoteSignType) {
        this(Lists.newArrayList(), bapBase, remoteSign, remoteSignType);
    }

    public BapDataLockBuilder(List<ByteBuffer> buffers, BapBaseCore bapBase) {
        super(buffers, bapBase);
        this.bapBase = bapBase;

    }

    public BapDataLockBuilder(List<ByteBuffer> buffers, BapBaseCore bapBase, SignType signType) {
        super(buffers, bapBase);
        this.bapBase = bapBase;
        this.signType = signType;

    }


    public BapDataLockBuilder(List<ByteBuffer> buffers, BapBaseCore bapBase, boolean remoteSign, RemoteSignType remoteSignType) {
        super(buffers, bapBase, remoteSign, remoteSignType);
        this.bapBase = bapBase;
    }

    public BapDataLockBuilder buildRoot() {

        List<ByteBuffer> byteBuffers = new ArrayList<>();
        byteBuffers.add(ByteBuffer.wrap(ProtocolConstant.BAP_PROTOCOL.getBytes(Charsets.UTF_8)));
        byteBuffers.add(ByteBuffer.wrap(BapTypeEnum.ID.getDesc().getBytes(Charsets.UTF_8)));
        byteBuffers.add(ByteBuffer.wrap(bapBase.getIdentityKey().getBytes(Charsets.UTF_8)));
        byteBuffers.add(ByteBuffer.wrap(bapBase.getRootAddress().getBytes(Charsets.UTF_8)));
        this.dataList = byteBuffers;
        initSignType(RemoteSignType.ROOT, SignType.ROOT);
        return this;
    }


    public BapDataLockBuilder buildId() {

        List<ByteBuffer> byteBuffers = new ArrayList<>();
        byteBuffers.add(ByteBuffer.wrap(ProtocolConstant.BAP_PROTOCOL.getBytes(Charsets.UTF_8)));
        byteBuffers.add(ByteBuffer.wrap(BapTypeEnum.ID.getDesc().getBytes(Charsets.UTF_8)));
        byteBuffers.add(ByteBuffer.wrap(bapBase.getIdentityKey().getBytes(Charsets.UTF_8)));
        byteBuffers.add(ByteBuffer.wrap(bapBase.getCurrentAddress().toBase58().getBytes(Charsets.UTF_8)));
        this.dataList = byteBuffers;
        initSignType(RemoteSignType.PREVIOUS, SignType.PREVIOUS);
        return this;
    }


    public BapDataLockBuilder buildAlias(String identity) {

        List<ByteBuffer> byteBuffers = new ArrayList<>();
        byteBuffers.add(ByteBuffer.wrap(ProtocolConstant.BAP_PROTOCOL.getBytes(Charsets.UTF_8)));
        byteBuffers.add(ByteBuffer.wrap(BapTypeEnum.ALIAS.getDesc().getBytes(Charsets.UTF_8)));
        byteBuffers.add(ByteBuffer.wrap(bapBase.getIdentityKey().getBytes(Charsets.UTF_8)));
        byteBuffers.add(ByteBuffer.wrap(identity.getBytes(Charsets.UTF_8)));
        this.dataList = byteBuffers;
        initSignType(RemoteSignType.CURRENT, SignType.CURRENT);
        return this;
    }

    private void initSignType(RemoteSignType remoteSignType, SignType signType) {
        if (this.remoteSign) {
            if (this.remoteSignType == null) {
                this.remoteSignType = remoteSignType;
            }

        } else {
            // 直接写入：buildRoot/buildId/buildAlias 自身最了解该交易该用哪种签名类型（ROOT/PREVIOUS/CURRENT）。
            // 原实现用 `this.signType == null` 判断"未显式指定"，但父类已把该字段初始化为 CURRENT（非 null），
            // 判断恒为假 → ROOT/PREVIOUS 永不生效，导致 BAP root 交易用 CURRENT 地址签名，
            // BapHelper.isRootBap 判 false、解析器认不出 root（2026-09-16 修复，见验收报告 §5 遗留项 1）。
            this.signType = signType;
        }
    }


}
