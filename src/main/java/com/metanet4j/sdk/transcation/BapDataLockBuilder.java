package com.metanet4j.sdk.transcation;

import cn.hutool.core.lang.Assert;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.metanet4j.base.constants.ProtocolConstant;
import com.metanet4j.base.type.BapTypeEnum;
import com.metanet4j.sdk.RemoteSignType;
import com.metanet4j.sdk.SignType;
import com.metanet4j.sdk.bap.BapBaseCore;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class BapDataLockBuilder extends UnSpendableDataLockBuilder<BapDataLockBuilder> {


    private BapBaseCore bapBase;

    public BapDataLockBuilder(BapBaseCore bapBase) {
        super(Lists.newArrayList());
        this.bapBase = bapBase;

    }

    public BapDataLockBuilder(List<ByteBuffer> buffers, BapBaseCore bapBase) {
        super(buffers);
        this.bapBase = bapBase;

    }

    public BapDataLockBuilder buildRoot() {
        return this.buildRoot(false);

    }


    public BapDataLockBuilder buildRoot(boolean remote) {

        List<ByteBuffer> byteBuffers = new ArrayList<>();
        byteBuffers.add(ByteBuffer.wrap(ProtocolConstant.BAP_PROTOCOL.getBytes(Charsets.UTF_8)));
        byteBuffers.add(ByteBuffer.wrap(BapTypeEnum.ID.getDesc().getBytes(Charsets.UTF_8)));
        byteBuffers.add(ByteBuffer.wrap(bapBase.getIdentityKey().getBytes(Charsets.UTF_8)));
        byteBuffers.add(ByteBuffer.wrap(bapBase.getRootAddress().getBytes(Charsets.UTF_8)));
        this.dataList = byteBuffers;
        if (remote) {
            return this.sign(SignType.REMOTE);
        }
        return this.sign(SignType.ROOT);
    }

    public BapDataLockBuilder buildId() {
        return this.buildId(false);

    }

    public BapDataLockBuilder buildId(boolean remote) {

        List<ByteBuffer> byteBuffers = new ArrayList<>();
        byteBuffers.add(ByteBuffer.wrap(ProtocolConstant.BAP_PROTOCOL.getBytes(Charsets.UTF_8)));
        byteBuffers.add(ByteBuffer.wrap(BapTypeEnum.ID.getDesc().getBytes(Charsets.UTF_8)));
        byteBuffers.add(ByteBuffer.wrap(bapBase.getIdentityKey().getBytes(Charsets.UTF_8)));
        byteBuffers.add(ByteBuffer.wrap(bapBase.getCurrentAddress().toBase58().getBytes(Charsets.UTF_8)));
        this.dataList = byteBuffers;
        if (remote) {
            return this.sign(SignType.REMOTE);
        }
        return this.sign(SignType.PREVIOUS);
    }

    public BapDataLockBuilder buildAlias(String identity) {
        return this.buildAlias(identity, false);
    }


    public BapDataLockBuilder buildAlias(String identity, boolean remote) {

        List<ByteBuffer> byteBuffers = new ArrayList<>();
        byteBuffers.add(ByteBuffer.wrap(ProtocolConstant.BAP_PROTOCOL.getBytes(Charsets.UTF_8)));
        byteBuffers.add(ByteBuffer.wrap(BapTypeEnum.ALIAS.getDesc().getBytes(Charsets.UTF_8)));
        byteBuffers.add(ByteBuffer.wrap(bapBase.getIdentityKey().getBytes(Charsets.UTF_8)));
        byteBuffers.add(ByteBuffer.wrap(identity.getBytes(Charsets.UTF_8)));
        this.dataList = byteBuffers;
        if (remote) {
            return this.sign(SignType.REMOTE);
        }
        return this.sign(SignType.CURRENT);
    }

    public BapDataLockBuilder sign(SignType signType) {
        return sign(signType, null);
    }

    public BapDataLockBuilder sign(SignType signType, RemoteSignType remoteSignType) {
        if (signType == SignType.REMOTE) {
            Assert.notNull(remoteSignType, "SignType is REMOTE, the remoteSignType not allow null");
            return this.addSig(remoteSignType);
        }
        return this.signOpReturnWithAIP(this.bapBase, signType);
    }


    @Override
    protected UnspendableDataSig getSig(Sha256Hash hash, RemoteSignType remoteSignType) {
        return null;
    }
}
