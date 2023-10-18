package com.metanet4j.sdk.bap;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.base.Charsets;
import com.metanet4j.sdk.PublicKey;
import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.crypto.MasterPrivateKey;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class BapBaseTest {


    MasterPrivateKey masterPrivateKey = MasterPrivateKey.fromXprv(TestData.xprv);

    @Before
    public void testFromXprv() {
        Assert.assertEquals(masterPrivateKey.toXprv(), TestData.xprv);
    }

    @Test
    public void testWordList() {
        List<String> list = MasterPrivateKey.generateWordList();
        String join = StrUtil.join(" ", list);
        System.out.println(join);

    }

    @Test
    public void testBapDefault() {
        BapBase bapBase = BapBase.fromOnlyMasterPrivateKey(masterPrivateKey);
        System.out.println(bapBase.getIdentityKey());
        System.out.println(bapBase.getRootAddress());
        System.out.println(bapBase.getCurrentAddress());
        System.out.println(bapBase.getOrdAddress());
        System.out.println(bapBase.getPayAccountAddress());
        System.out.println(AddressEnhance.fromPubKeyHash(bapBase.getEncryptKey().getPubKeyHash()).toBase58());
    }



    @Test
    public void testBapAddress(){
        BapBase bapBase = BapBase.fromOnlyMasterPrivateKey(masterPrivateKey);

        System.out.println("identityKey:" + bapBase.getIdentityKey());

        System.out.println("rootAddress:" + bapBase.getRootAddress());
        System.out.println("root publicKey as hex:" + HexUtil.encodeHexStr(bapBase.getRootPrivateKey().getPubKey()));
        System.out.println("rootPublicKeyhash as hex:" + HexUtil.encodeHexStr(bapBase.getRootPrivateKey().getPubKeyHash()));
        System.out.println("---------");
        System.out.println("currentAddress:" + bapBase.getCurrentAddress().toBase58());
        System.out.println("previouAddress:" + bapBase.getPreviouAddress().toBase58());
        System.out.println();
        System.out.println("ordAddress:" + bapBase.getOrdAddress().toBase58());
        System.out.println("ord PublicKey as hex:" + HexUtil.encodeHexStr(bapBase.getOrdPrivateKey().getPubKey()));
        System.out.println("ordPublicKeyhash as hex:" + HexUtil.encodeHexStr(bapBase.getOrdPrivateKey().getPubKeyHash()));
        System.out.println("---------");
        for (int i = 0; i < 5; i++) {
            System.out.println("------------" + i);
            bapBase.generateNextChildNumbers();
            System.out.println("rootAddress:" + bapBase.getRootAddress());
            System.out.println("currentAddress:" + bapBase.getCurrentAddress().toBase58());
            System.out.println("previouAddress:" + bapBase.getPreviouAddress().toBase58());
        }
    }


    @Test
    public void testEncrypt(){
        BapBase bapBase = BapBase.fromOnlyMasterPrivateKey(masterPrivateKey);
        String plainText ="god";
        String encrypt = bapBase.encrypt(plainText);
        String decrypt = bapBase.decrypt(encrypt);
        System.out.println(decrypt);
    }

    /**
     getSigningPathFromHex(hexString: string, hardened = true) {
     // "m/0/0/1"
     let signingPath = 'm';
     const signingHex = hexString.match(/.{1,8}/g);
     const maxNumber = 2147483648 - 1; // 0x80000000
     signingHex?.forEach((hexNumber) => {
     let number = Number('0x' + hexNumber);
     if (number > maxNumber) number -= maxNumber;
     signingPath += `/${number}${(hardened ? "'" : '')}`;
     });

     return signingPath;
     },
     */

    @Test
    public void testGetSigningPathFromHex(){
        BapBase bapBase = BapBase.fromOnlyMasterPrivateKey(masterPrivateKey);
        String s = HexUtil.encodeHexStr("3jViMo5UdBsfWeoAWLJDmSbhpTwP".getBytes());
        String signPath = bapBase.getSigningPathFromHex(s);
//
        //"m/943208499'/878916921'/909206374'/1714565172'/1667458660'/1714499894'/892560945'/1631151159'/1681076785'/929313587'/1647600436'/1681352037'/879126071'/1630942818'/825241913'/828793188'"
        Assert.assertEquals(signPath,"m/862606953'/1299133781'/1682076518'/1466265409'/1464617540'/1834181224"
                + "'/1884583760'");
    }

    @Test
    public void testGetFriendPublicKey() {
        BapBase bapBase = BapBase.fromOnlyMasterPrivateKey(masterPrivateKey);
        PublicKey friendPublicKey = bapBase.getFriendPublicKey();
        AddressEnhance addressEnhance = AddressEnhance.fromPublicKey(friendPublicKey);

        System.out.println("publicFriendKey:" + friendPublicKey.getPubKeyHex());
        System.out.println("address:" + addressEnhance.toString());
    }

    @Test
    public void testAddress() {

    }

    @Test
    public void testBytes() {
        byte[] messageBytes = HexUtil.decodeHex("32c9ef0ee6d9ba5cb595e17f1a55733de463aee6cdbfe72e03bc49cdf85e9702");
        String message = new String(messageBytes, Charsets.UTF_8);
        byte[] bytes = message.getBytes(Charsets.UTF_8);
        System.out.println(Arrays.equals(messageBytes, bytes));
        System.out.println(HexUtil.encodeHexStr(messageBytes));
        System.out.println(HexUtil.encodeHexStr(bytes));

    }

    @Test
    public void testBytes2() {
        byte[] messageBytes = "Hello, world!".getBytes(Charsets.UTF_8);
        String message = new String(messageBytes, Charsets.UTF_8);
        byte[] bytes = message.getBytes(Charsets.UTF_8);
        System.out.println(Arrays.equals(messageBytes, bytes));
    }


}
