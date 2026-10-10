package com.metanet4j.sdk.bap;

import com.metanet4j.sdk.PrivateKey;

import com.metanet4j.sdk.crypto.MasterPrivateKey;

public class PandaBapBase extends MasterKeyBapBase {


    public PandaBapBase(MasterPrivateKey masterPrivateKey, BapPath rootChildNumberList,
                        BapPath currentNumberList, BapBaseConfig bapBaseConfig) {
        super(masterPrivateKey, rootChildNumberList, currentNumberList, bapBaseConfig);
        this.appName = bapBaseConfig.getAppName();
    }

}
