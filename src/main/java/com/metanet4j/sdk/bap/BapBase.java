package com.metanet4j.sdk.bap;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.HexUtil;
import com.google.common.base.Charsets;
import com.metanet4j.base.bap.BapHelper;
import com.metanet4j.bsv.compat.HD;
import com.metanet4j.sdk.PrivateKey;
import com.metanet4j.sdk.PublicKey;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.crypto.Ecies;
import com.metanet4j.sdk.crypto.MasterPrivateKey;
import com.metanet4j.bsv.primitives.Hash;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Derive private key through HD to construct BapBase.
 *
 * <p>派生路径由旧库的 {@code List<ChildNumber>}／{@code HDUtils}／{@code DeterministicHierarchy}
 * 改为业务值类型 {@link BapPath} 与新 SDK 的 {@code compat.HD}；密钥访问器返回业务
 * {@link PrivateKey}，地址入口走 {@link AddressEnhance}。
 */
public class BapBase extends MasterKeyBapBase {

    private static final int defaultBapIdChildNumberSize = DefaultBapBaseConfig.BAP_CHILD_NUMBER_SIZE;

    private HD rootPrivateKey;

    private HD currentPrivateKey;
    private String previousPath;
    private HD previousPrivateKey;

    // 注意：不要在此重复声明 currentPath / currentNumberList —— 父类 MasterKeyBapBase 已有
    // protected 同名字段并在其构造器中完成初始化；重复声明会遮蔽父类字段（本类那份从未赋值），
    // 导致 getPreviousChildNumbers() 在构造器内 NPE、getCurrentPath()/getCurrentNumberList() 恒为 null。

    public BapBase(MasterPrivateKey masterPrivateKey, BapPath rootChildNumberList,
                   BapPath currentNumberList, BapBaseConfig bapBaseConfig) {
        super(masterPrivateKey, rootChildNumberList, currentNumberList, bapBaseConfig);
        this.rootPrivateKey = deriveBapId(rootChildNumberList);
        this.currentPrivateKey = deriveBapId(currentNumberList);
        this.previousPrivateKey = deriveBapId(this.getPreviousChildNumbers());

        this.identityKey = BapHelper.getIdentityKey(getRootAddress());
        this.bapBaseConfig = bapBaseConfig;
    }

    /**
     * 派生 BAP ID 密钥：前 {@code size-1} 段作为父路径，末段作为子索引
     * （与旧库 {@code deriveChild(list[0..n-2], …, list[n-1])} 等价）。
     */
    private HD deriveBapId(BapPath path) {
        return path.derive(masterPrivateKey.getMasterDeterministicKey());
    }

    public static BapBase fromOnlyMasterPrivateKey(MasterPrivateKey masterPrivateKey) {
        return fromOnlyMasterPrivateKey(masterPrivateKey, new DefaultBapBaseConfig());
    }

    public static BapBase fromOnlyMasterPrivateKey(MasterPrivateKey masterPrivateKey, BapBaseConfig bapBaseConfig) {
        return fromRootPath(masterPrivateKey, bapBaseConfig);
    }

    public static BapBase fromSeed(String seed) {
        return fromRootPath(MasterPrivateKey.fromSeed(seed), new DefaultBapBaseConfig());
    }

    /** 由 root 路径与配置构造。 */
    public static BapBase fromRootPath(MasterPrivateKey masterPrivateKey, BapBaseConfig bapBaseConfig) {
        return fromRootPath(masterPrivateKey, bapBaseConfig.getDefaultRootPath(), bapBaseConfig);
    }

    /** 由 root 路径串与配置构造。 */
    public static BapBase fromRootPath(MasterPrivateKey masterPrivateKey, String rootPath,
                                       BapBaseConfig bapBaseConfig) {
        return fromRootChildNumberList(masterPrivateKey, BapPath.parse(rootPath), bapBaseConfig);
    }

    public static BapBase fromRootChildNumberList(MasterPrivateKey masterPrivateKey,
                                                  BapPath rootChildNumberList, BapBaseConfig bapBaseConfig) {
        return new BapBase(masterPrivateKey, rootChildNumberList,
                nextChildNumbers(rootChildNumberList), bapBaseConfig);
    }

    /**
     * 当前序列约定：root 末段索引 +1，硬化位保持不变
     * （取代旧库 {@code new ChildNumber(last.num() + 1, last.isHardened())}）。
     */
    private static BapPath nextChildNumbers(BapPath root) {
        Assert.isTrue(!root.isEmpty(), "root 路径不能为空");
        return root.prefix(root.size() - 1).append(root.lastIndex() + 1, root.lastHardened());
    }

    @Override
    public PublicKey getFriendPublicKey() {
        return getFriendPublicKey(this.getIdentityKey());
    }

    protected PublicKey getFriendPublicKey(String friendBapId) {
        // 旧实现：Sha256Hash.hash(friendBapId) → hex → 派生路径 M/…H → 朋友公钥
        byte[] digest = toBytes(Hash.sha256(
                com.metanet4j.bsv.primitives.Utils.toArray(friendBapId.getBytes(Charsets.UTF_8)), null));
        String path = getSigningPathFromHex(HexUtil.encodeHexStr(digest));
        HD privateFriendKey = BapPath.parse(path).derive(this.masterPrivateKey.getMasterDeterministicKey());
        return PublicKey.of(privateFriendKey.privKey.toPublicKey());
    }

    public String getSigningPathFromHex(String hex) {
        StringBuilder signPath = new StringBuilder("M");
        Long maxNumber = 2147483648L - 1L;
        String[] split = hex.split("(?<=\\G.{8})");
        Arrays.stream(split).forEach(o -> {
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
        long maxNumber = 2147483647L;
        for (String hexNumber : signingHex) {
            long number = Long.parseLong(hexNumber, 16);
            if (number > maxNumber) {
                number -= maxNumber;
            }
            signingPath += "/" + number + (hardened ? "'" : "");
        }
        return signingPath;
    }

    /**
     * 由身份标识与当前签名地址反查 root 路径与当前路径。
     *
     * @param masterPrivateKey       主私钥
     * @param identityKey            BAP 身份标识
     * @param currentSignatureAddress 当前签名地址
     * @param loop                   轮换搜索上限（1..100）
     */
    public static BapBase fromSignatureAddress(MasterPrivateKey masterPrivateKey, String identityKey,
                                               String currentSignatureAddress, int loop) {
        Assert.isTrue(loop > 0 && loop <= 100, "loop must be between 1 and 100");
        BapPath defaultRoot = BapPath.parse(DefaultBapBaseConfig.DEFAULT_ROOT_PATH);
        BapPath head = defaultRoot.prefix(defaultBapIdChildNumberSize - 2);
        long changeIndex = defaultRoot.indices().get(4) & ~BapPath.HARDENED_BIT;
        boolean changeHardened = (defaultRoot.indices().get(4) & BapPath.HARDENED_BIT) != 0;
        long tailIndex = defaultRoot.indices().get(5);

        BapPath rootChildNumberList = null;
        BapPath currentNumberList = null;

        for (int i = 0; i < loop; i++) {
            BapPath candidate = head.append(changeIndex + i, changeHardened).append(tailIndex);
            HD rootKey = candidate.derive(masterPrivateKey.getMasterDeterministicKey());
            String maybeTargetIdentityKey = BapHelper.getIdentityKey(
                    AddressEnhance.fromPublicKey(rootKey.privKey.toPublicKey()).toBase58());
            if (identityKey.equals(maybeTargetIdentityKey)) {
                rootChildNumberList = candidate;
                break;
            }
        }
        if (rootChildNumberList == null) {
            throw new IllegalStateException("identityKey match fail");
        }

        for (int i = 0; i < loop; i++) {
            BapPath candidate = rootChildNumberList.prefix(defaultBapIdChildNumberSize - 1)
                    .append(i, true);
            HD deterministicKey = candidate.derive(masterPrivateKey.getMasterDeterministicKey());
            if (currentSignatureAddress.equals(
                    AddressEnhance.fromPublicKey(deterministicKey.privKey.toPublicKey()).toBase58())) {
                currentNumberList = candidate;
                break;
            }
        }
        if (currentNumberList == null) {
            throw new IllegalStateException("currentSignatureAddress match fail");
        }
        return new BapBase(masterPrivateKey, rootChildNumberList, currentNumberList, new DefaultBapBaseConfig());
    }

    /** 上一轮签名路径：当前末段索引 -1；索引不大于 1 时回到 root 路径。 */
    public BapPath getPreviousChildNumbers() {
        if (this.currentNumberList.lastIndex() <= 1) {
            return this.rootChildNumberList;
        }
        return this.currentNumberList.prefix(this.currentNumberList.size() - 1)
                .append(this.currentNumberList.lastIndex() - 1, this.currentNumberList.lastHardened());
    }

    /** 生成下一轮签名路径并把 current／previous 指针前移。 */
    public BapPath generateNextChildNumbers() {
        BapPath next = this.currentNumberList.prefix(defaultBapIdChildNumberSize - 1)
                .append(this.currentNumberList.lastIndex() + 1, this.currentNumberList.lastHardened());
        this.previousPath = this.currentPath;
        this.currentNumberList = next;
        this.currentPath = next.toString();
        this.previousPrivateKey = this.currentPrivateKey;
        this.currentPrivateKey = deriveBapId(next);
        return next;
    }

    private HD getEncryptDeterministicKey(BapPath childNumbers) {
        return childNumbers.derive(masterPrivateKey.getMasterDeterministicKey());
    }

    private HD getDeterministicKey(BapPath childNumbers) {
        return deriveBapId(childNumbers);
    }

    public String encryptSelf() {
        try {
            // 显式校验加密路径：BapPath.parse(null) 会把 null 当根路径而**静默成功**，
            // 于是「配置缺失加密路径」被掩盖（BapBaseLifecycleTest
            // #selfExportReportsMissingEncryptionPathWithItsCause 要求的正是把它暴露出来）。
            String encryptPath = this.bapBaseConfig.getDefaultEncryptPath();
            if (encryptPath == null || encryptPath.isBlank()) {
                throw new IllegalStateException("配置缺少默认加密路径（getDefaultEncryptPath 为空）");
            }
            HD encryptionKey = getEncryptDeterministicKey(BapPath.parse(encryptPath));
            return Ecies.encrypt(this.identityKey, encryptionKey.pubKey, null);
        } catch (Exception e) {
            throw new IllegalStateException("BAP 身份加密失败", e);
        }
    }

    /**
     * use for decryp export bap id
     */
    public String decryptSelf(String self) {
        try {
            HD encryptionKey = getEncryptDeterministicKey(
                    BapPath.parse(this.bapBaseConfig.getDefaultEncryptPath()));
            return new String(Ecies.decrypt(Base64.decode(self), encryptionKey.privKey, null),
                    Charset.forName("UTF-8"));
        } catch (Exception e) {
            throw new IllegalStateException("BAP 身份解密失败", e);
        }
    }

    /** 以 rootPath 作为根节点派生密钥（与 {@code getKeyBasePath} 同义，保留旧入口名）。 */
    private HD getKeyBaseRoot(BapPath childNumberList) {
        return childNumberList.derive(masterPrivateKey.getMasterDeterministicKey());
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
    public PrivateKey getRootPrivateKey() {
        return PrivateKey.of(this.rootPrivateKey.privKey);
    }

    @Override
    public PrivateKey getCurrentPrivateKey() {
        return PrivateKey.of(this.currentPrivateKey.privKey);
    }

    public PrivateKey getPreviousPrivateKey() {
        return PrivateKey.of(this.previousPrivateKey.privKey);
    }

    @Override
    public PrivateKey getOrdPrivateKey() {
        return PrivateKey.of(getKeyBasePath(
                BapPath.parse(this.bapBaseConfig.getDefaultOrdPath())).privKey);
    }

    @Override
    public PrivateKey getEncryptKey() {
        return PrivateKey.of(getKeyBaseRoot(
                BapPath.parse(this.bapBaseConfig.getDefaultEncryptPath())).privKey);
    }

    @Override
    public PrivateKey getPayAccountKey() {
        return PrivateKey.of(getKeyBasePath(
                BapPath.parse(this.bapBaseConfig.getDefaultPayAccountPath())).privKey);
    }

    @Override
    public String getRootAddress() {
        return AddressEnhance.fromECkeyLite(this.getRootPrivateKey()).toBase58();
    }

    @Override
    public AddressEnhance getCurrentAddress() {
        return AddressEnhance.fromECkeyLite(this.getCurrentPrivateKey());
    }

    public AddressEnhance getPreviouAddress() {
        return AddressEnhance.fromECkeyLite(getPreviousPrivateKey());
    }

    @Override
    public AddressEnhance getOrdAddress() {
        return AddressEnhance.fromECkeyLite(this.getOrdPrivateKey());
    }

    @Override
    public AddressEnhance getPayAccountAddress() {
        return AddressEnhance.fromECkeyLite(this.getPayAccountKey());
    }

    public BapPath getRootChildNumberList() {
        return rootChildNumberList;
    }

    public String getPreviousPath() {
        return previousPath;
    }

    public String getCurrentPath() {
        return currentPath;
    }

    public BapPath getCurrentNumberList() {
        return currentNumberList;
    }

    @Override
    public String getAppName() {
        return this.bapBaseConfig.getAppName();
    }

    /**
     * BAP 派生密钥提供者：在 root 轮换区间（0..loop-1，硬化）内按 hash160 查找签名私钥，
     * 并回退到支付账户与 Ordinals 账户；取代旧库 {@code KeyBag} 的反查抽象。
     */
    public static class BapProviderKeyBag implements SigningKeyProvider {

        private final MasterPrivateKey masterPrivateKey;
        private final int loop;

        private final BapBase bapBase;

        public BapProviderKeyBag(BapBase bapBase) {
            this.masterPrivateKey = bapBase.getMasterPrivateKey();
            this.loop = 100;
            this.bapBase = bapBase;
        }

        public BapProviderKeyBag(MasterPrivateKey masterPrivateKey, int loop) {
            this(masterPrivateKey, loop, new DefaultBapBaseConfig());
        }

        public BapProviderKeyBag(MasterPrivateKey masterPrivateKey, int loop, BapBaseConfig bapBaseConfig) {
            Assert.isTrue(loop <= 1000);
            this.masterPrivateKey = masterPrivateKey;
            this.loop = loop;
            this.bapBase = BapBase.fromRootPath(masterPrivateKey, bapBaseConfig);
        }

        @Nullable
        @Override
        public PrivateKey findKeyFromPubHash(byte[] pubkeyHash) {
            String target = AddressEnhance.fromPubKeyHash(pubkeyHash).toBase58();
            for (int i = 0; i < this.loop; i++) {
                HD key = this.bapBase.rootChildNumberList
                        .prefix(defaultBapIdChildNumberSize - 1)
                        .append(i, true)
                        .derive(this.masterPrivateKey.getMasterDeterministicKey());
                if (target.equals(AddressEnhance.fromPublicKey(key.privKey.toPublicKey()).toBase58())) {
                    return PrivateKey.of(key.privKey);
                }
            }
            if (target.equals(AddressEnhance.fromECkeyLite(this.bapBase.getPayAccountKey()).toBase58())) {
                return this.bapBase.getPayAccountKey();
            }
            if (target.equals(AddressEnhance.fromECkeyLite(this.bapBase.getOrdPrivateKey()).toBase58())) {
                return this.bapBase.getOrdPrivateKey();
            }
            throw new RuntimeException("not find ecKey by pubkeyHash:" + HexUtil.encodeHexStr(pubkeyHash)
                    + ",address is :" + target);
        }

        /** 业务 BAP 实例（轮换与地址入口）。 */
        public BapBase getBapBase() {
            return bapBase;
        }
    }

    private static byte[] toBytes(List<Double> values) {
        byte[] bytes = new byte[values.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (int) (double) values.get(i);
        }
        return bytes;
    }
}
