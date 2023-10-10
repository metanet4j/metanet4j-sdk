package com.metanet4j.sdk.crypto;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import com.google.common.base.Charsets;
import io.bitcoinsv.bitcoinjsv.core.ECKeyLite;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;


public final class Ecies {

    public static  byte[] encrypt(byte [] bytes, ECKeyLite pubKey,@Nullable ECKeyLite privKey) throws Exception {

        ECKeyLite recvPubkey = pubKey;
        ECKeyLite ephemeralKey ;
        if(privKey==null){
            ephemeralKey = new ECKeyLite();
        }else{
            ephemeralKey = privKey;
        }
        byte[] ecdhKey = ecdh(ephemeralKey, recvPubkey);

        byte[] iv = ArrayUtil.sub(ecdhKey,0,16);
        byte[] keye =  ArrayUtil.sub(ecdhKey,16,32);
        byte[] keym =  ArrayUtil.sub(ecdhKey,32,64);
        byte[] cryptedBytes = AesCBCUtil.aesEncrypt(bytes, keye, iv);

        byte[] ephemeralPubKey = ephemeralKey.getPubKey();

        ByteArrayOutputStream outputStream =new ByteArrayOutputStream();
        outputStream.writeBytes("BIE1".getBytes());
        outputStream.writeBytes(ephemeralPubKey);
        outputStream.writeBytes(cryptedBytes);

        HMac hMac =new HMac(HmacAlgorithm.HmacSHA256,keym);

        byte[] hmacBytes = hMac.digest(outputStream.toByteArray());

        ByteArrayOutputStream outputStream1 =new ByteArrayOutputStream();
        outputStream1.writeBytes(outputStream.toByteArray());
        outputStream1.writeBytes(hmacBytes);
        return outputStream1.toByteArray();


    }

    public static  String encrypt(String plainText, ECKeyLite pubKey,@Nullable ECKeyLite privKey) throws Exception {
        return Base64.encode(encrypt(plainText.getBytes(Charsets.UTF_8),pubKey,privKey));


    }

    /**
     * @param buf
     * @return
     */
    public static byte[] decrypt( byte [] buf,ECKeyLite privKey,@Nullable ECKeyLite publicKey) throws Exception {
        byte [] magic= ArrayUtil.sub(buf,0,4);
        if(!new String(magic, Charset.forName("UTF-8")).equals("BIE1")){

            throw new RuntimeException("magic is not match");
        }
        ECKeyLite ephemeralPubkey ;
        if(publicKey==null){
            ephemeralPubkey = ECKeyLite.fromPublicOnly(ArrayUtil.sub(buf,4,37));
        }else{
            ephemeralPubkey= publicKey;
        }

        byte [] ciphertext = ArrayUtil.sub(buf,37,buf.length-32);
        byte [] mac =ArrayUtil.sub(buf,buf.length-32,buf.length);

        byte[] ecdhKey = ecdh(privKey, ephemeralPubkey);

        byte[] iv = ArrayUtil.sub(ecdhKey,0,16);
        byte[] keye =  ArrayUtil.sub(ecdhKey,16,32);
        byte[] keym =  ArrayUtil.sub(ecdhKey,32,64);


        byte [] crypted = ArrayUtil.sub(buf,0,buf.length-32);

        HMac hMac =new HMac(HmacAlgorithm.HmacSHA256,keym);

        byte[] hmacBytes = hMac.digest(crypted);
        //check Hmac
        if(!ArrayUtil.equals(hmacBytes,mac)){
            throw new RuntimeException("mac check error");
        }

        byte[] decrypt = AesCBCUtil.aesDecrypt(ciphertext, keye, iv);
        return decrypt;

    }

    private static byte[] ecdh(ECKeyLite privKey,ECKeyLite pubKey){
        byte[] buf = ECKeyLite.fromPublicOnly(pubKey.getPubKeyPoint().multiply(privKey.getPrivKey())).getPubKey();
        return DigestUtilExtend.sha512(buf);
    }

}
