package com.metanet4j.sdk.bap;

import com.metanet4j.bsv.compat.HD;
import com.metanet4j.sdk.crypto.MasterPrivateKey;
import jakarta.validation.constraints.NotNull;

/**
 * 持有主私钥与派生路径的 BAP 基类。
 *
 * <p>路径表示由旧库的 {@code List<ChildNumber>}／{@code HDUtils} 改为业务值类型
 * {@link BapPath}；派生走新 SDK 的 {@code compat.HD.derive}／{@code deriveChild}。
 */
public abstract class MasterKeyBapBase extends BapBaseAbstract {

    protected MasterPrivateKey masterPrivateKey;
    protected String rootPath;
    protected BapPath rootChildNumberList;

    protected String currentPath;
    protected BapPath currentNumberList;

    @NotNull
    protected BapBaseConfig bapBaseConfig;

    public MasterKeyBapBase(MasterPrivateKey masterPrivateKey, BapPath rootChildNumberList,
                            BapPath currentNumberList, BapBaseConfig bapBaseConfig) {
        this.masterPrivateKey = masterPrivateKey;
        this.rootChildNumberList = rootChildNumberList;
        this.currentNumberList = currentNumberList;
        this.rootPath = rootChildNumberList.toString();
        this.currentPath = currentNumberList.toString();

        // 注意：不能在这里计算 identityKey —— getRootAddress() 被 BapBase 覆写，
        // 而 BapBase.rootPrivateKey 要等本构造器返回后才赋值，提前调用会 NPE。
        // 具体子类在自己的字段就绪后设置 identityKey（见 BapBase 构造器末尾）。
        this.bapBaseConfig = bapBaseConfig;
    }

    /**
     * 以 rootPath 作为根节点派生密钥。
     *
     * <p>与旧库 {@code DeterministicHierarchy.deriveChild(list[0..n-2], ..., list[n-1])} 等价。
     */
    protected HD getKeyBasePath(BapPath childNumberList) {
        return childNumberList.derive(masterPrivateKey.getMasterDeterministicKey());
    }

    /** 以路径串派生密钥。 */
    protected HD getKeyBasePath(String path) {
        return BapPath.parse(path).derive(masterPrivateKey.getMasterDeterministicKey());
    }
}
