package com.metanet4j.sdk.bap;

import com.metanet4j.sdk.PrivateKey;

import com.metanet4j.base.bap.BapHelper;
import com.metanet4j.sdk.crypto.MasterPrivateKey;
import io.bitcoinsv.bitcoinjsv.crypto.ChildNumber;
import io.bitcoinsv.bitcoinjsv.crypto.DeterministicHierarchy;
import io.bitcoinsv.bitcoinjsv.crypto.DeterministicKey;
import io.bitcoinsv.bitcoinjsv.crypto.HDUtils;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public abstract class MasterKeyBapBase extends BapBaseAbstract {

    protected MasterPrivateKey masterPrivateKey;
    protected String rootPath;
    protected List<ChildNumber> rootChildNumberList;

    protected String currentPath;
    protected List<ChildNumber> currentNumberList;


    @NotNull
    protected BapBaseConfig bapBaseConfig;

    public MasterKeyBapBase(MasterPrivateKey masterPrivateKey, List<ChildNumber> rootChildNumberList,
                            List<ChildNumber> currentNumberList, BapBaseConfig bapBaseConfig) {

        this.masterPrivateKey = masterPrivateKey;
        this.rootChildNumberList = rootChildNumberList;
        this.currentNumberList = currentNumberList;
        this.rootPath = HDUtils.formatPath(rootChildNumberList);
        this.currentPath = HDUtils.formatPath(currentNumberList);

        // 注意：不能在这里计算 identityKey —— getRootAddress() 被 BapBase 覆写，
        // 而 BapBase.rootPrivateKey 要等本构造器返回后才赋值，提前调用会 NPE。
        // 具体子类在自己的字段就绪后设置 identityKey（见 BapBase 构造器末尾）。
        this.bapBaseConfig = bapBaseConfig;

    }


    /**
     * 以rootPath作为根节点衍生秘钥
     *
     * @param childNumberList
     * @return
     */
    protected DeterministicKey getKeyBasePath(List<ChildNumber> childNumberList) {
        DeterministicHierarchy dh = new DeterministicHierarchy(masterPrivateKey.getMasterDeterministicKey());
        return dh.deriveChild(childNumberList.subList(0, childNumberList.size() - 1), false, true,
                childNumberList.get(childNumberList.size() - 1));
    }


}
