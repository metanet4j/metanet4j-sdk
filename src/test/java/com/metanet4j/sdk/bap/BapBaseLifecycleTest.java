package com.metanet4j.sdk.bap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.crypto.MasterPrivateKey;
import com.metanet4j.bsv.compat.HD;
import com.metanet4j.sdk.bap.BapPath;
import com.metanet4j.sdk.crypto.Ecies;
import java.util.List;
import org.junit.jupiter.api.Test;

class BapBaseLifecycleTest {

    @Test
    void rotatingSignatureKeysPreservesIdentityAndTracksPreviousKey() {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        String identity = bap.getIdentityKey();
        String rootAddress = bap.getRootAddress();
        String firstAddress = bap.getCurrentAddress().toBase58();
        assertEquals(rootAddress, bap.getPreviouAddress().toBase58());

        String firstPath = bap.getCurrentPath();
        BapPath secondPath = bap.generateNextChildNumbers();
        assertEquals(firstPath, bap.getPreviousPath());
        assertEquals(firstAddress, bap.getPreviouAddress().toBase58());
        assertEquals(identity, bap.getIdentityKey());
        assertEquals(rootAddress, bap.getRootAddress());
        assertEquals(2L, secondPath.lastIndex(), "第二个子路径的末位索引应为 2");
        assertNotEquals(firstAddress, bap.getCurrentAddress().toBase58());

        String secondAddress = bap.getCurrentAddress().toBase58();
        bap.generateNextChildNumbers();
        assertEquals(3L, bap.getCurrentNumberList().lastIndex(), "轮换后当前路径的末位索引应为 3");
        assertEquals(secondAddress, bap.getPreviouAddress().toBase58());
    }

    @Test
    void reconstructingRotatedIdentityRecoversThePreviousSignatureKey() {
        BapBase rotating = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        String previousAddress = rotating.getCurrentAddress().toBase58();
        rotating.generateNextChildNumbers();

        BapBase reconstructed = new BapBase(TestData.masterPrivateKey,
                rotating.getRootChildNumberList(), rotating.getCurrentNumberList(),
                new DefaultBapBaseConfig());
        assertEquals(rotating.getIdentityKey(), reconstructed.getIdentityKey());
        assertEquals(rotating.getCurrentAddress(), reconstructed.getCurrentAddress());
        assertEquals(previousAddress, reconstructed.getPreviouAddress().toBase58());
    }

    @Test
    void signatureAddressLookupReturnsMatchingIdentityOrReportsFailure() {
        BapBase expected = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        BapBase found = BapBase.fromSignatureAddress(TestData.masterPrivateKey,
                expected.getIdentityKey(), expected.getCurrentAddress().toBase58(), 3);
        assertNotNull(found);
        assertEquals(expected.getIdentityKey(), found.getIdentityKey());
        assertEquals(expected.getCurrentAddress(), found.getCurrentAddress());

        assertThrows(IllegalArgumentException.class, () -> BapBase.fromSignatureAddress(
                TestData.masterPrivateKey, expected.getIdentityKey(), expected.getCurrentAddress().toBase58(), 101));
        assertThrows(IllegalArgumentException.class, () -> BapBase.fromSignatureAddress(
                TestData.masterPrivateKey, expected.getIdentityKey(), expected.getCurrentAddress().toBase58(), 0));
        assertThrows(IllegalStateException.class, () -> BapBase.fromSignatureAddress(
                TestData.masterPrivateKey, "unknown-identity", expected.getCurrentAddress().toBase58(), 3));
        BapBase rootAsCurrent = BapBase.fromSignatureAddress(TestData.masterPrivateKey,
                expected.getIdentityKey(), expected.getRootAddress(), 3);
        assertEquals(expected.getRootAddress(), rootAsCurrent.getCurrentAddress().toBase58());
        assertEquals(expected.getRootAddress(), rootAsCurrent.getPreviouAddress().toBase58());
        assertThrows(IllegalStateException.class, () -> BapBase.fromSignatureAddress(
                TestData.masterPrivateKey, expected.getIdentityKey(), "invalid-address", 3));
    }

    @Test
    void signingPathHandlesUnsignedHexChunksAndHardening() {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        assertEquals("m/1'/2147483647'", bap.getSigningPathFromHex("000000017fffffff", true));
        assertEquals("m/1", bap.getSigningPathFromHex("80000000", false));
        assertEquals("m/1'", bap.getSigningPathFromHex("80000000", true));
        assertEquals("M/1H", bap.getSigningPathFromHex("80000000"));
    }

    @Test
    void factoryEntryPointsPreserveTheConfiguredRootAndApp() {
        DefaultBapBaseConfig config = new DefaultBapBaseConfig();
        BapBase fromMaster = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey, config);
        BapBase fromPath = BapBase.fromRootPath(TestData.masterPrivateKey, config);
        BapBase fromNumbers = BapBase.fromRootChildNumberList(TestData.masterPrivateKey,
                BapPath.parse(config.getDefaultRootPath()), config);

        assertEquals(fromMaster.getIdentityKey(), fromPath.getIdentityKey());
        assertEquals(fromMaster.getCurrentAddress(), fromNumbers.getCurrentAddress());
        assertEquals(config.getDefaultRootPath(), fromMaster.getRootPath());
        assertEquals(config, fromMaster.getBapBaseConfig());
        assertEquals(config.getAppName(), fromMaster.getAppName());

        String mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
        BapBase fromSeed = BapBase.fromSeed(mnemonic);
        assertEquals(BapBase.fromOnlyMasterPrivateKey(MasterPrivateKey.fromSeed(mnemonic)).getIdentityKey(),
                fromSeed.getIdentityKey());
    }

    @Test
    void selfDecryptUsesTheConfiguredAbsoluteEncryptionPathAndReportsBadCiphertext() throws Exception {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        BapPath path = BapPath.parse(bap.getBapBaseConfig().getDefaultEncryptPath());
        HD key = path.derive(TestData.masterPrivateKey.getMasterDeterministicKey());
        String ciphertext = Ecies.encrypt("identity export", key.pubKey, null);

        assertEquals("identity export", bap.decryptSelf(ciphertext));
        assertThrows(IllegalStateException.class, () -> bap.decryptSelf("invalid-ciphertext"));
    }

    @Test
    void selfExportEncryptsCurrentIdentityAndCanBeRestored() {
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        String ciphertext = bap.encryptSelf();

        assertNotNull(ciphertext);
        assertNotEquals(bap.getIdentityKey(), ciphertext);
        assertEquals(bap.getIdentityKey(), bap.decryptSelf(ciphertext));
    }

    @Test
    void selfExportReportsMissingEncryptionPathWithItsCause() {
        DefaultBapBaseConfig config = new DefaultBapBaseConfig() {
            @Override
            public String getDefaultEncryptPath() {
                return null;
            }
        };
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey, config);

        IllegalStateException error = assertThrows(IllegalStateException.class, bap::encryptSelf);
        assertEquals("BAP 身份加密失败", error.getMessage());
        assertNotNull(error.getCause());
    }

    @Test
    void selfExportAlsoRejectsABlankEncryptionPath() {
        // 同一处判断的另一侧：`encryptPath == null || encryptPath.isBlank()`——
        // 上一个用例用 null 覆盖了左侧，本用例用**纯空白串**覆盖右侧（真实可达：
        // 配置里写了空格与没写，对调用方是同一种"没配"）。
        DefaultBapBaseConfig config = new DefaultBapBaseConfig() {
            @Override
            public String getDefaultEncryptPath() {
                return "   ";
            }
        };
        BapBase bap = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey, config);

        IllegalStateException error = assertThrows(IllegalStateException.class, bap::encryptSelf);
        assertEquals("BAP 身份加密失败", error.getMessage());
        assertNotNull(error.getCause());
    }
}
