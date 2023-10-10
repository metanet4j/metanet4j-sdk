package com.metanet4j.sdk.transcation;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.metanet4j.base.constants.ProtocolConstant;
import com.metanet4j.base.model.*;
import com.metanet4j.base.type.BsocialTypeEnum;
import com.metanet4j.sdk.RemoteSignType;
import com.metanet4j.sdk.SignType;
import com.metanet4j.sdk.bap.BapBaseCore;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class BsocialDataLockBuilder extends UnSpendableDataLockBuilder<BsocialDataLockBuilder> {
    private BapBaseCore bapBase;


    public BsocialDataLockBuilder(List<ByteBuffer> buffers, BapBaseCore bapBase) {
        super(buffers);
        this.bapBase = bapBase;
    }

    public BsocialDataLockBuilder(BapBaseCore bapBase) {
        super(Lists.newArrayList());
        this.bapBase = bapBase;
    }

    public BsocialDataLockBuilder buildTip(@Nonnull String txid,
                                           String currency, String amount) {
        ArrayList<String> mapDataList = Lists.newArrayList("context", "tx", "tx", txid);
        if (StrUtil.isNotEmpty(currency) && StrUtil.isNotEmpty(amount)) {
            mapDataList.addAll(Lists.newArrayList("currency", currency, "amount", amount));
        }
        return buildSet(Collections.emptyList(), BsocialTypeEnum.TIP, mapDataList);

    }


    public BsocialDataLockBuilder buildUnFollow(@Nonnull String identityKey) {

        ArrayList<String> mapDataList = Lists.newArrayList("idKey", identityKey);
        return buildSet(Collections.emptyList(), BsocialTypeEnum.UNFOLLOW, mapDataList);

    }

    public BsocialDataLockBuilder buildFollow(@Nonnull String identityKey) {
        ArrayList<String> mapDataList = Lists.newArrayList("idKey", identityKey);
        return buildSet(Collections.emptyList(), BsocialTypeEnum.FOLLOW, mapDataList);

    }


    public BsocialDataLockBuilder buildLike(@Nonnull String txId,
                                            @Nullable String emoji) {
        ArrayList<String> mapDataList = Lists.newArrayList("context", "tx", "tx", txId);
        if (StrUtil.isNotEmpty(emoji)) {
            mapDataList.add("emoji");
            mapDataList.add(emoji);
        }
        return buildSet(Collections.emptyList(), BsocialTypeEnum.LIKE, mapDataList);
    }

    public BsocialDataLockBuilder buildLike(@Nonnull String txId) {
        return buildLike(txId, null);
    }

    public BsocialDataLockBuilder buildUnLike(@Nonnull String txId
    ) {
        ArrayList<String> mapDataList = Lists.newArrayList("context", "tx", "tx", txId);
        return buildSet(Collections.emptyList(), BsocialTypeEnum.UNLIKE, mapDataList);
    }


    public BsocialDataLockBuilder buildReply(List<B> bList, @Nonnull String txId) {
        ArrayList<String> mapDataList = Lists.newArrayList("context", "tx", "tx", txId);
        return buildSet(bList, BsocialTypeEnum.REPLY, mapDataList);
    }


    public BsocialDataLockBuilder buildRepost(List<B> bList, @Nonnull String txId) {
        return buildSet(bList, BsocialTypeEnum.REPOST, Lists.newArrayList("tx", txId));
    }

    public BsocialDataLockBuilder buildPost(@Nonnull List<B> bList, List<String> extendList) {
        return buildSet(bList, BsocialTypeEnum.POST, extendList);
    }

    public BsocialDataLockBuilder buildPost(@Nonnull List<B> bList, List<String> extendList, List<String> addTags) {
        MAP setMap = new MAP();
        MAP addMap = new MAP();

        setMap.setBsocialTypeEnum(BsocialTypeEnum.POST);
        setMap.setMapDataList(extendList);

        addMap.setBsocialTypeEnum(BsocialTypeEnum.TAGS);
        addMap.setMapDataList(addTags);

        return buildSet(bList, Lists.newArrayList(setMap, addMap));
    }


    public BsocialDataLockBuilder buildPost(@Nonnull List<B> bList) {
        return buildSet(bList, BsocialTypeEnum.POST, Collections.emptyList());
    }


    public BsocialDataLockBuilder buildSet(@Nonnull List<B> bList,
                                           BsocialTypeEnum bsocialTypeEnum, List<String> extendList) {
        Bsocial bsocial = new Bsocial();
        if (CollectionUtil.isNotEmpty(bList)) {
            bsocial.setBList(bList);
        } else {
            bsocial.setBList(Collections.EMPTY_LIST);
        }

        MAP map = mSet(bsocialTypeEnum, extendList);
        bsocial.setMapList(Lists.newArrayList(map));
        return build(bsocial);
    }

    public BsocialDataLockBuilder buildSet(@Nonnull List<B> bList,
                                           List<MAP> mapList) {
        Bsocial bsocial = new Bsocial();
        if (CollectionUtil.isNotEmpty(bList)) {
            bsocial.setBList(bList);
        } else {
            bsocial.setBList(Collections.EMPTY_LIST);
        }
        List<MAP> newMapList = Lists.newArrayList();
        for (MAP map : mapList) {
            if (map.getBsocialTypeEnum() == BsocialTypeEnum.TAGS) {
                newMapList.add(mAdd(map.getBsocialTypeEnum(), map.getMapDataList()));
            } else {
                newMapList.add(mSet(map.getBsocialTypeEnum(), map.getMapDataList()));
            }
        }
        bsocial.setMapList(newMapList);
        return build(bsocial);
    }


    public BsocialDataLockBuilder build(Bsocial bsocial) {
        Assert.notEmpty(bsocial.getMapList());
        List<ByteBuffer> byteBuffers = this.dataList;

        List<SortAble> sortAbleList = new ArrayList<>();
        if (CollectionUtil.isNotEmpty(bsocial.getBList())) {
            sortAbleList.addAll(bsocial.getBList());
        }
        if (CollectionUtil.isNotEmpty(bsocial.getBppList())) {
            sortAbleList.addAll(bsocial.getBppList());
        }

        //sort by index
        List<SortAble> sortedList = sortAbleList.stream().sorted(Comparator.comparing(o -> o.getIndex()))
                .collect(Collectors.toList());

        for (SortAble item : sortedList) {
            Assert.isTrue(item.getIndex() > 0);

            if (item instanceof B) {
                B b = (B) item;
                Assert.notNull(b.getByteBuffer());
                Assert.notEmpty(b.getContentType());

                byteBuffers.add(ByteBuffer.wrap(ProtocolConstant.B_PROTOCOL.getBytes(Charsets.UTF_8)));
                if (b.getByteBuffer() != null) {
                    if (b.isEncrypted()) {
                        if (this.bapBase.getEncryptKey() != null) {
                            byteBuffers.add(ByteBuffer.wrap(this.bapBase.encrypt(b.getByteBuffer().array())));
                        } else {
                            byteBuffers.add(b.getByteBuffer());
                        }

                    } else {
                        byteBuffers.add(b.getByteBuffer());
                    }

                }
                if (b.isEncrypted()) {
                    b.setContentType("application/bitcoin-ecies; content-type=" + b.getContentType());
                }
                byteBuffers.add(ByteBuffer.wrap(b.getContentType().getBytes(Charsets.UTF_8)));
                if (StrUtil.isNotEmpty(b.getEncoding())) {
                    byteBuffers.add(ByteBuffer.wrap(b.getEncoding().getBytes(Charsets.UTF_8)));
                }
                byteBuffers.add(ByteBuffer.wrap("|".getBytes(Charsets.UTF_8)));

            } else if (item instanceof BPP) {
                BPP bpp = (BPP) item;
                byteBuffers.add(ByteBuffer.wrap(ProtocolConstant.BPP_PROTOCOL.getBytes(Charsets.UTF_8)));
                byteBuffers.add(ByteBuffer.wrap(bpp.getAction().getBytes(Charsets.UTF_8)));
                byteBuffers.add(ByteBuffer.wrap(bpp.getCurrency().getBytes(Charsets.UTF_8)));
                byteBuffers.add(ByteBuffer.wrap(bpp.getAddress().getBytes(Charsets.UTF_8)));
                byteBuffers.add(ByteBuffer.wrap(bpp.getApiEndpoint().getBytes(Charsets.UTF_8)));
                byteBuffers.add(ByteBuffer.wrap("|".getBytes(Charsets.UTF_8)));
            }

        }

        if (bsocial.getMapList().size() == 1) {
            MAP map = bsocial.getMapList().stream().findFirst().orElse(null);
            Assert.notNull(map.getBsocialTypeEnum());
            Assert.notEmpty(map.getMapDataList());
            byteBuffers.add(ByteBuffer.wrap(ProtocolConstant.MAP_PROTOCOL.getBytes(Charsets.UTF_8)));
            for (String item : map.getMapDataList()) {
                byteBuffers.add(ByteBuffer.wrap(item.getBytes(Charsets.UTF_8)));
            }
        } else if (bsocial.getMapList().size() > 1) {

            for (int i = 0; i < bsocial.getMapList().size(); i++) {

                byteBuffers.add(ByteBuffer.wrap(ProtocolConstant.MAP_PROTOCOL.getBytes(Charsets.UTF_8)));
                for (String item : bsocial.getMapList().get(i).getMapDataList()) {
                    byteBuffers.add(ByteBuffer.wrap(item.getBytes(Charsets.UTF_8)));
                }
                if (i < bsocial.getMapList().size() - 1) {
                    byteBuffers.add(ByteBuffer.wrap("|".getBytes(Charsets.UTF_8)));
                }
            }
        }

        this.dataList = byteBuffers;
        return this;
    }

    public BsocialDataLockBuilder sign() {
        return sign(SignType.CURRENT, null);
    }

    public BsocialDataLockBuilder sign(SignType signType, RemoteSignType remoteSignType) {
        if (signType == SignType.REMOTE) {
            Assert.notNull(remoteSignType, "signType is REMOTE, the remoteSignType not allow null");
            return this.addSig(remoteSignType);
        } else {
            return this.signOpReturnWithAIP(this.bapBase, signType);
        }

    }


    private MAP mAdd(BsocialTypeEnum bsocialTypeEnum, @Nonnull List<String> extendList) {
        Assert.notNull(extendList, "ADD extendList not allow null");
        MAP map = new MAP();
        ArrayList<String> mapDataList = Lists
                .newArrayList("ADD", bsocialTypeEnum.getDesc());
        if (CollectionUtil.isNotEmpty(extendList)) {
            mapDataList.addAll(extendList);
        }
        map.setMapDataList(mapDataList);
        map.setBsocialTypeEnum(bsocialTypeEnum);
        return map;
    }

    private MAP mSet(BsocialTypeEnum bsocialTypeEnum, List<String> extendList) {
        MAP map = new MAP();
        ArrayList<String> mapDataList = Lists
                .newArrayList("SET", "app", this.bapBase.getAppName(), "type", bsocialTypeEnum.getDesc());
        if (CollectionUtil.isNotEmpty(extendList)) {
            mapDataList.addAll(extendList);
        }
        map.setMapDataList(mapDataList);
        map.setBsocialTypeEnum(bsocialTypeEnum);
        return map;
    }

    @Override
    protected UnspendableDataSig getSig(Sha256Hash hash, RemoteSignType remoteSignType) {
        return null;
    }
}
