package com.metanet4j.sdk.transcation;


import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.HexUtil;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.metanet4j.base.aip.AipHelper;
import com.metanet4j.base.constants.ProtocolConstant;
import com.metanet4j.sdk.PrivateKey;
import com.metanet4j.sdk.SignType;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.bap.BapBaseCore;
import com.metanet4j.sdk.context.PreSignHashContext;
import com.metanet4j.sdk.remote.PreSignHashUtils;
import com.metanet4j.sdk.remote.RemoteSignType;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import io.bitcoinsv.bitcoinjsv.core.Utils;
import io.bitcoinsv.bitcoinjsv.script.Script;
import io.bitcoinsv.bitcoinjsv.script.ScriptBuilder;
import io.bitcoinsv.bitcoinjsv.script.ScriptChunk;
import io.bitcoinsv.bitcoinjsv.script.ScriptOpCodes;
import org.spongycastle.util.encoders.Base64;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;

public abstract class UnSpendableDataLockBuilder<T extends UnSpendableDataLockBuilder> implements LockingScriptBuilder {

    protected List<ByteBuffer> dataList;
    protected BapBaseCore bapBase;
    protected boolean remoteSign;
    protected SignType signType = SignType.CURRENT;
    protected RemoteSignType remoteSignType;
    private boolean isHaveSign;

    public UnSpendableDataLockBuilder(List<ByteBuffer> buffers, BapBaseCore bapBase) {
        this.bapBase = bapBase;
        this.dataList = buffers;

    }

    public UnSpendableDataLockBuilder(List<ByteBuffer> buffers, BapBaseCore bapBase, SignType signType) {
        this.bapBase = bapBase;
        this.dataList = buffers;
        if (signType == null) {
            this.signType = SignType.CURRENT;
        } else {
            this.signType = signType;
        }


    }

    public UnSpendableDataLockBuilder(List<ByteBuffer> buffers, BapBaseCore bapBase, boolean remoteSign,
                                      RemoteSignType remoteSignType) {
        this.remoteSign = remoteSign;
        this.bapBase = bapBase;
        this.dataList = buffers;
        this.remoteSignType = remoteSignType;

    }


    @Override
    public Script getLockingScript() {
        ScriptBuilder builder = new ScriptBuilder();
        builder.addChunk(new ScriptChunk(ScriptOpCodes.OP_FALSE, null));
        builder.op(ScriptOpCodes.OP_RETURN);
        for (ByteBuffer buffer : dataList) {
            builder.data(buffer.array());
        }
        return builder.build();

    }

    private T addSig() {
        List<ByteBuffer> signingBuffers = Lists.newArrayList(this.dataList);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        outputStream.writeBytes(HexUtil.decodeHex("6a"));
        signingBuffers.stream().forEach(o -> outputStream.writeBytes(o.array()));
        outputStream.writeBytes(HexUtil.decodeHex("7c"));

        this.dataList.add(ByteBuffer.wrap("|".getBytes(Charsets.UTF_8)));
        this.dataList.add(ByteBuffer.wrap(ProtocolConstant.AIP_PROTOCOL.getBytes(Charsets.UTF_8)));
        this.dataList.add(ByteBuffer.wrap("BITCOIN_ECDSA".getBytes(Charsets.UTF_8)));
        byte[] data = Utils.formatMessageForSigning(outputStream.toString(Charsets.UTF_8));
        Sha256Hash hash = Sha256Hash.twiceOf(data);
        PreSignHashContext unspendableDataSig = getSig(hash);
        this.dataList.add(ByteBuffer.wrap(unspendableDataSig.getSignAddress().getBytes(Charsets.UTF_8)));
        this.dataList.add(ByteBuffer.wrap(Base64.decode(unspendableDataSig.getSigB64().getBytes(Charsets.UTF_8))));

        boolean b = AipHelper.verifySign(unspendableDataSig.getSignAddress(), unspendableDataSig.getSigB64(),
                outputStream.toString(Charsets.UTF_8));
        if (!b) {
            throw new RuntimeException("aip sig verify failed,the sig is:" + unspendableDataSig.getSigB64() + ",the address is:"
                    + unspendableDataSig.getSignAddress());
        }
        return (T) this;
    }


    private T signOpReturnWithAIP(PrivateKey privateKey, AddressEnhance addressEnhance) {
        Assert.notNull(privateKey, "sign privateKey not allow null when signOpReturnWithAIP");
        Assert.notNull(addressEnhance, "sign address not allow null when signOpReturnWithAIP");
        List<ByteBuffer> signingBuffers = Lists.newArrayList(this.dataList);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        outputStream.writeBytes(HexUtil.decodeHex("6a"));
        signingBuffers.stream().forEach(o -> outputStream.writeBytes(o.array()));
        outputStream.writeBytes(HexUtil.decodeHex("7c"));

        this.dataList.add(ByteBuffer.wrap("|".getBytes(Charsets.UTF_8)));
        this.dataList.add(ByteBuffer.wrap(ProtocolConstant.AIP_PROTOCOL.getBytes(Charsets.UTF_8)));
        this.dataList.add(ByteBuffer.wrap("BITCOIN_ECDSA".getBytes(Charsets.UTF_8)));

        this.dataList.add(ByteBuffer.wrap(addressEnhance.toBase58().getBytes(Charsets.UTF_8)));
        String sig = privateKey.getKey().signMessage(outputStream.toString(Charsets.UTF_8));
        this.dataList.add(ByteBuffer.wrap(Base64.decode(sig.getBytes(Charsets.UTF_8))));

        return (T) this;
    }


    /**
     * @param bapBase
     * @param signType signType
     * @return
     */
    private T signOpReturnWithAIP(BapBaseCore bapBase, SignType signType) {
        Assert.notNull(bapBase, "sign bapBase not allow null when signOpReturnWithAIP");
        List<ByteBuffer> signingBuffers = Lists.newArrayList(this.dataList);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        outputStream.writeBytes(HexUtil.decodeHex("6a"));
        signingBuffers.stream().forEach(o -> outputStream.writeBytes(o.array()));
        outputStream.writeBytes(HexUtil.decodeHex("7c"));

        this.dataList.add(ByteBuffer.wrap("|".getBytes(Charsets.UTF_8)));
        this.dataList.add(ByteBuffer.wrap(ProtocolConstant.AIP_PROTOCOL.getBytes(Charsets.UTF_8)));
        this.dataList.add(ByteBuffer.wrap("BITCOIN_ECDSA".getBytes(Charsets.UTF_8)));

        if (signType == SignType.PREVIOUS) {
            Assert.notNull(bapBase.getPreviouAddress(),
                    "the SignType is PREVIOUS,so sign previous address not allow null when signOpReturnWithAIP");
            Assert.notNull(bapBase.getPreviousPrivateKey(),
                    "the SignType is PREVIOUS,so sign previous privateKey not allow null when signOpReturnWithAIP");
            this.dataList.add(ByteBuffer.wrap(bapBase.getPreviouAddress().toBase58().getBytes(Charsets.UTF_8)));
            String sig = bapBase.getPreviousPrivateKey().signMessage(outputStream.toString(Charsets.UTF_8));
            this.dataList.add(ByteBuffer.wrap(Base64.decode(sig.getBytes(Charsets.UTF_8))));

        } else if (signType == SignType.CURRENT) {
            Assert.notNull(bapBase.getCurrentAddress(),
                    "the SignType is CURRENT,so sign current address not allow null when signOpReturnWithAIP");
            Assert.notNull(bapBase.getCurrentPrivateKey(),
                    "the SignType is CURRENT,so sign current privateKey not allow null when signOpReturnWithAIP");
            this.dataList.add(ByteBuffer.wrap(bapBase.getCurrentAddress().toBase58().getBytes(Charsets.UTF_8)));
            String sig = bapBase.getCurrentPrivateKey().signMessage(outputStream.toString(Charsets.UTF_8));
            this.dataList.add(ByteBuffer.wrap(Base64.decode(sig.getBytes(Charsets.UTF_8))));
        } else if (signType == SignType.ROOT) {
            Assert.notNull(bapBase.getRootAddress(),
                    "the SignType is ROOT,so sign root address not allow null when signOpReturnWithAIP");
            Assert.notNull(bapBase.getRootPrivateKey(),
                    "the SignType is ROOT,so sign root privateKey not allow null when signOpReturnWithAIP");
            this.dataList.add(ByteBuffer.wrap(bapBase.getRootAddress().getBytes(Charsets.UTF_8)));
            String sig = bapBase.getRootPrivateKey().signMessage(outputStream.toString(Charsets.UTF_8));
            this.dataList.add(ByteBuffer.wrap(Base64.decode(sig.getBytes(Charsets.UTF_8))));
        }

        return (T) this;
    }

    @Override
    public PreSignHashContext getPreSignHashContext() {
        List<ByteBuffer> signingBuffers = Lists.newArrayList(this.dataList);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        outputStream.writeBytes(HexUtil.decodeHex("6a"));
        signingBuffers.stream().forEach(o -> outputStream.writeBytes(o.array()));
        outputStream.writeBytes(HexUtil.decodeHex("7c"));

        this.dataList.add(ByteBuffer.wrap("|".getBytes(Charsets.UTF_8)));
        this.dataList.add(ByteBuffer.wrap(ProtocolConstant.AIP_PROTOCOL.getBytes(Charsets.UTF_8)));
        this.dataList.add(ByteBuffer.wrap("BITCOIN_ECDSA".getBytes(Charsets.UTF_8)));
        byte[] data = Utils.formatMessageForSigning(outputStream.toString(Charsets.UTF_8));
        Sha256Hash hash = Sha256Hash.twiceOf(data);

        PreSignHashContext preSignHashContext = new PreSignHashContext();
        preSignHashContext.setPreSignHash(hash);
        preSignHashContext.setSignAddress(this.bapBase.getSignAddress(this.remoteSignType));
        preSignHashContext.setRemoteSignType(this.remoteSignType);
        return preSignHashContext;
    }

    @Override
    public boolean isHaveSign() {
        return this.isHaveSign;
    }

    public T sign() {
        this.isHaveSign = true;
        if (this.remoteSign) {
            Assert.notNull(this.remoteSignType, "SignType is REMOTE, the remoteSignType not allow null");
            return this.addSig();
        }

        return this.signOpReturnWithAIP(this.bapBase, this.signType);
    }

    public List<ByteBuffer> getDataList() {
        return dataList;
    }

    public BapBaseCore getBapBase() {
        return bapBase;
    }

    public boolean isRemoteSign() {
        return remoteSign;
    }

    public RemoteSignType getRemoteSignType() {
        return remoteSignType;
    }

    /**
     * 通过hash获取签名
     * 子类可以重写这个方法，自定义获取签名的方式
     *
     * @param hash
     * @return
     */
    public PreSignHashContext getSig(Sha256Hash hash) {
        return PreSignHashUtils.getSignedHashContext(hash);
    }


}
