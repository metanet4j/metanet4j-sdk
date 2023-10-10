package com.metanet4j.sdk.transcation;

import com.google.common.collect.Lists;
import com.metanet4j.sdk.EcKeyLiteExtend;
import com.metanet4j.sdk.RemoteSignType;
import com.metanet4j.sdk.SignType;
import com.metanet4j.sdk.bap.BapBase;
import com.metanet4j.sdk.exception.SignErrorException;
import com.metanet4j.sdk.input.TransactionInputEnhance;
import com.metanet4j.sdk.input.TransactionOutPointEnhance;
import com.metanet4j.sdk.output.TransactionOutputEnhance;
import com.metanet4j.sdk.script.ScriptExtend;
import com.metanet4j.sdk.sigma.Sigma;
import com.metanet4j.sdk.sigma.SignResponse;
import com.metanet4j.sdk.signers.TransactionExtend;
import com.metanet4j.sdk.utils.RedeemDataExtend;
import com.metanet4j.sdk.utils.TxHelperExtend;
import com.metanet4j.sdk.utxo.UTXOProvider;
import io.bitcoinsv.bitcoinjsv.core.*;
import io.bitcoinsv.bitcoinjsv.msg.protocol.*;
import io.bitcoinsv.bitcoinjsv.params.Net;
import io.bitcoinsv.bitcoinjsv.script.Script;
import io.bitcoinsv.bitcoinjsv.script.ScriptBuilder;
import io.bitcoinsv.bitcoinjsv.script.interpreter.ScriptExecutionException;
import io.bitcoinsv.bitcoinjsv.signers.LocalTransactionSigner;
import io.bitcoinsv.bitcoinjsv.signers.TransactionSigner;
import io.bitcoinsv.bitcoinjsv.temp.KeyBag;
import io.bitcoinsv.bitcoinjsv.temp.RedeemData;
import lombok.Data;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;


@Data
public class TransactionBuilder {

    protected Net net;

    protected KeyBag keyBag;

    protected Transaction targetTransaction;

    protected TransactionSigner transactionSigner;

    protected Address changeAddress;

    protected Coin feePerKb = Coin.valueOf(500);

    protected Sigma sigma;


    public TransactionBuilder(KeyBag keyBag) {
        this(Net.MAINNET, keyBag);

    }

    public TransactionBuilder(KeyBag keyBag,TransactionSigner transactionSigner) {
        this(Net.MAINNET, null,transactionSigner,keyBag);

    }

    public TransactionBuilder(Net net, KeyBag keyBag) {
        this(net, null, keyBag);
    }

    public TransactionBuilder(Net net, Transaction targetTransaction, KeyBag keyBag) {
        this(net, targetTransaction, null, keyBag);

    }

    public TransactionBuilder(Net net, Transaction targetTransaction, TransactionSigner transactionSigner,
            KeyBag keyBag) {
        this.net = net;
        if (targetTransaction == null) {
            this.targetTransaction = new Transaction(net);
        } else {
            this.targetTransaction = targetTransaction;
        }
        if (transactionSigner == null) {
            this.transactionSigner = new LocalTransactionSigner();
        } else {
            this.transactionSigner = transactionSigner;
        }
        this.keyBag = keyBag;
    }


    public TransactionBuilder addInput(AddressLite addressLite, UTXOProvider utxoProvider) {
        addInputs(utxoProvider.listUxtos(Lists.newArrayList(addressLite)));
        return this;
    }

    public TransactionBuilder addInputs(List<AddressLite> addressLiteList, UTXOProvider utxoProvider) {
        addInputs(utxoProvider.listUxtos(addressLiteList));
        return this;
    }

    public TransactionBuilder addInputs(List<UTXO> utxo) {
        utxo.stream().forEach(o -> {
            addInput(o);

        });
        return this;
    }

    public TransactionBuilder addInput(UTXO o) {
        TransactionOutput output = new TransactionOutputEnhance(this.net, null, o.getValue(),
                Address.fromBase58(this.net.params(),
                        o.getAddress()), (int) o.getIndex()
        );

        TransactionOutPoint transactionOutPointEnhance = new TransactionOutPointEnhance(net, output, o.getHash());
        TransactionInputEnhance transactionInputEnhance = new TransactionInputEnhance(net, this.targetTransaction,
                transactionOutPointEnhance, o.getValue());

        targetTransaction.addInput(transactionInputEnhance);
        return this;
    }

    public TransactionBuilder addInput(TransactionOutput output) {
        targetTransaction.addInput(output);
        return this;
    }


    public TransactionBuilder addOutput(Coin value, Address address) {
        targetTransaction.addOutput(new TransactionOutput(net, targetTransaction, value, address));
        return this;
    }

    public TransactionBuilder addDataOutput(LockingScriptBuilder lockingScriptBuilder) {
        targetTransaction.addOutput(new TransactionOutput(net, targetTransaction, Coin.ZERO,
                lockingScriptBuilder.getLockingScript().getProgram()));
        return this;
    }

    /**
     * Add remote signature
     *
     * @param sigma
     * @param remoteSignType
     * @return
     */
    public TransactionBuilder addRemoteSigmaSign(Sigma sigma, RemoteSignType remoteSignType) {
        sigma.setTransaction(targetTransaction);
        SignResponse sign = sigma.signByRemote(remoteSignType);
        targetTransaction = sign.getSignedTx();
        this.sigma = sigma;
        return this;
    }

    public TransactionBuilder addSigmaSign(BapBase bapBase) {
        return addSigmaSign(bapBase, SignType.ROOT);
    }

    public TransactionBuilder addSigmaSign(BapBase bapBase, SignType signType) {
        Sigma sigma = new Sigma(targetTransaction);
        SignResponse sign = null;
        if (signType == SignType.CURRENT) {
            sign = sigma.sign(EcKeyLiteExtend.fromPrivate(bapBase.getCurrentPrivateKey().getPrivKey()));
        } else if (signType == SignType.PREVIOUS) {
            sign = sigma.sign(EcKeyLiteExtend.fromPrivate(bapBase.getPreviousPrivateKey().getPrivKey()));
        } else if (signType == SignType.ROOT) {
            sign = sigma.sign(EcKeyLiteExtend.fromPrivate(bapBase.getRootPrivateKey().getPrivKey()));
        } else {
            sign = sigma.sign(EcKeyLiteExtend.fromPrivate(bapBase.getOrdPrivateKey().getPrivKey()));
        }
        targetTransaction = sign.getSignedTx();
        this.sigma = sigma;
        return this;
    }


    public TransactionBuilder changeAddress(Address address) {
        addChangeOutput(address);
        return this;
    }

    public void addChangeOutput(Address address) {
        this.changeAddress = address;

        Coin changeAmount = calculateChangeAmount(targetTransaction, address, this.feePerKb);
        targetTransaction.addOutput(new TransactionOutput(net, targetTransaction, changeAmount, address));
    }

    /**
     * @param targetTransaction
     * @param address
     * @param feePerKb
     * @return
     */
    private Coin calculateChangeAmount(Transaction targetTransaction, Address address, Coin feePerKb) {

        Coin changeAmount = Coin.ZERO;
        long size = 0;
        TransactionOutput changeOutput = new TransactionOutput(net, targetTransaction, changeAmount, address);

        size += changeOutput.getMessageSize() + VarInt.sizeOf(targetTransaction.getOutputs().size() + 1);
        size += targetTransaction.unsafeBitcoinSerialize().length;

        for (TransactionInput transactionInput : targetTransaction.getInputs()) {

            Script script = transactionInput.getConnectedOutput().getScriptPubKey();
            RedeemDataExtend redeemData = TxHelperExtend.getConnectedRedeemDataExtend(transactionInput.getOutpoint(), keyBag);
            size += new ScriptExtend(script.getProgram()).getNumberOfBytesRequiredToSpend(
                    redeemData.getFullKey() == null ? -1 : redeemData.getFullKey().getPubKey().length,
                    redeemData.redeemScript);
        }
        Coin fee = Coin.valueOf((long) Math.ceil(size * this.feePerKb.value / 1000));
        changeAmount = targetTransaction.getInputSum().subtract(targetTransaction.getOutputSum()).subtract(fee);
        return changeAmount;

    }


    public TransactionBuilder withFeeKb(Coin feePerKb) {
        this.feePerKb = feePerKb;
        return this;
    }


    public Transaction completeAndSignTx(KeyBag keyBag, boolean useForkId) throws SignErrorException {

        setEmptyInputScript(targetTransaction, keyBag);
        final TransactionSigner.ProposedTransaction proposedTransaction = new TransactionSigner.ProposedTransaction(
                TransactionExtend.toTransactionExtend(targetTransaction), useForkId);

        boolean b = transactionSigner.signInputs(proposedTransaction, keyBag);
        if (b) {
            return proposedTransaction.partialTx;
        }
        throw new SignErrorException();
    }

    public static String broadcast(String raw, Broadcaster broadcaster) {
        return broadcaster.broadcast(raw);
    }

    protected void setEmptyInputScriptHaveOrdinals(final Transaction outputTransaction, final KeyBag bag)
            throws ScriptExecutionException {
        int numInputs = outputTransaction.getInputs().size();
        for (int i = 0; i < numInputs; i++) {
            TransactionInput txIn = outputTransaction.getInput(i);
            Script scriptPubKey = txIn.getConnectedOutput().getScriptPubKey();
            RedeemDataExtend redeemData = TxHelperExtend.getConnectedRedeemDataExtend(txIn.getOutpoint(), bag);
            checkNotNull(redeemData, "Transaction exists in wallet that we cannot redeem: %s",
                    txIn.getOutpoint().getHash());
            if (TxHelperExtend.isSentToOrdinals(scriptPubKey)) {
                txIn.setScriptSig(
                        ScriptBuilder.createInputScript(null, redeemData.keys.get(0).getPubKey()));
            } else {
                txIn.setScriptSig(
                        scriptPubKey.createEmptyInputScript(redeemData.keys.get(0).getPubKey(), redeemData.redeemScript));
            }

        }
    }

    /**
     * @param outputTransaction
     * @param bag
     * @throws ScriptExecutionException
     */
    protected void setEmptyInputScript(final Transaction outputTransaction, final KeyBag bag)
            throws ScriptExecutionException {
        int numInputs = outputTransaction.getInputs().size();
        for (int i = 0; i < numInputs; i++) {
            TransactionInput txIn = outputTransaction.getInput(i);
            Script scriptPubKey = txIn.getConnectedOutput().getScriptPubKey();
            RedeemData redeemData = TxHelper.getConnectedRedeemData(txIn, bag);
            checkNotNull(redeemData, "Transaction exists in wallet that we cannot redeem: %s",
                    txIn.getOutpoint().getHash());
            txIn.setScriptSig(
                    scriptPubKey.createEmptyInputScript(redeemData.keys.get(0).getPubKey(), redeemData.redeemScript));
        }
    }

    public Sigma getSigma() {
        return sigma;
    }

    @FunctionalInterface
    public interface Broadcaster{

        String broadcast(String raw);
    }


}
