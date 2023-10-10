package com.metanet4j.sdk.bap;

public class DefaultBapBaseConfig implements BapBaseConfig {
    @Override
    public String getDefaultRootPath() {
        return DEFAULT_ROOT_PATH;
    }

    @Override
    public String getDefaultEncryptPath() {
        return DEFAULT_ENCRYPT_PATH;
    }

    @Override
    public String getDefaultPayAccountPath() {
        return DEFAULT_PAY_ACCOUNT_PATH;
    }

    @Override
    public String getDefaultOrdPath() {
        return DEFAULT_ORD_PATH;
    }

    @Override
    public String getAppName() {
        return APP_NAME;
    }
}
