package com.metanet4j.sdk.signers;

import cn.hutool.core.util.StrUtil;
import com.metanet4j.sdk.exception.SignErrorException;
import com.metanet4j.sdk.script.ScriptExtend;
import com.metanet4j.sdk.script.SigHashExtend;
import com.metanet4j.sdk.utils.RedeemDataExtend;
import com.metanet4j.sdk.utils.TxHelperExtend;
import io.bitcoinsv.bitcoinjsv.core.ECKey;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
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

public class OrdinalsTransactionSigner implements TransactionSigner {
    private static final Logger log = LoggerFactory.getLogger(OrdinalsTransactionSigner.class);

    /**
     * Verify flags that are safe to use when testing if an input is already
     * signed.
     */
    private static final EnumSet<ScriptVerifyFlag> MINIMUM_VERIFY_FLAGS = EnumSet.of(ScriptVerifyFlag.P2SH,
            ScriptVerifyFlag.NULLDUMMY);


    public static boolean signInput(
            Transaction tx,
            int inputIndex,
            KeyBag keyBag,
            SigHash.Flags hashType,
            boolean anyoneCanPay) {
        TransactionInput txIn = tx.getInput(inputIndex);
        if (txIn.getConnectedOutput() == null) {
            log.error("when signInput,Missing connected output, assuming input {} is already signed.", inputIndex);
            throw new SignErrorException(StrUtil.format("when signInput,Missing connected output, assuming input {} is already signed.", inputIndex));
        }
        RedeemDataExtend redeemData = TxHelperExtend.getConnectedRedeemDataExtend(txIn.getOutpoint(), keyBag);

        Script scriptPubKey = redeemData.redeemScript;
        ECKey key;
        if ((key = redeemData.getFullKey()) == null) {
            log.warn("No local key found for input {}", inputIndex);
        }

        Script inputScript = txIn.getScriptSig();
        // script here would be either a standard CHECKSIG program for pay-to-address or pay-to-pubkey inputs or
        // a CHECKMULTISIG program for P2SH inputs
        byte[] script = scriptPubKey.getProgram();
        try {
            Sha256Hash hash = SigHashExtend.hashForForkIdSignature(Translate.toTx(tx), inputIndex, script, tx.getInput(inputIndex).getConnectedOutput().getValue(), hashType, anyoneCanPay);
            TransactionSignature signature = new TransactionSignature(key.sign(hash), hashType, anyoneCanPay, true);


            // at this point we have incomplete inputScript with OP_0 in place of one or more signatures. We already
            // have calculated the signature using the local key and now need to insert it in the correct place
            // within inputScript. For pay-to-address and pay-to-key script there is only one signature and it always
            // goes first in an inputScript (sigIndex = 0). In P2SH input scripts we need to figure out our relative
            // position relative to other signers.  Since we don't have that information at this point, and since
            // we always run first, we have to depend on the other signers rearranging the signatures as needed.
            // Therefore, always place as first signature.
            int sigIndex = 0;
            inputScript = new ScriptExtend(script).getScriptSigWithSignature(inputScript, signature.encodeToBitcoin(), sigIndex);
            txIn.setScriptSig(inputScript);
        } catch (ECKey.KeyIsEncryptedException e) {
            throw e;
        } catch (ECKey.MissingPrivateKeyException e) {
            log.warn("No private key in keypair for input {}", inputIndex);
        }
        return true;
    }

    @Override
    public boolean signInputs(TransactionSigner.ProposedTransaction signingTx, KeyBag keyBag) {
        ExtendForSignTransaction propTx = (ExtendForSignTransaction) signingTx;

        Transaction tx = propTx.partialTx;
        int numInputs = tx.getInputs().size();
        for (int i = 0; i < numInputs; i++) {
            TransactionInput txIn = tx.getInput(i);
            if (txIn.getConnectedOutput() == null) {
                log.warn("Missing connected output, assuming input {} is already signed.", i);
                continue;
            }

//            try {
//                // We assume if its already signed, its hopefully got a SIGHASH type that will not invalidate when
//                // we sign missing pieces (to check this would require either assuming any signatures are signing
//                // standard output types or a way to get processed signatures out of script execution)
//                ScriptUtils_legacy.correctlySpends(txIn.getScriptSig(), tx, i, txIn.getConnectedOutput().getScriptPubKey(), txIn.getConnectedOutput().getValue(), MINIMUM_VERIFY_FLAGS);
//                log.warn("Input {} already correctly spends output, assuming SIGHASH type used will be safe and skipping signing.", i);
//                continue;
//            } catch (ScriptExecutionException e) {
//                // Expected.
//            }

            RedeemDataExtend redeemData = TxHelperExtend.getConnectedRedeemDataExtend(txIn.getOutpoint(), keyBag);

            Script scriptPubKey = txIn.getConnectedOutput().getScriptPubKey();
            ECKey key;
            // locate private key in redeem data. For pay-to-address and pay-to-key inputs RedeemData will always contain
            // only one key (with private bytes). For P2SH inputs RedeemData will contain multiple keys, one of which MAY
            // have private bytes
            if ((key = redeemData.getFullKey()) == null) {
                log.warn("No local key found for input {}", i);
                continue;
            }

            Script inputScript = txIn.getScriptSig();
            byte[] script = redeemData.redeemScript.getProgram();
            try {

//                Sha256Hash hash = SigHashExtend.hashForForkIdSignature(Translate.toTx(tx), i, script, tx.getInput(i).getConnectedOutput().getValue(), propTx.getHashFlags(), propTx.isAnyoneCanPay());
//                TransactionSignature signature =new  TransactionSignature(key.sign(hash), propTx.getHashFlags(), propTx.isAnyoneCanPay(), true);
                TransactionSignature signature = propTx.isUseForkId() ?
                        tx.calculateForkIdSignature(i, key, script, tx.getInput(i).getConnectedOutput().getValue(), SigHash.Flags.ALL, false) :
                        tx.calculateLegacySignature(i, key, script, SigHash.Flags.ALL, false);
                int sigIndex = 0;
                inputScript = new ScriptExtend(script).getScriptSigWithSignature(inputScript, signature.encodeToBitcoin(), sigIndex);
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
