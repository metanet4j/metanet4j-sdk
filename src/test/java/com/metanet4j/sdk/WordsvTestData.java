package com.metanet4j.sdk;

import com.metanet4j.sdk.crypto.MasterPrivateKey;

public class WordsvTestData {

    // server xprv
    public static String serverXprv =
            "xprv9s21ZrQH143K2m2bjtqJQMdfDLW4Wo9c4AKFGE68CxmCgaWjQD2mA8KgJgUZGiMkCZgh2FMkXFLtFGt7Grx7mP6xDVigT8uPnMzP8rZM2LN";

    // alice xprv
    public static String aliceXprv = "xprv9s21ZrQH143K3nufCpBVZgtwhmiYpDSRueYUjwNuxeg1qYyCnTCTki6pwZ8Ry4MVaFpZxAWoFcEJ93uaqQqNKgRLX8ScWEnFUquZj5AMTz8";

    public static MasterPrivateKey serverMasterPrivateKey = MasterPrivateKey.fromXprv(WordsvTestData.serverXprv);

    public static MasterPrivateKey aliceMasterPrivateKey = MasterPrivateKey.fromXprv(WordsvTestData.aliceXprv);


}
