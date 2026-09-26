package com.metanet4j.sdk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.exception.InvalidKeyException;
import io.bitcoinsv.bitcoinjsv.core.ECKey;
import io.bitcoinsv.bitcoinjsv.core.ECKeyLite;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import io.bitcoinsv.bitcoinjsv.core.Utils;
import io.bitcoinsv.bitcoinjsv.params.MainNetParams;
import io.bitcoinsv.bitcoinjsv.params.TestNet3Params;
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
        assertEquals(COMPRESSED_MAIN, PrivateKey.fromWIF(COMPRESSED_MAIN).toWif(MainNetParams.get()));
        assertEquals(UNCOMPRESSED_MAIN,
                PrivateKey.fromWIF(UNCOMPRESSED_MAIN).toWif(MainNetParams.get()));
        assertEquals(COMPRESSED_TEST, PrivateKey.fromWIF(COMPRESSED_TEST).toWif(TestNet3Params.get()));
        assertEquals(UNCOMPRESSED_TEST,
                PrivateKey.fromWIF(UNCOMPRESSED_TEST).toWif(TestNet3Params.get()));
    }

    @Test
    void exposesKnownPublicKeyAddressAndSignature() {
        ECKeyLite key = ECKeyLite.fromPrivate(BigInteger.TWO);
        PrivateKey privateKey = new PrivateKey(key);
        PublicKey publicKey = privateKey.getPublicKey();
        String pubHex = "02c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5";
        byte[] hash160 = Utils.HEX.decode("06afd46bcdfd22ef94ac122aa11f241244a37ecc");
        String main = "1cMh228HTCiwS8ZsaakH8A8wze1JR5ZsP";
        String test = "mg8Jz5776UdyiYcBb9Z873NTozEiADRW5H";

        assertEquals(COMPRESSED_MAIN, privateKey.toWif(MainNetParams.get()));
        assertEquals(pubHex, publicKey.getPubKeyHex());
        assertArrayEquals(key.getPubKey(), publicKey.getPubKeyBytes());
        assertArrayEquals(hash160, publicKey.getPubKeyHash());
        assertArrayEquals(key.getPubKey(), PublicKey.fromBytes(key.getPubKey()).getPubKeyBytes());
        assertArrayEquals(key.getPubKey(), PublicKey.fromHex(pubHex).getKey().getPubKey());
        assertEquals(key, privateKey.getKey());
        assertArrayEquals(key.getPrivKeyBytes(), privateKey.transformEcKey().getPrivKeyBytes());
        assertEquals(key.getPrivKey(), privateKey.transformEcKeyLiteExtend().getPrivKey());
        Sha256Hash digest = Sha256Hash.of(new byte[] {1, 2, 3});
        assertTrue(key.verify(digest, privateKey.sign(digest)));

        assertEquals(main, AddressEnhance.fromPubKeyHash(hash160).toBase58());
        assertEquals(main, AddressEnhance.fromPrivateKey(privateKey).toBase58());
        assertEquals(main, AddressEnhance.fromPublicKey(publicKey).toBase58());
        assertEquals(main, AddressEnhance.fromECkeyLite(key).toBase58());
        assertEquals(main, AddressEnhance.fromECkey(ECKey.fromPrivate(BigInteger.TWO)).toBase58());
        AddressEnhance address = new AddressEnhance(hash160);
        assertEquals(main, address.toBase58());
        assertEquals(main, new AddressEnhance(address).toBase58());
        assertEquals(main, address.transformAddress().toBase58());
        assertArrayEquals(hash160, address.transformAddress().getHash160());
        assertEquals(test, new AddressEnhance(TestNet3Params.get(), hash160).toBase58());
        assertEquals(test, new AddressEnhance(TestNet3Params.get(),
                TestNet3Params.get().getAddressHeader(), hash160).toBase58());
        assertEquals(test, AddressEnhance.fromPubKeyHash(TestNet3Params.get(), hash160).toBase58());
        assertEquals(test, AddressEnhance.fromPublicKey(TestNet3Params.get(), publicKey).toBase58());
        assertEquals(main, AddressEnhance.fromBase58(MainNetParams.get(), main).toBase58());
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
