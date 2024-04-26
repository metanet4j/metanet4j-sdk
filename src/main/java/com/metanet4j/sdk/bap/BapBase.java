package com.metanet4j.sdk.bap;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.HexUtil;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.metanet4j.base.bap.BapHelper;
import com.metanet4j.sdk.PrivateKey;
import com.metanet4j.sdk.PublicKey;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.crypto.Ecies;
import com.metanet4j.sdk.crypto.MasterPrivateKey;
import io.bitcoinsv.bitcoinjsv.core.*;
import io.bitcoinsv.bitcoinjsv.crypto.ChildNumber;
import io.bitcoinsv.bitcoinjsv.crypto.DeterministicHierarchy;
import io.bitcoinsv.bitcoinjsv.crypto.DeterministicKey;
import io.bitcoinsv.bitcoinjsv.crypto.HDUtils;
import io.bitcoinsv.bitcoinjsv.params.MainNetParams;
import io.bitcoinsv.bitcoinjsv.temp.KeyBag;
import io.bitcoinsv.bitcoinjsv.temp.RedeemData;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Derive private key through HD to construct BapBase.
 */
public class BapBase extends BapBaseAbstract {

    private static final int defaultBapIdChildNumberSize = DefaultBapBaseConfig.BAP_CHILD_NUMBER_SIZE;
    @NotNull
    private BapBaseConfig bapBaseConfig;
    private MasterPrivateKey masterPrivateKey;

    private String rootPath;
    private DeterministicKey rootPrivateKey;
    private List<ChildNumber> rootChildNumberList;

    private String previousPath;
    private DeterministicKey previousPrivateKey;

    private String currentPath;
    private List<ChildNumber> currentNumberList;
    private DeterministicKey currentPrivateKey;

    public BapBase(MasterPrivateKey masterPrivateKey, List<ChildNumber> rootChildNumberList,
                   List<ChildNumber> currentNumberList, BapBaseConfig bapBaseConfig) {

        this.masterPrivateKey = masterPrivateKey;
        this.rootChildNumberList = rootChildNumberList;
        this.currentNumberList = currentNumberList;
        this.rootPath = HDUtils.formatPath(rootChildNumberList);
        this.currentPath = HDUtils.formatPath(currentNumberList);

        DeterministicHierarchy dh = new DeterministicHierarchy(masterPrivateKey.getMasterDeterministicKey());
        this.rootPrivateKey = dh.deriveChild(rootChildNumberList.subList(0, defaultBapIdChildNumberSize - 1), false, true,
                rootChildNumberList.get(defaultBapIdChildNumberSize - 1));
        this.currentPrivateKey = dh.deriveChild(currentNumberList.subList(0, defaultBapIdChildNumberSize - 1), false, true,
                currentNumberList.get(defaultBapIdChildNumberSize - 1));

        List<ChildNumber> previousChildNumbers = this.getPreviousChildNumbers();
        this.previousPrivateKey = dh.deriveChild(previousChildNumbers.subList(0, defaultBapIdChildNumberSize - 1), false, true,
                previousChildNumbers.get(defaultBapIdChildNumberSize - 1));

        this.identityKey = BapHelper.getIdentityKey(getRootAddress());
        this.bapBaseConfig = bapBaseConfig;

    }


    public static BapBase fromOnlyMasterPrivateKey(MasterPrivateKey masterPrivateKey) {
        return fromOnlyMasterPrivateKey(masterPrivateKey, new DefaultBapBaseConfig());
    }

    public static BapBase fromOnlyMasterPrivateKey(MasterPrivateKey masterPrivateKey, BapBaseConfig bapBaseConfig) {

        return fromRootChildNumberList(masterPrivateKey, HDUtils.parsePath(bapBaseConfig.getDefaultRootPath()), bapBaseConfig);
    }

    public static BapBase fromSeed(String seed) {
        return fromRootChildNumberList(MasterPrivateKey.fromSeed(seed), HDUtils.parsePath(DefaultBapBaseConfig.DEFAULT_ROOT_PATH), new DefaultBapBaseConfig());
    }


    public static BapBase fromRootChildNumberList(MasterPrivateKey masterPrivateKey,
                                                  List<ChildNumber> rootChildNumberList, BapBaseConfig bapBaseConfig) {
        ArrayList<ChildNumber> currentChildNumberList = Lists.newArrayList(rootChildNumberList.subList(0, defaultBapIdChildNumberSize
                - 1));
        ChildNumber last = CollectionUtil.getLast(rootChildNumberList);
        currentChildNumberList.add(new ChildNumber(last.num() + 1, last.isHardened()));
        return new BapBase(masterPrivateKey, rootChildNumberList, currentChildNumberList, bapBaseConfig);
    }

    public static BapBase fromRootPath(MasterPrivateKey masterPrivateKey, BapBaseConfig bapBaseConfig) {
        return fromRootChildNumberList(masterPrivateKey, HDUtils.parsePath(bapBaseConfig.getDefaultRootPath()), bapBaseConfig);
    }

    @Override
    public PublicKey getFriendPublicKey() {
        return getFriendPublicKey(this.getIdentityKey());
    }

    public PublicKey getFriendPublicKey(String friendBapId) {
        String hex = HexUtil.encodeHexStr(Sha256Hash.hash(friendBapId.getBytes()));
        String path = getSigningPathFromHex(HexUtil.encodeHexStr(hex.getBytes(Charsets.UTF_8)));

        DeterministicHierarchy dh = new DeterministicHierarchy(this.masterPrivateKey.getMasterDeterministicKey());
        List<ChildNumber> childNumbers = HDUtils.parsePath(path);
        int size = childNumbers.size();
        DeterministicKey privateFriendKey = dh.deriveChild(childNumbers.subList(0, size - 1), false, true,
                childNumbers.get(size - 1));

        System.out.println(privateFriendKey.serializePrivB58(MainNetParams.get()));
        PrivateKey privateKey = new PrivateKey(ECKeyLite.fromPrivate(privateFriendKey.getPrivKey()));
        return privateKey.getPublicKey();
    }

    /**
     * @param masterPrivateKey
     * @param currentSignatureAddress
     * @param loop
     * @return
     */
    public static BapBase fromSignatureAddress(MasterPrivateKey masterPrivateKey, String identityKey,
                                               String currentSignatureAddress,
                                               int loop) {
        Assert.isTrue(loop <= 100);
        List<ChildNumber> rootChildNumberList = null;
        List<ChildNumber> currentNumberList = null;

        //find identityKey root rootPath;
        DeterministicHierarchy dh = new DeterministicHierarchy(masterPrivateKey.getMasterDeterministicKey());
        List<ChildNumber> childNumbers = HDUtils.parsePath(DefaultBapBaseConfig.DEFAULT_ROOT_PATH);

        List<ChildNumber> head = Lists.newArrayList(childNumbers.subList(0, defaultBapIdChildNumberSize - 2));
        ChildNumber changeChildNumber = CollectionUtil.get(childNumbers, 4);
        ChildNumber tailChildNumber = CollectionUtil.get(childNumbers, 5);

        boolean matchIdentityKey = false;
        boolean matchCurrentSignatureAddress = false;

        for (int i = 0; i < loop; i++) {

            ArrayList<ChildNumber> tmpList = Lists.newArrayList(head);

            tmpList.add(new ChildNumber(changeChildNumber.num() + i, changeChildNumber.isHardened()));

            DeterministicKey rootPrivateKey = dh.deriveChild(tmpList, false, true,
                    tailChildNumber);

            String maybeTargetIdentityKey = BapHelper
                    .getIdentityKey(AddressEnhance.fromPubKeyHash(rootPrivateKey.getPubKeyHash()).toBase58());

            if (identityKey.equals(maybeTargetIdentityKey)) {
                matchIdentityKey = true;
                tmpList.add(tailChildNumber);
                rootChildNumberList = tmpList;
                break;
            }

        }

        if (matchIdentityKey) {

            for (int i = 0; i < loop; i++) {

                DeterministicKey deterministicKey = dh.deriveChild(rootChildNumberList.subList(0, defaultBapIdChildNumberSize
                                - 1),
                        false, true,
                        new ChildNumber(i, true));

                if (currentSignatureAddress
                        .equals(AddressEnhance.fromPubKeyHash(deterministicKey.getPubKeyHash()).toBase58())) {

                    currentNumberList = CollectionUtil
                            .newArrayList(rootChildNumberList.subList(0, defaultBapIdChildNumberSize - 1));
                    currentNumberList.add(new ChildNumber(i, true));
                    matchCurrentSignatureAddress = true;
                    break;
                }

            }

        } else {
            throw new RuntimeException("identityKey match fail");
        }

        if (matchIdentityKey && matchCurrentSignatureAddress) {
            return new BapBase(masterPrivateKey, rootChildNumberList, currentNumberList, new DefaultBapBaseConfig());
        }
        return null;


    }


    public List<ChildNumber> getPreviousChildNumbers() {
        ChildNumber last = CollectionUtil.getLast(this.currentNumberList);
        if (last.num() == 1) {
            return this.rootChildNumberList;
        } else {
            List<ChildNumber> previousChildNumbers = CollectionUtil.newArrayList(this.currentNumberList);
            previousChildNumbers.add(new ChildNumber(last.num() - 1, last.isHardened()));
            return previousChildNumbers;
        }

    }


    public List<ChildNumber> generateNextChildNumbers() {
        List<ChildNumber> childNumbers = this.currentNumberList;
        List<ChildNumber> currentChildNumbers = childNumbers.subList(0, defaultBapIdChildNumberSize - 1);

        currentChildNumbers.add(new ChildNumber(childNumbers.get(defaultBapIdChildNumberSize - 1).num() + 1,
                childNumbers.get(defaultBapIdChildNumberSize - 1).isHardened()));

        this.previousPath = this.currentPath;

        this.currentNumberList = currentChildNumbers;

        this.currentPath = HDUtils.formatPath(currentChildNumbers);
        this.currentPrivateKey = getDeterministicKey(currentNumberList);
        this.previousPrivateKey = getDeterministicKey(HDUtils.parsePath(previousPath));
        return currentChildNumbers;
    }


    private DeterministicKey getEncryptDeterministicKey(List<ChildNumber> childNumbers) {
        int size = childNumbers.size();
        DeterministicHierarchy dh = new DeterministicHierarchy(masterPrivateKey.getMasterDeterministicKey());
        return dh.deriveChild(childNumbers.subList(0, size - 1), false, true,
                childNumbers.get(size - 1));
    }

    private DeterministicKey getDeterministicKey(List<ChildNumber> childNumbers) {
        DeterministicHierarchy dh = new DeterministicHierarchy(masterPrivateKey.getMasterDeterministicKey());
        return dh.deriveChild(childNumbers.subList(0, defaultBapIdChildNumberSize - 1), false, true,
                childNumbers.get(defaultBapIdChildNumberSize - 1));
    }

    public String getSigningPathFromHex(String hex) {
        StringBuilder signPath = new StringBuilder("M");
        Long maxNumber = 2147483648L - 1L;
        String[] split = hex.split("(?<=\\G.{8})");
        Arrays.stream(split).forEach(o -> {
            //转为数字
            Long i = Long.parseLong(o, 16);

            if (i > maxNumber) {
                i = i - maxNumber;
            }
            signPath.append("/").append(i.toString()).append("H");

        });
        return signPath.toString();
    }

    public String getSigningPathFromHex(String hexString, boolean hardened) {
        String signingPath = "m";
        String[] signingHex = hexString.split("(?<=\\G.{8})");
        int maxNumber = 2147483647;
        for (String hexNumber : signingHex) {
            int number = Integer.parseInt(hexNumber, 16);
            if (number > maxNumber) {
                number -= maxNumber;
            }
            signingPath += "/" + number + (hardened ? "'" : "");
        }

        return signingPath;
    }

    public String encryptSelf() {
        return null;
    }


    /**
     * use for decryp export bap id
     *
     * @param self
     * @return
     */
    public String decryptSelf(String self) {
        try {
            return new String(Ecies.decrypt(Base64.decode(self),
                    ECKeyLite.fromPrivate(getEncryptDeterministicKey(HDUtils.parsePath(this.bapBaseConfig.getDefaultEncryptPath())).getPrivKeyBytes()),
                    null), Charset.forName("UTF-8"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    private DeterministicKey getEncryptKey(List<ChildNumber> encryptChildNumberList) {
        ArrayList<ChildNumber> childNumbers = Lists.newArrayList(this.rootChildNumberList);
        childNumbers.addAll(encryptChildNumberList);

        DeterministicHierarchy dh = new DeterministicHierarchy(masterPrivateKey.getMasterDeterministicKey());
        return dh.deriveChild(childNumbers.subList(0, childNumbers.size() - 1), false, true,
                childNumbers.get(childNumbers.size() - 1));
    }


    private DeterministicKey getKeyBaseRoot(List<ChildNumber> childNumberList) {
        ArrayList<ChildNumber> childNumbers = Lists.newArrayList(this.rootChildNumberList);
        childNumbers.addAll(childNumberList);

        DeterministicHierarchy dh = new DeterministicHierarchy(masterPrivateKey.getMasterDeterministicKey());
        return dh.deriveChild(childNumbers.subList(0, childNumbers.size() - 1), false, true,
                childNumbers.get(childNumbers.size() - 1));
    }

    private DeterministicKey getKeyBasePath(List<ChildNumber> childNumberList) {
        DeterministicHierarchy dh = new DeterministicHierarchy(masterPrivateKey.getMasterDeterministicKey());
        return dh.deriveChild(childNumberList.subList(0, childNumberList.size() - 1), false, true,
                childNumberList.get(childNumberList.size() - 1));
    }

    public BapBaseConfig getBapBaseConfig() {
        return bapBaseConfig;
    }

    public MasterPrivateKey getMasterPrivateKey() {
        return masterPrivateKey;
    }

    public String getRootPath() {
        return rootPath;
    }

    @Override
    public ECKeyLite getRootPrivateKey() {
        return ECKeyLite.fromPrivate(this.rootPrivateKey.getPrivKey());
    }

    @Override
    public ECKeyLite getCurrentPrivateKey() {

        return ECKeyLite.fromPrivate(this.currentPrivateKey.getPrivKey());
    }

    public ECKeyLite getPreviousPrivateKey() {
        return ECKeyLite.fromPrivate(this.previousPrivateKey.getPrivKey());
    }


    @Override
    public ECKeyLite getOrdPrivateKey() {
        return ECKeyLite.fromPrivate(getKeyBasePath(HDUtils.parsePath(this.bapBaseConfig.getDefaultOrdPath())).getPrivKey());
    }

    @Override
    public ECKeyLite getEncryptKey() {
        return ECKeyLite.fromPrivate(getKeyBaseRoot(HDUtils.parsePath(this.bapBaseConfig.getDefaultEncryptPath())).getPrivKeyBytes());
    }

    @Override
    public ECKeyLite getPayAccountKey() {
        return ECKeyLite.fromPrivate(getKeyBasePath(HDUtils.parsePath(this.bapBaseConfig.getDefaultPayAccountPath())).getPrivKeyBytes());
    }


    @Override
    public String getRootAddress() {
        return new AddressLite(MainNetParams.get(), this.getRootPrivateKey().getPubKeyHash()).toBase58();
    }

    @Override
    public AddressEnhance getCurrentAddress() {
        return new AddressEnhance(MainNetParams.get(), this.getCurrentPrivateKey().getPubKeyHash());
    }

    public AddressEnhance getPreviouAddress() {
        return AddressEnhance.fromECkeyLite(getPreviousPrivateKey());

    }

    @Override
    public Address getOrdAddress() {
        return new Address(MainNetParams.get(), this.getOrdPrivateKey().getPubKeyHash());
    }

    @Override
    public AddressEnhance getPayAccountAddress() {
        return new AddressEnhance(MainNetParams.get(), this.getPayAccountKey().getPubKeyHash());
    }


    public List<ChildNumber> getRootChildNumberList() {
        return rootChildNumberList;
    }

    public String getPreviousPath() {
        return previousPath;
    }

    public String getCurrentPath() {
        return currentPath;
    }

    public List<ChildNumber> getCurrentNumberList() {
        return currentNumberList;
    }

    @Override
    public String getAppName() {
        return this.bapBaseConfig.getAppName();
    }

    public static class BapProviderKeyBag implements KeyBag {

        private MasterPrivateKey masterPrivateKey;
        private int loop;

        private BapBase bapBase;

        public BapProviderKeyBag(BapBase bapBase) {
            this(bapBase.getMasterPrivateKey(), 100, new DefaultBapBaseConfig());
            this.bapBase = bapBase;
        }

        public BapProviderKeyBag(MasterPrivateKey masterPrivateKey, int loop) {
            this(masterPrivateKey, loop, new DefaultBapBaseConfig());
        }

        public BapProviderKeyBag(MasterPrivateKey masterPrivateKey, int loop, BapBaseConfig bapBaseConfig) {
            Assert.isTrue(loop <= 1000);
            this.masterPrivateKey = masterPrivateKey;
            this.loop = loop;
            if (this.bapBase == null) {
                this.bapBase = BapBase.fromRootPath(masterPrivateKey, bapBaseConfig);
            }

        }


        @Nullable
        @Override
        public ECKey findKeyFromPubHash(byte[] pubkeyHash) {
            //find key in loop,base root
            for (int i = 0; i < this.loop; i++) {
                DeterministicHierarchy dh =
                        new DeterministicHierarchy(this.masterPrivateKey.getMasterDeterministicKey());
                List<ChildNumber> childNumbers = HDUtils.parsePath(this.bapBase.getRootPath());

                DeterministicKey deterministicKey = dh.deriveChild(childNumbers.subList(0, defaultBapIdChildNumberSize - 1),
                        false, true,
                        new ChildNumber(i, true));

                if (AddressEnhance.fromPubKeyHash(pubkeyHash).toBase58()
                        .equals(AddressEnhance.fromPubKeyHash(deterministicKey.getPubKeyHash()).toBase58())) {

                    return ECKey.fromPrivate(deterministicKey.getPrivKeyBytes());
                }
            }
            //find payAccountKey
            if (AddressEnhance.fromPubKeyHash(pubkeyHash).toBase58()
                    .equals(AddressEnhance.fromPubKeyHash(this.bapBase.getPayAccountKey().getPubKeyHash()).toBase58())) {

                return ECKey.fromPrivate(this.bapBase.getPayAccountKey().getPrivKeyBytes());
            }
            //find ordKey
            if (AddressEnhance.fromPubKeyHash(pubkeyHash).toBase58()
                    .equals(AddressEnhance.fromPubKeyHash(this.bapBase.getOrdPrivateKey().getPubKeyHash()).toBase58())) {

                return ECKey.fromPrivate(this.bapBase.getOrdPrivateKey().getPrivKeyBytes());
            }
            throw new RuntimeException("not find ecKey by pubkeyHash:" + HexUtil.encodeHexStr(pubkeyHash) + ",address is :" + AddressEnhance.fromPubKeyHash(pubkeyHash).toBase58());
        }

        @Nullable
        @Override
        public ECKey findKeyFromPubKey(byte[] pubkey) {
            return null;
        }

        @Nullable
        @Override
        public RedeemData findRedeemDataFromScriptHash(byte[] scriptHash) {
            return null;
        }
    }


}
