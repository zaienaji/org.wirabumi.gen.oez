/*
 *************************************************************************
 * The contents of this file are subject to the Openbravo  Public  License
 * Version  1.1  (the  "License"),  being   the  Mozilla   Public  License
 * Version 1.1  with a permitted attribution clause; you may not  use this
 * file except in compliance with the License. You  may  obtain  a copy of
 * the License at http://www.openbravo.com/legal/license.html 
 * Software distributed under the License  is  distributed  on  an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific  language  governing  rights  and  limitations
 * under the License. 
 * The Original Code is Openbravo ERP. 
 * The Initial Developer of the Original Code is Openbravo SLU 
 * All portions are Copyright (C) 2011-2012 Openbravo SLU 
 * All Rights Reserved. 
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */

package org.openbravo.client.application.event;


import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.enterprise.event.Observes;

import org.hibernate.criterion.Restrictions;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.base.provider.OBConfigFileProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.database.ConnectionProviderImpl;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.exception.PoolNotFoundException;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.common.enterprise.DocumentType;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.order.Order;
import org.openbravo.model.financialmgmt.calendar.Period;
import org.openbravo.model.financialmgmt.calendar.Year;
import org.openbravo.service.db.DalConnectionProvider;

/**
 * Listens to save events on purchase and sales orders and sets the document no.
 * 
 * @see Utility#getDocumentNo(java.sql.Connection, org.openbravo.database.ConnectionProvider,
 *      org.openbravo.base.secureApp.VariablesSecureApp, String, String, String, String, boolean,
 *      boolean)
 * 
 * @author mtaal
 */
public class SetDocumentNoHandler extends EntityPersistenceEventObserver {
  private static Entity[] entities = null;
  private static Property[] documentNoProperties = null;
  private static Property[] documentTypeProperties = null;
  private static Property[] documentTypeTargetProperties = null;
  private static Property[] processedProperties = null;
  

  public void onUpdate(@Observes EntityUpdateEvent event) {
    handleEvent(event);
  }

  public void onSave(@Observes EntityNewEvent event) {
    handleEvent(event);
  }

  private void handleEvent(EntityPersistenceEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    int index = 0;
    for (int i = 0; i < entities.length; i++) {
      if (entities[i] == event.getTargetInstance().getEntity()) {
        index = i;
        break;
      }
    }
    Entity entity = entities[index];
    Property documentNoProperty = documentNoProperties[index];
    Property documentTypeProperty = documentTypeProperties[index];
    Property docTypeTargetProperty = documentTypeTargetProperties[index];
    Property processedProperty = processedProperties[index];
    
    String documentNo = (String) event.getCurrentState(documentNoProperty);
    BaseOBObject objek = event.getTargetInstance();
    String nameString = entity.getName();
    Organization org = null;
    org = (Organization) objek.get("organization");
    String idOrg = org.getId();
    String Org = "";
    OBConfigFileProvider obConfigFileProvider = OBConfigFileProvider.getInstance();
    String webINFLocation=obConfigFileProvider.getServletContext().getRealPath("")+obConfigFileProvider.getClassPathLocation();
    String obConfigFileLocation = webINFLocation+"/Openbravo.properties";
    ConnectionProvider conn = null;
    ResultSet rs= null;
    try {
		conn = new ConnectionProviderImpl(obConfigFileLocation);
	} catch (PoolNotFoundException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
    try {
		PreparedStatement ps = conn.getPreparedStatement("select ad_org_getcalendarowner from ad_org_getcalendarowner(?)");
		ps.setString(1, idOrg);
		rs = ps.executeQuery();
		while(rs.next()){
			Org = rs.getString(1);
		}
	} catch (Exception e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
    Organization paramOrg = OBDal.getInstance().get(Organization.class, Org);
    String propDate = null;
    OBCriteria<Table> tableQry = OBDal.getInstance().createCriteria(Table.class);
    tableQry.add(Restrictions.eq(Table.PROPERTY_NAME, nameString));
    for(Table tblHsl : tableQry.list()){
    	OBContext.setAdminMode();
    	propDate = tblHsl.getOezDocumentdate();
    	OBContext.restorePreviousMode();
    }
    
    boolean processed = false;
    Object oProcessed = (processedProperty == null ? false : event
        .getCurrentState(processedProperty));
    if (oProcessed instanceof String) {
      processed = "Y".equals(oProcessed.toString());
    } else if (oProcessed instanceof Boolean) {
      processed = (Boolean) oProcessed;
    }
    
    if (documentNo == null || documentNo.startsWith("<") && !processed) {
    	if(propDate!=null){
    		Date date = null;
            date = (Date) objek.get(propDate);
           
            Period period = null;
            Year year = null;
            OBCriteria<Period> periodeList = OBDal.getInstance().createCriteria(Period.class);
            periodeList.add(Restrictions.eq(Period.PROPERTY_ORGANIZATION, paramOrg));
            periodeList.add(Restrictions.le(Period.PROPERTY_STARTINGDATE, date));
            periodeList.add(Restrictions.ge(Period.PROPERTY_ENDINGDATE, date));
            for(Period periode :periodeList.list()){
            	period =periode;
            	year = period.getYear();
            }
            
            final DocumentType docTypeTarget = (docTypeTargetProperty == null ? null
            		: (DocumentType) event.getCurrentState(docTypeTargetProperty));
            final DocumentType docType = (documentTypeProperty == null ? null : (DocumentType) event
            		.getCurrentState(documentTypeProperty));
            // use empty strings instead of null
            final String docTypeTargetId = docTypeTarget != null ? docTypeTarget.getId() : "";
            final String docTypeId = docType != null ? docType.getId() : "";
            String windowId = RequestContext.get().getRequestParameter("windowId");
            if (windowId == null) {
            	windowId = "";
            }

            // recompute it
            documentNo = Utility.getDocNoSeqLine(OBDal.getInstance().getConnection(false),
            		new DalConnectionProvider(false), RequestContext.get().getVariablesSecureApp(), windowId,
            		entity.getTableName(), docTypeTargetId, docTypeId, false, true,period,year);
            event.setCurrentState(documentNoProperty, documentNo);
    	} else {
    		final DocumentType docTypeTarget = (docTypeTargetProperty == null ? null
    				: (DocumentType) event.getCurrentState(docTypeTargetProperty));
    		final DocumentType docType = (documentTypeProperty == null ? null : (DocumentType) event
    				.getCurrentState(documentTypeProperty));
    		// use empty strings instead of null
    		final String docTypeTargetId = docTypeTarget != null ? docTypeTarget.getId() : "";
    		final String docTypeId = docType != null ? docType.getId() : "";
    		String windowId = RequestContext.get().getRequestParameter("windowId");
    		if (windowId == null) {
    			windowId = "";
    		}

    		// recompute it
    		documentNo = Utility.getDocumentNo(OBDal.getInstance().getConnection(false),
    				new DalConnectionProvider(false), RequestContext.get().getVariablesSecureApp(), windowId,
    				entity.getTableName(), docTypeTargetId, docTypeId, false, true);
    		event.setCurrentState(documentNoProperty, documentNo);
    	}
    	
    }
    
  }

  @Override
  protected synchronized Entity[] getObservedEntities() {
    if (entities == null) {
      List<Entity> entityList = new ArrayList<Entity>();
      List<Property> documentNoPropertyList = new ArrayList<Property>();
      List<Property> documentTypePropertyList = new ArrayList<Property>();
      List<Property> documentTypeTargetPropertyList = new ArrayList<Property>();
      List<Property> processedPropertyList = new ArrayList<Property>();
      
      for (Entity entity : ModelProvider.getInstance().getModel()) {
        for (Property prop : entity.getProperties()) {
          if (prop.isUsedSequence()) {
            entityList.add(entity);
            documentNoPropertyList.add(prop);
            if (entity.hasProperty(Order.PROPERTY_DOCUMENTTYPE)) {
              documentTypePropertyList.add(entity.getProperty(Order.PROPERTY_DOCUMENTTYPE));
            } else {
              documentTypePropertyList.add(null);
            }
            if (entity.hasProperty(Order.PROPERTY_TRANSACTIONDOCUMENT)) {
              documentTypeTargetPropertyList.add(entity
                  .getProperty(Order.PROPERTY_TRANSACTIONDOCUMENT));
            } else {
              documentTypeTargetPropertyList.add(null);
            }
            if (entity.hasProperty(Order.PROPERTY_PROCESSED)) {
              processedPropertyList.add(entity.getProperty(Order.PROPERTY_PROCESSED));
            } else {
              processedPropertyList.add(null);
            }
            break;
          }
        }
      }
      entities = entityList.toArray(new Entity[entityList.size()]);
      documentNoProperties = documentNoPropertyList.toArray(new Property[documentNoPropertyList
          .size()]);
      documentTypeProperties = documentTypePropertyList
          .toArray(new Property[documentTypePropertyList.size()]);
      documentTypeTargetProperties = documentTypeTargetPropertyList
          .toArray(new Property[documentTypeTargetPropertyList.size()]);
      processedProperties = processedPropertyList
          .toArray(new Property[processedPropertyList.size()]);
    }
    return entities;
  }
}
