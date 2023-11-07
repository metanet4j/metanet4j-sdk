package com.metanet4j.sdk.remote;

import com.google.common.collect.Lists;
import com.metanet4j.sdk.RemoteSignType;
import com.metanet4j.sdk.bap.BapBaseCore;
import com.metanet4j.sdk.bap.RemoteBapBase;
import com.metanet4j.sdk.context.PreSignHashContext;
import com.metanet4j.sdk.exception.SignErrorException;
import com.metanet4j.sdk.sigma.Sigma;
import com.metanet4j.sdk.signers.ExtendForSignTransaction;
import com.metanet4j.sdk.signers.TransactionExtend;
import com.metanet4j.sdk.transcation.LockingScriptBuilder;
import com.metanet4j.sdk.transcation.TransactionBuilder;
import com.metanet4j.sdk.utils.TxHelperExtend;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import io.bitcoinsv.bitcoinjsv.msg.Translate;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.msg.protocol.TransactionInput;
import io.bitcoinsv.bitcoinjsv.script.Script;
import io.bitcoinsv.bitcoinjsv.script.SigHash;
import io.bitcoinsv.bitcoinjsv.signers.TransactionSigner;
import io.bitcoinsv.bitcoinjsv.temp.KeyBag;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class RemoteTransactionBuilder extends TransactionBuilder {

    public List<PreSignHashContext> bsmSignHashContexts;

    public RemoteTransactionBuilder(RemoteBapBase bapBaseCore, TransactionSigner transactionSigner) {
        super(bapBaseCore, transactionSigner);
    }


    @Override
    public TransactionBuilder addDataOutput(LockingScriptBuilder lockingScriptBuilder) {
        if (!lockingScriptBuilder.isHaveSign()) {
            bsmSignHashContexts.add(lockingScriptBuilder.getPreSignHashContext());
        }
        return super.addDataOutput(lockingScriptBuilder);
    }

    @Override
    public TransactionBuilder addSigmaSign(BapBaseCore bapBase, RemoteSignType remoteSignType) {
        Sigma sigma = new Sigma(bapBase, targetTransaction, true, remoteSignType);
        targetTransaction = sigma.sign().getSignedTx();
        this.sigma = sigma;
        return this;
    }

    @Override
    public Transaction completeAndSignTx(KeyBag keyBag, boolean useForkId) throws SignErrorException {
        final ExtendForSignTransaction proposedTransaction = new ExtendForSignTransaction(TransactionExtend.toTransactionExtend(this.getTargetTransaction()), SigHash.Flags.ALL, true, false);
        boolean b = transactionSigner.signInputs(proposedTransaction, keyBag);
        if (b) {
            return proposedTransaction.partialTx;
        }
        throw new SignErrorException();
    }

    @Override
    public List<PreSignHashContext> getAllBsmSignHash(boolean useForkId, boolean anyoneCanPay, BapBaseCore bapBase) {
        return this.bsmSignHashContexts;
    }

    @Override
    public List<PreSignHashContext> getTxInputSignHash(boolean anyoneCanPay, BapBaseCore bapBase) {
        return super.getTxInputSignHash(anyoneCanPay, bapBase);
    }

    @Override
    public List<PreSignHashContext> getTxInputSignHash(SigHash.Flags flags, boolean anyoneCanPay, BapBaseCore bapBase) {
        List<PreSignHashContext> preSignHashContexts = Lists.newArrayList();

        int numInputs = this.targetTransaction.getInputs().size();
        for (int i = 0; i < numInputs; i++) {
            PreSignHashContext preSignHashContext = new PreSignHashContext();
            TransactionInput txIn = this.targetTransaction.getInput(i);
            if (txIn.getConnectedOutput() == null) {
                log.warn("Missing connected output, assuming input {} is already signed.", i);
                continue;
            }
            Script scriptPubKey = txIn.getConnectedOutput().getScriptPubKey();


            Sha256Hash hash = SigHash.hashForForkIdSignature(Translate.toTx(this.targetTransaction), i, scriptPubKey.getProgram(), this.targetTransaction.getInput(i).getConnectedOutput().getValue(), flags, anyoneCanPay);
            preSignHashContext.setPreSignHash(hash);

            if (TxHelperExtend.isSentToOrdinals(scriptPubKey)) {
                preSignHashContext.setRemoteSignType(RemoteSignType.ORD);
                preSignHashContext.setSignAddress(bapBase.getSignAddress(RemoteSignType.ORD));
            } else {
                preSignHashContext.setRemoteSignType(RemoteSignType.PAYMENT);
                preSignHashContext.setSignAddress(bapBase.getSignAddress(RemoteSignType.PAYMENT));
            }
            preSignHashContexts.add(preSignHashContext);
        }

        return preSignHashContexts.stream().map(o -> {
            o.setFee(this.getFee());
            return o;

        }).collect(Collectors.toList());
    }
}
