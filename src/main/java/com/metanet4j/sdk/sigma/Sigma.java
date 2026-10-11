package com.metanet4j.sdk.sigma;


import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.HexUtil;
import com.google.common.collect.Lists;
import com.metanet4j.base.aip.AipHelper;
import com.metanet4j.sdk.EcKeyLiteExtend;
import com.metanet4j.sdk.PrivateKey;
import com.metanet4j.sdk.SignType;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.bap.BapBaseCore;
import com.metanet4j.sdk.context.PreSignHashContext;
import com.metanet4j.sdk.remote.PreSignHash;
import com.metanet4j.sdk.remote.PreSignHashUtils;
import com.metanet4j.sdk.remote.RemoteSignType;
import com.metanet4j.sdk.utils.ScriptHelper;
import com.metanet4j.sdk.utils.UtilsExtend;
import com.metanet4j.base.bsv.BsvAddress;
import com.metanet4j.base.bsv.BsvHashBytes;
import com.metanet4j.base.bsv.BsvNetwork;
import com.metanet4j.bsv.script.OP;
import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.script.ScriptChunk;
import com.metanet4j.bsv.transaction.Transaction;
import com.metanet4j.bsv.transaction.TransactionInput;
import com.metanet4j.bsv.transaction.TransactionOutput;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;


import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Data
@Slf4j
public class Sigma implements PreSignHash {

    public static final String SIGMA_PREFIX = "5349474D41";
    private BsvHashBytes inputHash;
    private BsvHashBytes dataHash;
    private Transaction transaction;
    private int sigmaInstance;
    private int refVin;
    private int targetVout;
    private Sig sig;
    private BapBaseCore bapBaseCore;
    private boolean remoteSign;
    private SignType signType = SignType.CURRENT;
    private RemoteSignType remoteSignType;
    private boolean isHaveSign;


    public Sigma(BapBaseCore bapBaseCore, Transaction transaction) {
        this(bapBaseCore, transaction, 0, 0, 0);
    }

    public Sigma(BapBaseCore bapBaseCore, Transaction transaction, int targetVout) {
        this(bapBaseCore, transaction, targetVout, 0, 0);
    }

    public Sigma(BapBaseCore bapBaseCore, Transaction transaction, SignType signType) {
        this(bapBaseCore, transaction, 0, 0, 0);
        if (signType == null) {
            this.signType = SignType.CURRENT;
        } else {
            this.signType = signType;
        }

    }

    public Sigma(BapBaseCore bapBaseCore, Transaction transaction, boolean remoteSign, RemoteSignType remoteSignType) {
        this(bapBaseCore, transaction, 0, 0, 0);
        this.remoteSign = remoteSign;
        if (remoteSignType == null) {
            this.remoteSignType = RemoteSignType.CURRENT;
        } else {
            this.remoteSignType = remoteSignType;
        }
    }


    public Sigma(BapBaseCore bapBaseCore, Transaction transaction, int targetVout, int sigmaInstance, int refVin) {
        this.bapBaseCore = bapBaseCore;
        this.transaction = Objects.requireNonNull(transaction, "transaction");
        this.targetVout = targetVout;
        this.refVin = refVin;
        this.sigmaInstance = sigmaInstance;
        this.sig = getSig();
        setHashes();
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = Objects.requireNonNull(transaction, "transaction");
        this.targetVout = 0;
        this.refVin = 0;
        this.sigmaInstance = 0;
        this.sig = getSig();
        setHashes();
    }

    private SignResponse sigM(String signature, String address) {
        int vin = (this.refVin == -1) ? this.targetVout : this.refVin;

        Script signedScript = new Script()
                .writeBin(toBoxed(HexUtil.decodeHex(SIGMA_PREFIX)))
                .writeBin(toBoxed(Algorithm.BSM.toString().getBytes()))
                .writeBin(toBoxed(address.getBytes()))
                .writeBin(toBoxed(Base64.decode(signature)))
                .writeBin(toBoxed(String.valueOf(vin).getBytes()));

        this.sig = new Sig(address, signature, Algorithm.BSM.toString(), vin, this.targetVout);
        List<ScriptChunk> newScriptAsm = new ArrayList<>(com.metanet4j.sdk.utils.ScriptHelper.chunksOf(this.getTargetTxOut().lockingScript));
        int existingCount = getSigInstanceCount();
        if (sigmaInstance > existingCount) {
            throw new IllegalArgumentException("Sigma instance is out of range: " + sigmaInstance);
        }
        if (sigmaInstance < existingCount) {
            int sigIndex = getSigInstancePosition();
            for (int i = 0; i < com.metanet4j.sdk.utils.ScriptHelper.chunksOf(signedScript).size(); i++) {
                newScriptAsm.set(sigIndex + i, com.metanet4j.sdk.utils.ScriptHelper.chunksOf(signedScript).get(i));
            }
        } else {
            boolean containsOpReturn = newScriptAsm.stream()
                    .anyMatch(chunk -> chunk.op == OP.OP_RETURN);
            String separator = containsOpReturn ? "|" : "OP_RETURN";
            newScriptAsm.addAll(new Script().writeBin(toBoxed(separator.getBytes())).getChunks());
            newScriptAsm.addAll(com.metanet4j.sdk.utils.ScriptHelper.chunksOf(signedScript));
        }

        // Clone the transaction and set the output script.
        Transaction signedTx = this.transaction;
        Script script = new Script(new ArrayList<>(newScriptAsm));
        TransactionOutput signedTxOut = new TransactionOutput(script,
                transaction.outputs.get(targetVout).satoshis);
        ArrayList<TransactionOutput> transactionOutputs = new ArrayList<>(signedTx.outputs);
        transactionOutputs.set(targetVout, signedTxOut);
        signedTx.outputs = transactionOutputs;


        SignResponse response = new SignResponse();
        response.setSigmaScript(signedScript);
        response.setSignedTx(signedTx);
        response.setSignature(this.sig.getSignature());


        this.transaction = signedTx;
        return response;

    }


    public SignResponse sign() {
        this.isHaveSign = true;
        log.info("sign messagehash:" + getMessageHash().toString());
        if (this.remoteSign) {
            PreSignHashContext preSignHashContext = getSigByRemote(getPreSignHashContext().getPreSignHash());
            return sigM(preSignHashContext.getSigB64(), preSignHashContext.getSignAddress());
        } else {
            EcKeyLiteExtend signPrivateKey = null;
            if (signType == SignType.CURRENT) {
                signPrivateKey = EcKeyLiteExtend.fromPrivate(bapBaseCore.getCurrentPrivateKey().getPrivKey());
            } else if (signType == SignType.PREVIOUS) {
                signPrivateKey = EcKeyLiteExtend.fromPrivate(bapBaseCore.getPreviousPrivateKey().getPrivKey());
            } else if (signType == SignType.ROOT) {
                signPrivateKey = EcKeyLiteExtend.fromPrivate(bapBaseCore.getRootPrivateKey().getPrivKey());
            } else {
                signPrivateKey = EcKeyLiteExtend.fromPrivate(bapBaseCore.getOrdPrivateKey().getPrivKey());
            }
            String s = signPrivateKey.signMessage(getMessageHash().getBytes());
            return sigM(s, AddressEnhance.fromPublicKey(signPrivateKey.getPublicKey()).toBase58());
        }
    }


    public boolean verify() {

        if (sig == null) {
            throw new IllegalArgumentException("No signature provided");
        }

        BsvAddress address = BsvAddress.fromBase58(BsvNetwork.MAINNET, this.sig.getAddress());
        log.info("verify sigma sign the ,inputhash:" + this.inputHash.toString());
        log.info("verify sigma sign the ,datashash:" + this.dataHash.toString());
        log.info("verify sigma sign the ,message:" + getMessageHash().toString());
        log.info("verify sigma sign the ,sign address:" + address.toString());
        log.info("verify sigma sign the ,sig:" + this.sig.getSignature());
        log.info("verify sigma sign the ,vin:" + this.sig.getVin());

        return AipHelper.verifySign(address.toBase58(), this.sig.getSignature(), getMessageHash().getBytes());

    }


    public Sig getSig() {

        List<Sig> instances = new ArrayList<>();
        TransactionOutput output = transaction.outputs.get(targetVout);
        Script outputScript = output.lockingScript;

        List<ScriptChunk> chunks = com.metanet4j.sdk.utils.ScriptHelper.chunksOf(outputScript);

        for (int i = 0; i < chunks.size(); i++) {
            if (!ScriptHelper.hasPushData(chunks.get(i))) {
                continue;
            }
            if (HexUtil.encodeHexStr(ScriptHelper.getPushData(chunks.get(i))).equalsIgnoreCase(SIGMA_PREFIX)) {
                //TODO
                String algorithm = ScriptHelper.getPushDataAsString(chunks.get(i + 1));
                String address = ScriptHelper.getPushDataAsString(chunks.get(i + 2));
                String signature = Base64.encode(ScriptHelper.getPushData(chunks.get(i + 3)));

                int vin = Integer.parseInt(ScriptHelper.getPushDataAsString(chunks.get(i + 4)));
                instances.add(new Sig(address, signature, algorithm, vin, targetVout));
                // fast forward to the next possible instance position
                // 3 fields + 1 extra for the "|" separator
                i += 4;
            }
        }

        if (instances.size() == 0) {
            return this.sig;
        } else {
            return instances.get(this.sigmaInstance);
        }


    }

    public int getSigInstanceCount() {
        Script scriptPubKey = getTargetTxOut().lockingScript;
        List<ScriptChunk> chunks = com.metanet4j.sdk.utils.ScriptHelper.chunksOf(scriptPubKey);

        int count = 0;
        for (ScriptChunk chunk : chunks) {
            if (!ScriptHelper.hasPushData(chunk)) {
                continue;
            }
            if (HexUtil.encodeHexStr(ScriptHelper.getPushData(chunk)).equalsIgnoreCase(SIGMA_PREFIX)) {
                count++;
            }
        }
        return count;
    }

    public int getSigInstancePosition() {
        Script scriptPubKey = getTargetTxOut().lockingScript;
        List<ScriptChunk> chunks = com.metanet4j.sdk.utils.ScriptHelper.chunksOf(scriptPubKey);

        int instance = 0;
        for (int i = 0; i < chunks.size(); i++) {
            if (!ScriptHelper.hasPushData(chunks.get(i))) {
                continue;
            }
            if (HexUtil.encodeHexStr(ScriptHelper.getPushData(chunks.get(i))).equalsIgnoreCase(SIGMA_PREFIX)) {
                if (instance == sigmaInstance) {
                    return i;
                }
                instance++;
            }
        }
        return -1;
    }

    public void setHashes() {
        this.inputHash = getInputHash();
        this.dataHash = getDataHash();
    }

    public void setTargetVout(int targetVout) {
        this.targetVout = targetVout;
    }

    public void setSigmaInstance(int sigmaInstance) {
        this.sigmaInstance = sigmaInstance;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public TransactionOutput getTargetTxOut() {
        return this.transaction.outputs.get(targetVout);
    }

    @Override
    public PreSignHashContext getPreSignHashContext() {
        PreSignHashContext context = new PreSignHashContext();
        byte[] data = UtilsExtend.formatMessageForSigning(this.getMessageHash().getBytes());
        BsvHashBytes hash = BsvHashBytes.twiceOf(data);
        context.setPreSignHash(hash);
        context.setSignAddress(this.bapBaseCore.getSignAddress(remoteSignType));
        context.setRemoteSignType(this.remoteSignType);
        return context;
    }


    public BsvHashBytes getMessageHash() {

        if (this.inputHash == null || this.dataHash == null) {
            throw new IllegalStateException("Input hash and data hash must be set");
        }

        byte[] combined = concatenate(this.inputHash.getBytes(),
                this.dataHash.getBytes());

        return BsvHashBytes.of(combined);

    }

    public BsvHashBytes getDataHash() {

        TransactionOutput output = this.transaction.outputs.get(this.targetVout);
        Script scriptPubKey = output.lockingScript;
        List<ScriptChunk> scriptChunks = com.metanet4j.sdk.utils.ScriptHelper.chunksOf(scriptPubKey);

        int occurrences = 0;
        for (int i = 0; i < scriptChunks.size(); i++) {
            if (!ScriptHelper.hasPushData(scriptChunks.get(i))) {
                continue;
            }
            if (HexUtil.encodeHexStr(ScriptHelper.getPushData(scriptChunks.get(i))).equalsIgnoreCase(SIGMA_PREFIX)) {
                if (occurrences == this.sigmaInstance) {
                    List<ScriptChunk> dataChunks = scriptChunks.subList(0, i - 1);
                    return BsvHashBytes.of(new Script(new ArrayList<>(dataChunks)).toUint8Array());
                }
                occurrences++;
            }
        }

        // Use full script if index not found
        return BsvHashBytes.of(scriptPubKey.toUint8Array());

    }

    public BsvHashBytes getInputHash() {
        int vin;
        if (this.refVin == -1) {
            vin = this.targetVout;
        } else {
            vin = this.refVin;
        }
        return getInputHashByVin(vin);
    }

    private BsvHashBytes getInputHashByVin(int vin) {
        if (vin >= 0 && vin < transaction.inputs.size()) {
            TransactionInput input = transaction.inputs.get(vin);
            if (input != null) {
                byte[] outpoint = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN)
                        .put(sourceTxidBytes(input.sourceTXID))
                        .putInt(input.sourceOutputIndex).array();
                return BsvHashBytes.of(outpoint);
            }
        }
        // Use a dummy hash.
        // 注意：契约要求的是**全零哈希本身**（BsvHashBytes.ZERO_HASH），
        // 而 `BsvHashBytes.of(new byte[32])` 是对 32 个零字节做一次 SHA-256，
        // 得到的是非零值（实测 25295f0d…）——曾因此使「无输入时用 dummy 哈希」的断言失败。
        return BsvHashBytes.ZERO_HASH;
    }

    /**
     * Override this method to implement remote signature.
     *
     * @return
     */
    public PreSignHashContext getSigByRemote(BsvHashBytes hash) {
        return PreSignHashUtils.getSignedHashContext(hash);
    }



    /** 新 SDK 脚本写入需要 {@code List<Double>} 形式的字节。 */
    private static List<Double> toBoxed(byte[] data) {
        if (data == null) {
            return List.of();
        }
        Double[] values = new Double[data.length];
        for (int i = 0; i < data.length; i++) {
            values[i] = (double) (data[i] & 0xFF);
        }
        return List.of(values);
    }

    /**
     * 取 {@code sourceTXID} 的字节。
     *
     * <p><b>本方法经 §86 核查后按 §62.4 方案 C 收敛</b>：该字段在新 SDK 中是 {@code Object}，
     * 原实现对其做了 {@code null}／{@code BsvHashBytes}／{@code byte[]}／{@code String}／{@code List}
     * 五类 {@code instanceof} 分派。核查"谁会产生这些类型"得：
     *
     * <ul>
     *   <li>该字段的<b>默认值是哨兵 {@code Undefined.INSTANCE}</b>（<b>不是 {@code null}</b>）；</li>
     *   <li><b>本仓库生产侧的唯一赋值点</b>是 {@code SigHashExtend} 的
     *       {@code params.sourceTXID = txidOf(...)}，而 {@code txidOf} 返回<b>显示序 hex 的 {@code String}</b>。</li>
     * </ul>
     *
     * <p>故实践中只有两种可能：<b>{@code String}</b>（已解析）与 <b>{@code Undefined}</b>（从未解析）。
     * 其余四类<b>无任何生产者</b>、其分支不可达，按 §62.4 删除；
     * 并<b>保留抛错兜底</b>——它正对应 {@code Undefined} 这一<b>真实可达</b>状态：
     * 未解析的输入走到这里<b>应当报错</b>，而不是静默产出错误哈希。
     */
    private static byte[] sourceTxidBytes(Object sourceTXID) {
        if (sourceTXID instanceof String text) {
            // 【基线直跑取证】基线 Sigma.getInputHashByVin 用的是
            //     input.getOutpoint().getHash().getBytes()
            // 而旧库中该 getBytes() 与 toString() 输出**相同**（即**显示序**，不反转）。
            // 实测（670ee3d 直跑）：基线 outpoint 首 32 字节 = 5322ddd0…e0e1（显示序原样）。
            // 故此处必须按显示序**原样**解码，不能经 fromDisplayHex 再取 getBytes()（那会反转成内部序），
            // 否则整个 inputHash 不同、历史签名校验必然失败（SIGMA-VERIFY-001）。
            byte[] out = new byte[text.length() / 2];
            for (int i = 0; i < out.length; i++) {
                out[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
            }
            return out;
        }
        // 兜底：对应「字段仍是默认哨兵 Undefined」这一真实状态（见上）
        throw new IllegalArgumentException("sourceTXID 尚未解析为显示序 hex 串，无法取字节："
                + (sourceTXID == null ? "null" : sourceTXID.getClass().getName()));
    }




    /** 原用 BouncyCastle 系（spongycastle）的 ByteUtils.concatenate；R5 移除旧库传递依赖后改为本地实现。 */
    private static byte[] concatenate(byte[] first, byte[] second) {
        byte[] out = new byte[first.length + second.length];
        System.arraycopy(first, 0, out, 0, first.length);
        System.arraycopy(second, 0, out, first.length, second.length);
        return out;
    }
}
