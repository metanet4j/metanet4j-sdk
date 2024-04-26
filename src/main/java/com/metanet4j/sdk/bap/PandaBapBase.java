package com.metanet4j.sdk.bap;

import com.metanet4j.sdk.crypto.MasterPrivateKey;
import io.bitcoinsv.bitcoinjsv.crypto.ChildNumber;

import java.util.List;

public class PandaBapBase extends MasterKeyBapBase {


    public PandaBapBase(MasterPrivateKey masterPrivateKey, List<ChildNumber> rootChildNumberList,
                        List<ChildNumber> currentNumberList, BapBaseConfig bapBaseConfig) {
        super(masterPrivateKey, rootChildNumberList, currentNumberList, bapBaseConfig);
    }

}
