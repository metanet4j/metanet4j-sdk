package com.metanet4j.sdk.bap;

import cn.hutool.core.util.StrUtil;
import com.metanet4j.bsv.compat.HD;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * BAP 派生路径值类型：以「索引 + 硬化位」列表承载 BIP32 路径，取代旧库的
 * {@code List<ChildNumber>}／{@code HDUtils}。
 *
 * <p>新 SDK（com.metanet4j.bsv）的 {@code compat.HD} 只提供 {@code derive(String path)}
 * 与 {@code deriveChild(long index)}，没有 ChildNumber 值类型；业务侧用它统一解析、
 * 格式化与派生，避免各处自行拼路径。
 *
 * <p>接受两种写法：旧库的 {@code M/44H/236H/1H/0/0}（{@code H} 表示硬化）与标准
 * {@code m/44'/236'/1'/0/0}；{@link #toString()} 输出标准小写 {@code m/...} 形式。
 * 硬化索引按 BIP32 约定存储为 {@code index | 0x80000000}。
 */
public final class BapPath {

    /** 硬化位。 */
    public static final long HARDENED_BIT = 0x80000000L;

    private final List<Long> indices;

    private BapPath(List<Long> indices) {
        this.indices = List.copyOf(indices);
    }

    /** 解析路径；空串或 null 视为空路径（主密钥自身）。 */
    public static BapPath parse(String path) {
        if (StrUtil.isBlank(path)) {
            return new BapPath(List.of());
        }
        String normalized = path.trim();
        if (normalized.startsWith("m") || normalized.startsWith("M")) {
            normalized = normalized.substring(1);
        }
        List<Long> indices = new ArrayList<>();
        for (String segment : normalized.split("/")) {
            if (segment.isBlank()) {
                continue;
            }
            String text = segment.trim();
            boolean hardened = text.endsWith("'") || text.endsWith("h") || text.endsWith("H");
            if (hardened) {
                text = text.substring(0, text.length() - 1);
            }
            long value;
            try {
                value = Long.parseLong(text);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("非法派生路径段：" + segment + "（路径 " + path + "）", e);
            }
            if (value < 0 || value >= HARDENED_BIT) {
                throw new IllegalArgumentException("派生索引越界：" + value + "（路径 " + path + "）");
            }
            indices.add(hardened ? value | HARDENED_BIT : value);
        }
        return new BapPath(indices);
    }

    /** 由索引列表构造（已含硬化位）。 */
    public static BapPath of(List<Long> indices) {
        return new BapPath(indices);
    }

    /** 在末尾追加一个非硬化索引。 */
    public BapPath append(long index) {
        List<Long> next = new ArrayList<>(indices);
        next.add(index);
        return new BapPath(next);
    }

    /** 在末尾追加整段路径。 */
    public BapPath append(BapPath other) {
        List<Long> next = new ArrayList<>(indices);
        next.addAll(other.indices);
        return new BapPath(next);
    }

    /** 在末尾追加一个索引；{@code hardened} 为真时置硬化位。 */
    public BapPath append(long index, boolean hardened) {
        return append(hardened ? index | HARDENED_BIT : index);
    }

    /** 取前 {@code size} 段。 */
    public BapPath prefix(int size) {
        if (size < 0 || size > indices.size()) {
            throw new IllegalArgumentException("前缀长度越界：" + size + "（路径段数 " + indices.size() + "）");
        }
        return new BapPath(indices.subList(0, size));
    }

    /** 最后一段的原始索引（不含硬化位）。 */
    public long lastIndex() {
        if (indices.isEmpty()) {
            throw new IllegalStateException("空路径没有末段索引");
        }
        return indices.get(indices.size() - 1) & ~HARDENED_BIT;
    }

    /** 最后一段是否硬化。 */
    public boolean lastHardened() {
        if (indices.isEmpty()) {
            throw new IllegalStateException("空路径没有末段索引");
        }
        return (indices.get(indices.size() - 1) & HARDENED_BIT) != 0;
    }

    /** 段数。 */
    public int size() {
        return indices.size();
    }

    /** 是否为空路径。 */
    public boolean isEmpty() {
        return indices.isEmpty();
    }

    /** 索引列表副本（含硬化位）。 */
    public List<Long> indices() {
        return indices;
    }

    /** 标准小写路径串（{@code m/44'/236'/1'/0/0}）；空路径输出 {@code m}。 */
    /**
     * 以**上限 M/H 形式**渲染（如 {@code M/0H/236H/0H/0/0}），等价旧库 {@code HDUtils.formatPath}。
     *
     * <p>与 {@link #toString()}（{@code m/0'/236'/…}）**并存且各有用途**：
     * 配置常量（{@code DefaultBapBaseConfig.DEFAULT_ROOT_PATH} 等）与本类实例暴露的
     * {@code rootPath}/{@code currentPath} 沿用上限形式（BapVariantContractTest 与 BapBaseLifecycleTest
     * 均按此断言），而 {@code toString()} 的规范形式由 BapPathTest 固定。
     */
    public String toDisplayPath() {
        if (indices.isEmpty()) {
            return "M";
        }
        StringBuilder out = new StringBuilder("M");
        for (Long index : indices) {
            boolean hardened = (index & HARDENED_BIT) != 0;
            out.append('/').append(index & ~HARDENED_BIT);
            if (hardened) {
                out.append('H');
            }
        }
        return out.toString();
    }

    @Override
    public String toString() {
        if (indices.isEmpty()) {
            return "m";
        }
        StringBuilder out = new StringBuilder("m");
        for (Long index : indices) {
            boolean hardened = (index & HARDENED_BIT) != 0;
            out.append('/').append(index & ~HARDENED_BIT);
            if (hardened) {
                out.append('\'');
            }
        }
        return out.toString();
    }

    /**
     * 在新 SDK 的 HD 节点上按本路径派生；
     * 末段用 {@code deriveChild}（与旧库 {@code deriveChild(..., last)} 的语义一致）。
     */
    public HD derive(HD master) {
        if (indices.isEmpty()) {
            return master;
        }
        HD parent = indices.size() == 1 ? master : master.derive(prefix(indices.size() - 1).toString());
        return parent.deriveChild(indices.get(indices.size() - 1));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BapPath value && value.indices.equals(indices);
    }

    @Override
    public int hashCode() {
        return Objects.hash(indices);
    }
}
