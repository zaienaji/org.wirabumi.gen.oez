package org.wirabumi.gen.oez.utility;

import org.openbravo.base.structure.BaseOBObject;

public class EntityEquality {

  public static boolean isEqual(BaseOBObject a, BaseOBObject b) {
    return a.getId().toString().equalsIgnoreCase(b.getId().toString());
  }

}
