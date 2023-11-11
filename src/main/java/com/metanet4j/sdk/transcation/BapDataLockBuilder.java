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
        if (this.remoteSign) {
            if (this.remoteSignType == null) {
                this.remoteSignType = RemoteSignType.ROOT;
            }

        } else {
            if (this.signType == null) {
                this.signType = SignType.ROOT;
            }
        }
        return this;
    }


    public BapDataLockBuilder buildId() {

        List<ByteBuffer> byteBuffers = new ArrayList<>();
        byteBuffers.add(ByteBuffer.wrap(ProtocolConstant.BAP_PROTOCOL.getBytes(Charsets.UTF_8)));
        byteBuffers.add(ByteBuffer.wrap(BapTypeEnum.ID.getDesc().getBytes(Charsets.UTF_8)));
        byteBuffers.add(ByteBuffer.wrap(bapBase.getIdentityKey().getBytes(Charsets.UTF_8)));
        byteBuffers.add(ByteBuffer.wrap(bapBase.getCurrentAddress().toBase58().getBytes(Charsets.UTF_8)));
        this.dataList = byteBuffers;
        if (this.remoteSign) {
            if (this.remoteSignType == null) {
                this.remoteSignType = RemoteSignType.PREVIOUS;
            }

        } else {
            if (this.signType == null) {
                this.signType = SignType.PREVIOUS;
            }
        }
        return this;
    }


    public BapDataLockBuilder buildAlias(String identity) {

        List<ByteBuffer> byteBuffers = new ArrayList<>();
        byteBuffers.add(ByteBuffer.wrap(ProtocolConstant.BAP_PROTOCOL.getBytes(Charsets.UTF_8)));
        byteBuffers.add(ByteBuffer.wrap(BapTypeEnum.ALIAS.getDesc().getBytes(Charsets.UTF_8)));
        byteBuffers.add(ByteBuffer.wrap(bapBase.getIdentityKey().getBytes(Charsets.UTF_8)));
        byteBuffers.add(ByteBuffer.wrap(identity.getBytes(Charsets.UTF_8)));
        this.dataList = byteBuffers;
        if (this.remoteSign) {
            if (this.remoteSignType == null) {
                this.remoteSignType = RemoteSignType.CURRENT;
            }

        } else {
            if (this.signType == null) {
                this.signType = SignType.CURRENT;
            }
        }
        return this;
    }








}
