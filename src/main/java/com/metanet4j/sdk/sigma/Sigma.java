package com.metanet4j.sdk.sigma;


import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.HexUtil;
import com.google.common.collect.Lists;
import com.metanet4j.base.aip.AipHelper;
import com.metanet4j.sdk.EcKeyLiteExtend;
import com.metanet4j.sdk.PrivateKey;
import com.metanet4j.sdk.RemoteSignType;
import com.metanet4j.sdk.address.AddressEnhance;
import com.metanet4j.sdk.utils.ScriptHelper;
import io.bitcoinsv.bitcoinjsv.core.AddressLite;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import io.bitcoinsv.bitcoinjsv.core.UnsafeByteArrayOutputStream;
import io.bitcoinsv.bitcoinjsv.core.Utils;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.msg.protocol.TransactionInput;
import io.bitcoinsv.bitcoinjsv.msg.protocol.TransactionOutput;
import io.bitcoinsv.bitcoinjsv.params.MainNetParams;
import io.bitcoinsv.bitcoinjsv.params.Net;
import io.bitcoinsv.bitcoinjsv.script.Script;
import io.bitcoinsv.bitcoinjsv.script.ScriptBuilder;
import io.bitcoinsv.bitcoinjsv.script.ScriptChunk;
import io.bitcoinsv.bitcoinjsv.script.ScriptOpCodes;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.spongycastle.pqc.math.linearalgebra.ByteUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Data
@Slf4j
public class Sigma {

    public static final String SIGMA_PREFIX = "5349474D41";
    private Sha256Hash inputHash;
    private Sha256Hash dataHash;
    private Transaction transaction;
    private int sigmaInstance;
    private int refVin;
    private int targetVout;
    private Sig sig;

    public Sigma(Transaction transaction) {
        this(transaction, 0, 0, 0);
    }

    public Sigma(Transaction transaction, int targetVout) {
        this(transaction, targetVout, 0, 0);
    }

    public Sigma(Transaction transaction, int targetVout, int sigmaInstance, int refVin) {
        this.transaction = transaction;
        this.targetVout = targetVout;
        this.refVin = refVin;
        this.sigmaInstance = sigmaInstance;
        this.sig = getSig();
        setHashes();
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
        this.targetVout = 0;
        this.refVin = 0;
        this.sigmaInstance = 0;
        this.sig = getSig();
        setHashes();
    }

    private SignResponse sigM(String signature, String address) {
        int vin = (this.refVin == -1) ? this.targetVout : this.refVin;

        ScriptBuilder asmScriptBuilder = new ScriptBuilder();
        Script signedScript = asmScriptBuilder
                .data(HexUtil.decodeHex(SIGMA_PREFIX))
                .data(Algorithm.BSM.toString().getBytes())
                .data(address.getBytes())
                .data(Base64.decode(signature))
                .data(String.valueOf(vin).getBytes())
                .build();

        this.sig = new Sig(address, signature, Algorithm.BSM.toString(), vin, this.targetVout);
        Sig existingSig = getSig();

        List<ScriptChunk> newScriptAsm = new ArrayList<>();
        List<ScriptChunk> existingAsm = this.getTargetTxOut().getScriptPubKey().getChunks();

        boolean containsOpReturn = existingAsm.stream().filter(s -> s.opcode == ScriptOpCodes.OP_RETURN).findFirst().isPresent();
        String separator = containsOpReturn ? "|" : "OP_RETURN";


        if (existingSig != null && this.sigmaInstance == getSigInstanceCount()) {
            // Get the location of the signature.
            int sigIndex = getSigInstancePosition();
            // Replace the old signature with a new one.
            if (sigIndex != -1) {
                existingAsm = existingAsm.subList(sigIndex, 5);
                existingAsm.addAll(signedScript.getChunks());

            }
        }

        newScriptAsm.addAll(existingAsm);
        ScriptBuilder newScriptBuilder = new ScriptBuilder();
        newScriptBuilder.data(separator.getBytes());
        newScriptAsm.addAll(newScriptBuilder.build().getChunks());
        newScriptAsm.addAll(signedScript.getChunks());

        // Clone the transaction and set the output script.
        Transaction signedTx = this.transaction;
        ScriptBuilder scriptBuilder = new ScriptBuilder();
        for (ScriptChunk chunk : newScriptAsm) {
            scriptBuilder.addChunk(chunk);
        }

        Script script = scriptBuilder.build();
        TransactionOutput signedTxOut = new TransactionOutput(Net.MAINNET, null, transaction.getOutput(targetVout).getValue(), script.getProgram());
        ArrayList<TransactionOutput> transactionOutputs = Lists.newArrayList(signedTx.getOutputs());
        transactionOutputs.set(targetVout, signedTxOut);
        signedTx.setOutputs(transactionOutputs);


        SignResponse response = new SignResponse();
        response.setSigmaScript(signedScript);
        response.setSignedTx(signedTx);
        response.setSignature(this.sig.getSignature());


        this.transaction = signedTx;
        return response;

    }

    /**
     * @param remoteSignType
     * @return
     */
    public SignResponse signByRemote(RemoteSignType remoteSignType) {
        Sig remoteSig = getSigByRemote(HexUtil.encodeHexStr(getMessageHash().getBytes()), remoteSignType);
        return sigM(remoteSig.getSignature(), remoteSig.getAddress());
    }


    public SignResponse sign(EcKeyLiteExtend privateKey) {
        System.out.println("sign messagehash:" + getMessageHash().toString());
        String s = privateKey.signMessage(getMessageHash().getBytes());
        return sigM(s, AddressEnhance.fromPrivateKey(new PrivateKey(privateKey)).toBase58());
    }


    public boolean verify() {

        if (sig == null) {
            throw new IllegalArgumentException("No signature provided");
        }

        if (getMessageHash() == null) {
            throw new IllegalStateException("No transaction data");
        }

        AddressLite address = AddressLite.fromBase58(MainNetParams.get(), this.sig.getAddress());
        log.debug("verify sigma sign the ,inputhash:" + this.inputHash.toString());
        log.debug("verify sigma sign the ,datashash:" + this.dataHash.toString());
        log.debug("verify sigma sign the ,message:" + getMessageHash().toString());
        log.debug("verify sigma sign the ,sign address:" + address.toString());
        log.debug("verify sigma sign the ,sig:" + this.sig.getSignature());
        log.debug("verify sigma sign the ,vin:" + this.sig.getVin());

        return AipHelper.verifySign(address.toString(), this.sig.getSignature(), getMessageHash().getBytes());

    }


    public Sig getSig() {

        List<Sig> instances = new ArrayList<>();
        TransactionOutput output = transaction.getOutput(targetVout);
        Script outputScript = output.getScriptPubKey();

        List<ScriptChunk> chunks = outputScript.getChunks();

        for (int i = 0; i < chunks.size(); i++) {
            if (!ScriptHelper.hasPushData(chunks.get(i))) {
                continue;
            }
            if (HexUtil.encodeHexStr(chunks.get(i).data()).equalsIgnoreCase(SIGMA_PREFIX)) {
                //TODO
                String algorithm = new String(chunks.get(i + 1).data(), StandardCharsets.UTF_8);
                String address = new String(chunks.get(i + 2).data(), StandardCharsets.UTF_8);
                String signature = Base64.encode(chunks.get(i + 3).data());

                int vin = Integer.parseInt(new String(chunks.get(i + 4).data(), StandardCharsets.UTF_8));
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
        Script scriptPubKey = getTargetTxOut().getScriptPubKey();
        List<ScriptChunk> chunks = scriptPubKey.getChunks();

        int count = 0;
        for (ScriptChunk chunk : chunks) {
            if (!ScriptHelper.hasPushData(chunk)) {
                continue;
            }
            if (HexUtil.encodeHexStr(chunk.data.data()).equalsIgnoreCase(SIGMA_PREFIX)) {
                count++;
            }
        }
        return count;
    }

    public int getSigInstancePosition() {
        Script scriptPubKey = getTargetTxOut().getScriptPubKey();
        List<ScriptChunk> chunks = scriptPubKey.getChunks();

        for (int i = 0; i < chunks.size(); i++) {
            if (!ScriptHelper.hasPushData(chunks.get(i))) {
                continue;
            }
            if (HexUtil.encodeHexStr(chunks.get(i).data()).equalsIgnoreCase(SIGMA_PREFIX)) {
                return i;
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
        TransactionOutput output = this.transaction.getOutput(targetVout);
        if (output != null) {
            return output;
        } else {
            return null;
        }
    }


    public Sha256Hash getMessageHash() {

        if (this.inputHash == null || this.dataHash == null) {
            throw new IllegalStateException("Input hash and data hash must be set");
        }

        byte[] combined = ByteUtils.concatenate(this.inputHash.getBytes(),
                this.dataHash.getBytes());

        return Sha256Hash.of(combined);

    }

    public Sha256Hash getDataHash() {

        if (this.transaction == null) {
            throw new IllegalArgumentException("No transaction provided");
        }

        TransactionOutput output = this.transaction.getOutput(this.targetVout);
        Script scriptPubKey = output.getScriptPubKey();
        List<ScriptChunk> scriptChunks = scriptPubKey.getChunks();

        int occurrences = 0;
        for (int i = 0; i < scriptChunks.size(); i++) {
            if (!ScriptHelper.hasPushData(scriptChunks.get(i))) {
                continue;
            }
            if (HexUtil.encodeHexStr(scriptChunks.get(i).data()).equalsIgnoreCase(SIGMA_PREFIX)) {
                if (occurrences == this.sigmaInstance) {
                    List<ScriptChunk> dataChunks = scriptChunks.subList(0, i - 1);
                    ScriptBuilder builder = new ScriptBuilder();
                    for (ScriptChunk scriptChunk : dataChunks) {
                        builder.addChunk(scriptChunk);
                    }
                    return Sha256Hash.of(builder.build().getProgram());
                }
                occurrences++;
            }
        }

        // Use full script if index not found
        return Sha256Hash.of(scriptPubKey.getProgram());

    }

    public Sha256Hash getInputHash() {
        int vin;
        if (this.refVin == -1) {
            vin = this.targetVout;
        } else {
            vin = this.refVin;
        }
        return getInputHashByVin(vin);
    }

    private Sha256Hash getInputHashByVin(int vin) {
        TransactionInput input = this.transaction.getInput(vin);

        if (input != null) {
            ByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(256);
            try {
                bos.write(input.getOutpoint().getHash().getBytes());
                Utils.uint32ToByteStreamLE(input.getOutpoint().getIndex(), bos);
                return Sha256Hash.of(bos.toByteArray());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        // Use a dummy hash.
        byte[] dummy = new byte[32];
        return Sha256Hash.of(dummy);
    }

    /**
     * Override this method to implement remote signature.
     *
     * @param encodeHexStr
     * @param remoteSignType
     * @return
     */
    public Sig getSigByRemote(String encodeHexStr, RemoteSignType remoteSignType) {
        return null;
    }


}