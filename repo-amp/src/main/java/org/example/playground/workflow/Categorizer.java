package org.example.playground.workflow;

import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.delegate.TaskListener;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.workflow.activiti.ActivitiScriptNode;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.*;
import org.alfresco.service.namespace.QName;
import org.apache.log4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

@Component
public class Categorizer implements TaskListener{
  private static Logger log = Logger.getLogger(Categorizer.class);

  @Override
  public void notify(DelegateTask delegateTask) {
    log.error("Started notify. Owner is " + delegateTask.getOwner() + " and assignee is " + delegateTask.getAssignee());

    log.error("Here are all the execution variables: \n" + delegateTask.getVariables());
    log.error("And here are the local ones: " + delegateTask.getVariablesLocal());

    log.info("TEST INFO!");
    log.warn("TEST WARN!");
    log.debug("TEST DEBUG!");

    // Set up services
    CategoryService categoryService;
    NodeService nodeService;
    ApplicationContext appContext = new ClassPathXmlApplicationContext("alfresco/application-context.xml");

    ServiceRegistry registry = (ServiceRegistry)appContext.getBean(ServiceRegistry.SERVICE_REGISTRY);
    if (registry.isServiceProvided(ServiceRegistry.CATEGORY_SERVICE))
    {
      categoryService = registry.getCategoryService();
    } else {
      log.error("Failed to get the category service :-(");
      return;
    }

    if (registry.isServiceProvided(ServiceRegistry.NODE_SERVICE))
    {
      nodeService = registry.getNodeService();
    } else {
      log.error("Failed to get the node service :-(");
      return;
    }

    // Query for the one whole category we want, need it as a NodeRef
    SearchParameters sp = new SearchParameters();
    sp.addStore(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
    sp.setLanguage(SearchService.LANGUAGE_SOLR_ALFRESCO);
    sp.setQuery("PATH:\"/cm:generalclassifiable//cm:Approved/member\"");

    ResultSet results = null;
    ArrayList<NodeRef> categories = new ArrayList<>();
    try
    {
      results = registry.getSearchService().query(sp);
      for(ResultSetRow row : results)
      {
        NodeRef currentNodeRef = row.getNodeRef();
        log.error("Got a node ref from search: " + currentNodeRef);
        categories.add(currentNodeRef);
      }
    }
    finally
    {
      if(results != null)
      {
        results.close();
      }
    }

    // Grab the NodeRef from the flowchart
    ActivitiScriptNode node = delegateTask.getVariable("bpm_package", ActivitiScriptNode.class);
    NodeRef nodeRef = node.getNodeRef();
    log.error("NodeRef from the flowchart: " + nodeRef);

    // Apply the category NodeRef to the flowchart's NodeRef
    if(!nodeService.hasAspect(nodeRef, ContentModel.ASPECT_GEN_CLASSIFIABLE)) {
      log.error("The node wasn't previously classifiable so adding the aspect then the category");
      HashMap<QName, Serializable> props = new HashMap<>();
      props.put(ContentModel.PROP_CATEGORIES, categories);
      nodeService.addAspect(nodeRef, ContentModel.ASPECT_GEN_CLASSIFIABLE, props);
    } else {
      log.error("Node already was classifiable, so just adding the category");
      nodeService.setProperty(nodeRef, ContentModel.PROP_CATEGORIES, categories);
    }
  }
}
