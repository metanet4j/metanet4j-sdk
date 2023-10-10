
package com.metanet4j.sdk;


import com.metanet4j.sdk.exception.InvalidKeyException;
import com.metanet4j.sdk.utils.ReadUtils;
import io.bitcoinsv.bitcoinjsv.core.*;
import io.bitcoinsv.bitcoinjsv.ecc.ECDSASignature;
import io.bitcoinsv.bitcoinjsv.params.MainNetParams;
import io.bitcoinsv.bitcoinjsv.params.Net;
import io.bitcoinsv.bitcoinjsv.params.NetworkParameters;

import javax.annotation.Nullable;
import java.math.BigInteger;

public class PrivateKey  {

    private ECKeyLite key;
    private boolean isCompressed;
    private NetworkParameters networkType;

    public PrivateKey(@Nullable ECKeyLite key){
        this(key, true, MainNetParams.get());
    }

    public PrivateKey(@Nullable ECKeyLite key, boolean isCompressed,NetworkParameters networkType) {
        this.key = key;
        this.isCompressed = isCompressed;
        this.networkType = networkType;
    }




    public static PrivateKey fromWIF(String wif) throws InvalidKeyException {

        boolean isCompressed = false;

        if (wif.length() != 51 && wif.length() != 52){
            throw new InvalidKeyException("Valid keys are either 51 or 52 bytes in length");
        }

        //decode from base58
        byte[] versionAndDataBytes = Base58.decodeChecked(wif);

        NetworkParameters networkType = decodeNetworkType(wif);

        //strip first byte
        ReadUtils reader = new ReadUtils(versionAndDataBytes);
        byte version = reader.readByte();
        byte[] dataBytes = reader.readBytes(versionAndDataBytes.length - 1);

        byte[] keyBytes = dataBytes.clone();
        if (dataBytes.length == 33){
            //drop last byte
            //throw error if last byte is not 0x01 to indicate compression
            if (dataBytes[32] != 0x01) {
                throw new InvalidKeyException("Compressed keys must have last byte set as 0x01. Yours is [" + dataBytes[32] + "]");
            }

            keyBytes = new ReadUtils(dataBytes).readBytes(32);
            isCompressed = true;
        }

        String keyHex = Utils.HEX.encode(keyBytes);
        BigInteger d = new BigInteger(keyHex, 16);

        ECKeyLite key = ECKeyLite.fromPrivate(d);

        return new PrivateKey(key, isCompressed, networkType);
    }



    public String toWif(NetworkParameters networkType){
        return this.key.getPrivateKeyAsWiF(networkType);
    }

    public PublicKey getPublicKey() {
        return PublicKey.fromHex(Utils.HEX.encode(key.getPubKey()));
    }

    public ECKeyLite getKey() {
        return key;
    }

    public ECKey transformEcKey() {

        return ECKey.fromPrivate(key.getPrivKeyBytes());
    }

    public EcKeyLiteExtend transformEcKeyLiteExtend() {
        return EcKeyLiteExtend.fromPrivate(key.getPrivKey());
    }

    public ECDSASignature sign(Sha256Hash hash) {
        return key.sign(hash);
    }

    private static NetworkParameters decodeNetworkType(String wifKey) throws InvalidKeyException {

        switch (wifKey.charAt(0)) {
            case '5': {
                if (wifKey.length() != 51) {
                    throw new InvalidKeyException("Uncompressed private keys have a length of 51 bytes");
                }

                return Net.MAINNET.params();
            }
            case '9' : {
                if (wifKey.length() != 51) {
                    throw new InvalidKeyException("Uncompressed private keys have a length of 51 bytes");
                }

                return Net.TESTNET3.params();
            }
            case 'L' : case 'K' : {
                if (wifKey.length() != 52) {
                    throw new InvalidKeyException("Compressed private keys have a length of 52 bytes");
                }

                return  Net.MAINNET.params();
            }
            case 'c' : {
                if (wifKey.length() != 52) {
                    throw new InvalidKeyException("Compressed private keys have a length of 52 bytes");
                }

                return  Net.TESTNET3.params();
            }
            default : {
                throw new InvalidKeyException("AddressEnhance WIF format must start with either [5] , [9], [L], [K] or [c]");
            }

        }
    }
}
