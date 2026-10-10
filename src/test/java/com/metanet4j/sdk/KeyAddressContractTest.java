package com.metanet4j.sdk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metanet4j.base.bsv.BsvHashBytes;
import com.metanet4j.base.bsv.BsvNetwork;
import com.metanet4j.sdk.EcKeyLiteExtend;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.exception.InvalidKeyException;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class KeyAddressContractTest {

    private static final String COMPRESSED_MAIN =
            "KwDiBf89QgGbjEhKnhXJuH7LrciVrZi3qYjgd9M7rFU74NMTptX4";
    private static final String UNCOMPRESSED_MAIN =
            "5HpHagT65TZzG1PH3CSu63k8DbpvD8s5ip4nEB3kEsreAvUcVfH";
    private static final String COMPRESSED_TEST =
            "cMahea7zqjxrtgAbB7LSGbcQUr1uX1ojuat9jZodMN87K7XCyj5v";
    private static final String UNCOMPRESSED_TEST =
            "91avARGdfge8E4tZfYLoxeJ5sGBdNJQH4kvjJoQFacbgww7vXtT";

    @Test
    void roundTripsCompressedAndUncompressedWifOnBothNetworks() throws InvalidKeyException {
        assertEquals(COMPRESSED_MAIN, PrivateKey.fromWIF(COMPRESSED_MAIN).toWif(BsvNetwork.MAINNET));
        assertEquals(UNCOMPRESSED_MAIN,
                PrivateKey.fromWIF(UNCOMPRESSED_MAIN).toWif(BsvNetwork.MAINNET));
        assertEquals(COMPRESSED_TEST, PrivateKey.fromWIF(COMPRESSED_TEST).toWif(BsvNetwork.TESTNET3));
        assertEquals(UNCOMPRESSED_TEST,
                PrivateKey.fromWIF(UNCOMPRESSED_TEST).toWif(BsvNetwork.TESTNET3));
    }

    @Test
    void exposesKnownPublicKeyAddressAndSignature() {
        PrivateKey privateKey = PrivateKey.of(BigInteger.TWO);
        PublicKey publicKey = privateKey.getPublicKey();
        byte[] pubKey = privateKey.getPubKey();
        String pubHex = "02c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5";
        byte[] hash160 = cn.hutool.core.util.HexUtil.decodeHex("06afd46bcdfd22ef94ac122aa11f241244a37ecc");
        String main = "1cMh228HTCiwS8ZsaakH8A8wze1JR5ZsP";
        String test = "mg8Jz5776UdyiYcBb9Z873NTozEiADRW5H";

        assertEquals(COMPRESSED_MAIN, privateKey.toWif(BsvNetwork.MAINNET));
        assertEquals(pubHex, publicKey.getPubKeyHex());
        assertArrayEquals(pubKey, publicKey.getPubKeyBytes());
        assertArrayEquals(hash160, publicKey.getPubKeyHash());
        assertArrayEquals(pubKey, PublicKey.fromBytes(pubKey).getPubKeyBytes());
        assertArrayEquals(pubKey, PublicKey.fromHex(pubHex).getPubKeyBytes());
        assertEquals(com.metanet4j.bsv.primitives.PrivateKey.fromString("2", 16), privateKey.getKey());
        assertArrayEquals(privateKey.getPrivKeyBytes(), privateKey.getPrivKeyBytes());
        assertEquals(privateKey.getPrivKey(), privateKey.getPrivKey());
        BsvHashBytes digest = BsvHashBytes.of(new byte[] {1, 2, 3});
        EcKeyLiteExtend lite = privateKey.transformEcKeyLiteExtend();
        assertTrue(lite.verify(digest, lite.signHash(digest)));

        assertEquals(main, AddressEnhance.fromPubKeyHash(hash160).toBase58());
        assertEquals(main, AddressEnhance.fromPrivateKey(privateKey).toBase58());
        assertEquals(main, AddressEnhance.fromPublicKey(publicKey).toBase58());
        assertEquals(main, AddressEnhance.fromECkeyLite(privateKey).toBase58());
        assertEquals(main, AddressEnhance.fromECkey(privateKey.getPublicKey()).toBase58());
        AddressEnhance address = new AddressEnhance(hash160);
        assertEquals(main, address.toBase58());
        assertEquals(main, new AddressEnhance(address).toBase58());
        assertEquals(main, address.toBase58());
        assertArrayEquals(hash160, address.getHash160());
        assertEquals(test, new AddressEnhance(BsvNetwork.TESTNET3, hash160).toBase58());
        assertEquals(test, new AddressEnhance(BsvNetwork.TESTNET3,
                BsvNetwork.TESTNET3.getAddressHeader(), hash160).toBase58());
        assertEquals(test, AddressEnhance.fromPubKeyHash(BsvNetwork.TESTNET3, hash160).toBase58());
        assertEquals(test, AddressEnhance.fromPublicKey(BsvNetwork.TESTNET3, publicKey).toBase58());
        assertEquals(main, AddressEnhance.fromBase58(BsvNetwork.MAINNET, main).toBase58());
    }

    @Test
    void rejectsBadLengthChecksumAndCompressionMarker() {
        assertThrows(InvalidKeyException.class, () -> PrivateKey.fromWIF("short"));
        assertThrows(RuntimeException.class,
                () -> PrivateKey.fromWIF(COMPRESSED_MAIN.substring(0, 51) + "1"));
        assertThrows(InvalidKeyException.class,
                () -> PrivateKey.fromWIF("KwDiBf89QgGbjEhKnhXJuH7LrciVrZi3qYjgd9M7rFU74NTaiiru"));
        assertThrows(InvalidKeyException.class,
                () -> PrivateKey.fromWIF("54utymBHnNmc56YYWBGwPqHJ52upjrkebrzWnFNv7Aa4rcMJrxHi"));
        assertThrows(InvalidKeyException.class,
                () -> PrivateKey.fromWIF("94ZvR7uaWXWKP4uHSDw3tXqSnmLSHA5Gznp2tWjUN5vHTYoF3389"));
        assertThrows(InvalidKeyException.class,
                () -> PrivateKey.fromWIF("xjeyNcQ79XicEGQtazBgBJ4quh8D54PBaun2i6vo7k7wfDnpK7b"));
    }
}
