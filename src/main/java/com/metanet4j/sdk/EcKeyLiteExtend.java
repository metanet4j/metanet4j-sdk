package com.metanet4j.sdk;

import com.metanet4j.sdk.utils.UtilsExtend;
import io.bitcoinsv.bitcoinjsv.core.ECKeyLite;
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash;
import io.bitcoinsv.bitcoinjsv.core.Utils;
import io.bitcoinsv.bitcoinjsv.crypto.KeyCrypterException;
import io.bitcoinsv.bitcoinjsv.ecc.ECDSASignature;
import org.spongycastle.crypto.params.KeyParameter;
import org.spongycastle.math.ec.ECPoint;
import org.spongycastle.util.encoders.Base64;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * only for BSM sign message
 * Inherits from ECKeyLite and provides the signMessage method to support signing byte arrays.
 * There is a bug in the signMessage method of ECKeyLite when signing the getMessageHash method of Sigma,which results in mismatched signatures.
 */
public class EcKeyLiteExtend extends ECKeyLite {
    public EcKeyLiteExtend(@Nullable BigInteger priv, ECPoint pub) {
        super(priv, pub);
    }


    public static EcKeyLiteExtend fromPrivate(BigInteger privKey) {
        ECPoint point = publicPointFromPrivate(privKey);
        return new EcKeyLiteExtend(privKey, point);
    }


    public String signMessage(byte[] messageBytes) {
        return signMessage(messageBytes, null);
    }

    public String signMessage(byte[] messageBytes, @Nullable KeyParameter aesKey) throws KeyCrypterException {
        byte[] data = UtilsExtend.formatMessageForSigning(messageBytes);
        Sha256Hash hash = Sha256Hash.twiceOf(data);
        ECDSASignature sig = sign(hash, aesKey);
        // Now we have to work backwards to figure out the recId needed to recover the signature.
        int recId = -1;
        for (int i = 0; i < 4; i++) {
            ECKeyLite k = ECKeyLite.recoverFromSignature(i, sig, hash, isCompressed());
            if (k != null && Arrays.equals(k.getPubKey(), getPubKey())) {
                recId = i;
                break;
            }
        }
        if (recId == -1)
            throw new RuntimeException("Could not construct a recoverable key. This should never happen.");
        int headerByte = recId + 27 + (isCompressed() ? 4 : 0);
        byte[] sigData = new byte[65];  // 1 header + 32 bytes for R + 32 bytes for S
        sigData[0] = (byte) headerByte;
        System.arraycopy(Utils.bigIntegerToBytes(sig.r, 32), 0, sigData, 1, 32);
        System.arraycopy(Utils.bigIntegerToBytes(sig.s, 32), 0, sigData, 33, 32);
        return new String(Base64.encode(sigData), Charset.forName("UTF-8"));
    }


}
