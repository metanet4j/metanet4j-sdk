package com.metanet4j.sdk.sigma;


import com.metanet4j.bsv.script.Script;
import com.metanet4j.bsv.transaction.Transaction;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SignResponse extends Sig {

  Script sigmaScript;
  Transaction signedTx;

  public SignResponse(String address, String signature, String algorithm, int vin, int targetVout, Script sigmaScript, Transaction signedTx) {
    super(address, signature, algorithm, vin, targetVout);
    this.sigmaScript = sigmaScript;
    this.signedTx = signedTx;
  }

}