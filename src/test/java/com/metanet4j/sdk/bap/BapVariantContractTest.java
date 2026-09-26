package com.metanet4j.sdk.bap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.remote.RemoteSignType;
import io.bitcoinsv.bitcoinjsv.crypto.ChildNumber;
import io.bitcoinsv.bitcoinjsv.crypto.HDUtils;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class BapVariantContractTest {

    @Test
    void remoteIdentityUsesPublicAddressesAndHasNoPrivateKeys() {
        BapBase local = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);
        RemoteBapBase remote = new RemoteBapBase(local.getRootAddress(), local.getPreviouAddress(),
                local.getCurrentAddress(), local.getPayAccountAddress(), local.getOrdAddress(), "remote-app");

        assertEquals(local.getIdentityKey(), remote.getIdentityKey());
        assertEquals("remote-app", remote.getAppName());
        assertEquals(local.getRootAddress(), remote.getSignAddress(RemoteSignType.ROOT));
        assertEquals(local.getPreviouAddress().toBase58(), remote.getSignAddress(RemoteSignType.PREVIOUS));
        assertEquals(local.getCurrentAddress().toBase58(), remote.getSignAddress(RemoteSignType.CURRENT));
        assertEquals(local.getOrdAddress().toBase58(), remote.getSignAddress(RemoteSignType.ORD));
        assertEquals(local.getPayAccountAddress().toBase58(), remote.getSignAddress(RemoteSignType.PAYMENT));
        assertNull(remote.getRootPrivateKey());
        assertNull(remote.getCurrentPrivateKey());
        assertNull(remote.getPreviousPrivateKey());
        assertNull(remote.getEncryptKey());
        assertNull(remote.getOrdPrivateKey());
        assertNull(remote.getPayAccountKey());
        assertNull(remote.getFriendPublicKey());

        RemoteBapBase empty = new RemoteBapBase();
        assertNull(empty.getIdentityKey());
        assertNull(empty.getAppName());
        assertNull(empty.getRootAddress());
        assertNull(empty.getPreviouAddress());
        assertNull(empty.getCurrentAddress());
        assertNull(empty.getOrdAddress());
        assertNull(empty.getPayAccountAddress());
    }

    @Test
    void builtInConfigurationsExposeStablePaths() {
        BapBaseConfig standard = new DefaultBapBaseConfig();
        assertEquals("M/424150H/0H/0H/0H/0H/0H", standard.getDefaultRootPath());
        assertEquals("M/424150H/2147483647H/2147483647H", standard.getDefaultEncryptPath());
        assertEquals("M/0H/236H/0H/1/0", standard.getDefaultPayAccountPath());
        assertEquals("M/44H/236H/1H/0/0", standard.getDefaultOrdPath());
        assertEquals("metanet4j.com", standard.getAppName());

        BapBaseConfig panda = new PandaBapBaseConfig();
        assertEquals("M/0H/236H/0H/0/0", panda.getDefaultRootPath());
        assertEquals("M/0H/236H/0H/0/0", panda.getDefaultEncryptPath());
        assertEquals("M/0H/236H/0H/1/0", panda.getDefaultPayAccountPath());
        assertEquals("M/44H/236H/1H/0/0", panda.getDefaultOrdPath());
        assertEquals("yours wallet", panda.getAppName());

        List<ChildNumber> path = HDUtils.parsePath(panda.getDefaultRootPath());
        PandaBapBase pandaIdentity = new PandaBapBase(TestData.masterPrivateKey, path, path, panda);
        assertEquals(HDUtils.formatPath(path), pandaIdentity.rootPath);
        assertEquals(HDUtils.formatPath(path), pandaIdentity.currentPath);
        assertEquals(panda, pandaIdentity.bapBaseConfig);
        assertEquals("yours wallet", pandaIdentity.getAppName());
    }

    @Test
    void taggedDerivationMatchesFixedVectorAndCommandOutput() {
        String expected = "M/44H/236H/218H/1322583827/1974741175";
        TaggedDerivation.DerivationTag tag = new TaggedDerivation.DerivationTag(
                "BAP", "1QBoVKLwVpttbDx9BwFrLMTi2zdmLMVKCd");
        assertEquals(expected, TaggedDerivation.getTaggedDerivation(tag));

        PrintStream previous = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            TaggedDerivation.main(new String[0]);
        } finally {
            System.setOut(previous);
        }
        assertEquals(expected + System.lineSeparator(), output.toString(StandardCharsets.UTF_8));
    }
}
