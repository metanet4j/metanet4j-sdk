package com.metanet4j.sdk.bap;

/**
 * {
 *   "mnemonic": "mass bomb kitchen rubber bus embrace dune adult country warm demand hobby",
 *   "payPk": "Kyzyq9QgRUjD6dzizRraSq5Si5Tg6XxFn5brYduppNWNCxqxnK3N",
 *   "payDerivationPath": "m/44'/236'/0'/1/0",
 *   "ordPk": "L1zcaupEgnJ8pZJEBktDhMchgDcpPkrxJrjRd6xuneZ2Q8Wk3gNB",
 *   "ordDerivationPath": "m/44'/236'/1'/0/0",
 *   "identityPk": "L5NYQqLfqvYQYrKyh25wStWTw5LVuKBxPvWGsPATqKwapkqZnYwU",
 *   "identityDerivationPath": "m/0'/236'/0'/0/0"
 * }
 */
public class PandaBapBaseConfig implements BapBaseConfig{

    public static final String DEFAULT_ROOT_PATH = "M/0H/236H/0H/0/0";

    public static final String DEFAULT_ENCRYPT_PATH = "M/0H/236H/0H/0/0";

    public static final String DEFAULT_PAY_ACCOUNT_PATH =  "M/0H/236H/0H/1/0";

    public static final String DEFAULT_ORD_PATH = "M/44H/236H/1H/0/0";


    public static final String APP_NAME = "yours wallet";


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
