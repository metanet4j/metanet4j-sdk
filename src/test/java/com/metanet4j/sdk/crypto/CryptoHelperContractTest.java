package com.metanet4j.sdk.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metanet4j.base.bsv.BsvNetwork;
import com.metanet4j.bsv.compat.HD;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.bap.BapPath;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link CryptoHelper} 的派生与取值契约。
 *
 * <p>迁移要点：网络参数由旧库 {@code MainNetParams/TestNet3Params} 改 {@link BsvNetwork}；
 * 子密钥派生不再用 {@code DeterministicHierarchy + List<ChildNumber>}，改为
 * {@link BapPath#derive(HD)}（一次派生完整路径）与 {@code generateChlidCryptoBase(HD, long, ...)}；
 * 私钥比对用新 SDK {@code HD.privKey} 的 hex（旧 {@code getPrivKey()} 无 equals 保证）。
 */
@SuppressWarnings("deprecation")
class CryptoHelperContractTest {

    private static final List<String> WORDS = List.of("abandon", "abandon", "abandon", "abandon",
            "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "abandon", "about");

    @Test
    void fixedMnemonicDerivesExpectedRootAndConsistentPublicValues() throws Exception {
        CryptoHelper.CryptoBase root = CryptoHelper.generateRootCryptoBase(WORDS, "TREZOR",
                BsvNetwork.MAINNET);
        MasterPrivateKey master = MasterPrivateKey.fromSeed(WORDS, "TREZOR");
        BapPath path = BapPath.parse("M/44H/236H/0H/0/0");
        HD expected = path.derive(master.getMasterDeterministicKey());

        assertEquals(expected.privKey.toHex(), root.getDeterministicKey().privKey.toHex(),
                "派生出的确定性私钥应与独立派生结果一致");
        assertEquals(root.getPrivateKeyToWif(), root.getPrivateKey().toWif(BsvNetwork.MAINNET));
        assertEquals(root.getPublicKeyHex(), root.getPublicKey().getPubKeyHex());
        assertEquals(AddressEnhance.fromPublicKey(expected.pubKey).toBase58(),
                root.getLegacyAddress().toBase58(), "地址应由独立派生出的公钥得到");
        assertEquals(root.getLegacyAddress().toBase58(), root.getAddressBase58());

        CryptoHelper.CryptoBase defaultNetwork = CryptoHelper.generateRootCryptoBase(WORDS, "TREZOR");
        assertEquals(root.getAddressBase58(), defaultNetwork.getAddressBase58(),
                "省略网络时应与主网一致");
    }

    @Test
    void childDerivationRespectsNetworkAndParentKey() throws Exception {
        CryptoHelper.CryptoBase parent = CryptoHelper.generateRootCryptoBase(WORDS, "",
                BsvNetwork.TESTNET3);
        CryptoHelper.CryptoBase child = CryptoHelper.generateChlidCryptoBase(
                parent.getDeterministicKey(), 1L, BsvNetwork.TESTNET3);
        HD expected = BapPath.of(List.of(1L)).derive(parent.getDeterministicKey());

        assertEquals(expected.privKey.toHex(), child.getDeterministicKey().privKey.toHex());
        assertEquals(child.getLegacyAddress().toBase58(), child.getAddressBase58());
        assertNotEquals(parent.getAddressBase58(), child.getAddressBase58());
        assertEquals(child.getDeterministicKey().privKey.toHex(), CryptoHelper.generateChlidCryptoBase(
                parent.getDeterministicKey(), 1L).getDeterministicKey().privKey.toHex(),
                "省略网络时派生结果应一致");
    }

    @Test
    void emptyMnemonicAndNoArgumentFactoryCreateUsableKeys() throws Exception {
        CryptoHelper.CryptoBase fromEmpty = CryptoHelper.generateRootCryptoBase(List.of(), "",
                BsvNetwork.MAINNET);
        CryptoHelper.CryptoBase generated = CryptoHelper.generateRootCryptoBase();

        assertNotNull(fromEmpty.getPrivateKey());
        assertNotNull(generated.getPrivateKey());
        assertTrue(fromEmpty.getAddressBase58().startsWith("1"), "主网地址应以 1 开头");
        assertTrue(generated.getAddressBase58().startsWith("1"));
    }
}
