package com.metanet4j.sdk.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metanet4j.sdk.address.AddressEnhance;
import io.bitcoinsv.bitcoinjsv.crypto.ChildNumber;
import io.bitcoinsv.bitcoinjsv.crypto.DeterministicHierarchy;
import io.bitcoinsv.bitcoinjsv.crypto.DeterministicKey;
import io.bitcoinsv.bitcoinjsv.crypto.HDKeyDerivation;
import io.bitcoinsv.bitcoinjsv.crypto.HDUtils;
import io.bitcoinsv.bitcoinjsv.params.MainNetParams;
import io.bitcoinsv.bitcoinjsv.params.TestNet3Params;
import java.util.List;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class CryptoHelperContractTest {

    private static final List<String> WORDS = List.of("abandon", "abandon", "abandon", "abandon",
            "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "about");

    @Test
    void fixedMnemonicDerivesExpectedRootAndConsistentPublicValues() throws Exception {
        CryptoHelper.CryptoBase root = CryptoHelper.generateRootCryptoBase(WORDS, "TREZOR", MainNetParams.get());
        MasterPrivateKey master = MasterPrivateKey.fromSeed(WORDS, "TREZOR", MainNetParams.get());
        List<ChildNumber> path = HDUtils.parsePath("M/44H/236H/0H/0/0");
        DeterministicHierarchy hierarchy = new DeterministicHierarchy(master.getMasterDeterministicKey());
        DeterministicKey expected = hierarchy.deriveChild(path.subList(0, path.size() - 1),
                false, true, path.getLast());

        assertEquals(expected.getPrivKey(), root.getDeterministicKey().getPrivKey());
        assertEquals(expected.getPrivateKeyAsWiF(MainNetParams.get()), root.getPrivateKeyToWif());
        assertEquals(root.getPrivateKeyToWif(), root.getPrivateKey().toWif(MainNetParams.get()));
        assertEquals(root.getPublicKeyHex(), root.getPublicKey().getPubKeyHex());
        assertEquals(AddressEnhance.fromPubKeyHash(expected.getPubKeyHash()), root.getLegacyAddress());
        assertEquals(root.getLegacyAddress().toBase58(), root.getAddressBase58());

        CryptoHelper.CryptoBase defaultNetwork = CryptoHelper.generateRootCryptoBase(WORDS, "TREZOR");
        assertEquals(root.getAddressBase58(), defaultNetwork.getAddressBase58());
    }

    @Test
    void childDerivationRespectsNetworkAndParentKey() throws Exception {
        CryptoHelper.CryptoBase parent = CryptoHelper.generateRootCryptoBase(WORDS, "", TestNet3Params.get());
        ChildNumber childNumber = new ChildNumber(1, false);
        CryptoHelper.CryptoBase child = CryptoHelper.generateChlidCryptoBase(
                parent.getDeterministicKey(), childNumber, TestNet3Params.get());
        DeterministicKey expected = HDKeyDerivation.deriveChildKey(parent.getDeterministicKey(), childNumber);

        assertEquals(expected.getPrivKey(), child.getDeterministicKey().getPrivKey());
        assertEquals(expected.getPrivateKeyAsWiF(TestNet3Params.get()), child.getPrivateKeyToWif());
        assertEquals(child.getLegacyAddress().toBase58(), child.getAddressBase58());
        assertNotEquals(parent.getAddressBase58(), child.getAddressBase58());
        assertEquals(child.getDeterministicKey().getPrivKey(), CryptoHelper.generateChlidCryptoBase(
                parent.getDeterministicKey(), childNumber).getDeterministicKey().getPrivKey());
    }

    @Test
    void emptyMnemonicAndNoArgumentFactoryCreateUsableKeys() throws Exception {
        CryptoHelper.CryptoBase fromEmpty = CryptoHelper.generateRootCryptoBase(List.of(), "", MainNetParams.get());
        CryptoHelper.CryptoBase generated = CryptoHelper.generateRootCryptoBase();

        assertNotNull(fromEmpty.getPrivateKey());
        assertNotNull(generated.getPrivateKey());
        assertTrue(fromEmpty.getAddressBase58().startsWith("1"));
        assertTrue(generated.getAddressBase58().startsWith("1"));
    }
}
