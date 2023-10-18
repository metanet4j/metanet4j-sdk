package com.metanet4j.sdk.bap;

public interface BapBaseConfig {

    String DEFAULT_ROOT_PATH = "M/424150H/0H/0H/0H/0H/0H";

    String DEFAULT_ENCRYPT_PATH = "M/424150H/2147483647H/2147483647H";

//    String DEFAULT_PAY_ACCOUNT_PATH = "M/424150H/2147483647H/2147483646H";

//    String DEFAULT_ORD_PATH = "M/424150H/2147483647H/2147483645H";

    String DEFAULT_PAY_ACCOUNT_PATH = "M/44H/236H/0H/0/0";

    String DEFAULT_ORD_PATH = "M/44H/236H/1H/0/0";


    String APP_NAME = "metanet4j.com";
    //fixed size
    int BAP_CHILD_NUMBER_SIZE = 6;

    String getDefaultRootPath();

    String getDefaultEncryptPath();

    String getDefaultPayAccountPath();

    String getDefaultOrdPath();

    String getAppName();
}
