package com.metanet4j.sdk.transcation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.metanet4j.sdk.TestData;
import com.metanet4j.sdk.bap.BapBase;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * BAP 输出的 AIP 签名地址必须与交易类型匹配（离线用例，不联网）。
 *
 * 回归背景：buildRoot()/buildId()/buildAlias() 通过 initSignType 声明签名类型，但父类把 signType 初始化为
 * CURRENT（非 null），而 initSignType 只在 signType == null 时赋值 → ROOT/PREVIOUS 永不生效，root 交易被
 * CURRENT 地址签名，BapHelper.isRootBap 判 false、解析器认不出 root。2026-09-16 修复。
 */
class BapDataLockBuilderSignTypeTest {

    private final BapBase bapBase = BapBase.fromOnlyMasterPrivateKey(TestData.masterPrivateKey);

    @Test
    void buildRootSignsWithRootAddress() {
        BapDataLockBuilder builder = new BapDataLockBuilder(bapBase).buildRoot().sign();
        assertEquals(bapBase.getRootAddress(), aipSignAddress(builder));
    }

    @Test
    void buildAliasSignsWithCurrentAddress() {
        BapDataLockBuilder builder = new BapDataLockBuilder(bapBase).buildAlias("{\"@type\":\"Person\"}").sign();
        assertEquals(bapBase.getCurrentAddress().toBase58(), aipSignAddress(builder));
    }

    @Test
    void buildIdSignsWithPreviousAddress() {
        bapBase.generateNextChildNumbers();
        BapDataLockBuilder builder = new BapDataLockBuilder(bapBase).buildId().sign();
        assertEquals(bapBase.getPreviouAddress().toBase58(), aipSignAddress(builder));
    }

    /** AIP 签名块结构为 [..., "|", AIP_PROTOCOL, "BITCOIN_ECDSA", 签名地址, 签名] → 倒数第二个元素是签名地址。 */
    private static String aipSignAddress(UnSpendableDataLockBuilder<?> builder) {
        List<ByteBuffer> dataList = builder.getDataList();
        return new String(dataList.get(dataList.size() - 2).array(), StandardCharsets.UTF_8);
    }
}
