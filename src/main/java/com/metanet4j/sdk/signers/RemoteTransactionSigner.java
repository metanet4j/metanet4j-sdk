package com.metanet4j.sdk.signers;

import cn.hutool.core.codec.Base64;
import com.metanet4j.sdk.context.PreSignHashContext;
import com.metanet4j.sdk.script.ScriptExtend;
import com.metanet4j.sdk.script.SigHashExtend;
import io.bitcoinsv.bitcoinjsv.core.ECKey;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import io.bitcoinsv.bitcoinjsv.ecc.ECDSASignature;
import io.bitcoinsv.bitcoinjsv.ecc.TransactionSignature;
import io.bitcoinsv.bitcoinjsv.msg.Translate;
import io.bitcoinsv.bitcoinjsv.msg.protocol.Transaction;
import io.bitcoinsv.bitcoinjsv.msg.protocol.TransactionInput;
import io.bitcoinsv.bitcoinjsv.script.Script;
import io.bitcoinsv.bitcoinjsv.script.ScriptVerifyFlag;
import io.bitcoinsv.bitcoinjsv.script.SigHash;
import io.bitcoinsv.bitcoinjsv.signers.TransactionSigner;
import io.bitcoinsv.bitcoinjsv.temp.KeyBag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;

public class RemoteTransactionSigner implements TransactionSigner {
    private static final Logger log = LoggerFactory.getLogger(RemoteTransactionSigner.class);

    /**
     * Verify flags that are safe to use when testing if an input is already
     * signed.
     */
    private static final EnumSet<ScriptVerifyFlag> MINIMUM_VERIFY_FLAGS = EnumSet.of(ScriptVerifyFlag.P2SH,
            ScriptVerifyFlag.NULLDUMMY);


    @Override
    public boolean signInputs(ProposedTransaction signingTx, KeyBag keyBag) {
        ExtendForSignTransaction propTx = (ExtendForSignTransaction) signingTx;

        Transaction tx = propTx.partialTx;
        int numInputs = tx.getInputs().size();
        for (int i = 0; i < numInputs; i++) {
            TransactionInput txIn = tx.getInput(i);

            if (txIn.getConnectedOutput() == null) {
                log.warn("Missing connected output, assuming input {} is already signed.", i);
                continue;
            }
            Script scriptPubKey = txIn.getConnectedOutput().getScriptPubKey();

            try {
//
                Sha256Hash hash = SigHashExtend.hashForForkIdSignature(Translate.toTx(tx), i, scriptPubKey.getProgram(), tx.getInput(i).getConnectedOutput().getValue(), SigHash.Flags.ALL, propTx.isAnyoneCanPay());
                PreSignHashContext signedHashContext = PreSignHashUtils.getSignedHashContext(hash);
                TransactionSignature signature = new TransactionSignature(ECDSASignature.decodeFromDER(Base64.decode(signedHashContext.getSigB64())), propTx.getHashFlags(), propTx.isAnyoneCanPay(), true);

                txIn.setScriptSig(
                        scriptPubKey.createEmptyInputScript(signedHashContext.getPublicKey().getPubKeyBytes(), scriptPubKey));
                Script inputScript = txIn.getScriptSig();
                int sigIndex = 0;
                inputScript = new ScriptExtend(scriptPubKey.getProgram()).getScriptSigWithSignature(inputScript, signature.encodeToBitcoin(), sigIndex);
                txIn.setScriptSig(inputScript);
            } catch (ECKey.KeyIsEncryptedException e) {
                throw e;
            } catch (ECKey.MissingPrivateKeyException e) {
                log.warn("No private key in keypair for input {}", i);
            }

        }
        return true;
    }

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public byte[] serialize() {
        return new byte[0];
    }

    @Override
    public void deserialize(byte[] data) {

    }
}
