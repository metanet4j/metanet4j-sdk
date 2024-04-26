package com.metanet4j.sdk.bap;

public class DefaultBapBaseConfig implements BapBaseConfig {

    public static final String DEFAULT_ROOT_PATH = "M/424150H/0H/0H/0H/0H/0H";

    public static final String DEFAULT_ENCRYPT_PATH = "M/424150H/2147483647H/2147483647H";

//    String DEFAULT_PAY_ACCOUNT_PATH = "M/424150H/2147483647H/2147483646H";

    //    String DEFAULT_ORD_PATH = "M/424150H/2147483647H/2147483645H";
    public static final String DEFAULT_PAY_ACCOUNT_PATH = "M/44H/236H/0H/0/0";

    public static final String DEFAULT_ORD_PATH = "M/44H/236H/1H/0/0";


    public static final String APP_NAME = "metanet4j.com";
    //fixed size
    public static final int BAP_CHILD_NUMBER_SIZE = 6;

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
