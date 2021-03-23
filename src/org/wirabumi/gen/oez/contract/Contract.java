package org.wirabumi.gen.oez.contract;

public class Contract {

  public static void require(boolean predicate, String errorMessage) {
    if (!predicate) {
      throw new ContractViolationException(errorMessage);
    }
  }

}
