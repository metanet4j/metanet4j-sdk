package com.metanet4j.sdk.sigma;


import io.bitcoinsv.bitcoinjsv.core.Address;
import lombok.Data;

@Data
public class Sig {

  String address;
  String signature;
  String algorithm;
  int vin;
  int targetVout;

  public Sig() {

  }


  public Sig(String address, String signature, String algorithm, int vin, int targetVout) {
    this.address = address;
    this.signature = signature;
    this.algorithm = algorithm;
    this.vin = vin;
    this.targetVout = targetVout;
  }

}

