package com.metanet4j.sdk.bap;

import com.metanet4j.base.bap.BapHelper;
import com.metanet4j.sdk.crypto.MasterPrivateKey;
import io.bitcoinsv.bitcoinjsv.crypto.ChildNumber;
import io.bitcoinsv.bitcoinjsv.crypto.DeterministicHierarchy;
import io.bitcoinsv.bitcoinjsv.crypto.DeterministicKey;
import io.bitcoinsv.bitcoinjsv.crypto.HDUtils;

import javax.validation.constraints.NotNull;
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

        this.identityKey = BapHelper.getIdentityKey(getRootAddress());
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
