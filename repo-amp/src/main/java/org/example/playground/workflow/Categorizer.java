package org.example.playground.workflow;

import org.activiti.engine.delegate.DelegateTask;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.workflow.WorkflowNotificationUtils;
import org.alfresco.repo.workflow.activiti.ActivitiScriptNode;
import org.alfresco.repo.workflow.activiti.tasklistener.ScriptTaskListener;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.*;
import org.alfresco.service.namespace.QName;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

// The Component here likely doesn't matter unless component scanning is put back, paired with static autowired services?
// Started the class simply implementing TaskListener, but later experiments used ScriptTaskListener for getServiceRegistry
@Component
public class Categorizer extends ScriptTaskListener {
  private static Logger log = Logger.getLogger(Categorizer.class);

  // This is goofy. Alfresco maintains *two* bean versions only differing by capitalization :-( lower case is internal
  // Based on debugging it looks like during initialization both resolve to the same object. Maybe Activiti only gets one?
  private static NodeService nodeServiceInternal;
  private NodeService nodeServiceProxied;
  private static SearchService searchServiceInternal;
  private SearchService searchServiceProxied;

  // Of particular weirdness if this is added to the bean the order of initialization seems to get screwed up
  // resulting first in bad DB generation then auth provider not found error on interacting with  just the DelegateTask!
  private static CategoryService categoryService;

  // This seems able to identify the bean to load via Spring, but would still end up null - unless static?
  @Autowired
  private NodeService nodeService;

  @Override
  public void notify(DelegateTask delegateTask) {
    log.error("Started notify. Owner is " + delegateTask.getOwner() + " and assignee is " + delegateTask.getAssignee());

    log.error("Here are all the execution variables: \n" + delegateTask.getVariables());
    log.error("And here are the local ones: " + delegateTask.getVariablesLocal());

    log.info("TEST INFO!");
    log.warn("TEST WARN!");
    log.debug("TEST DEBUG!");

    // Various output to check values. Only the static bean initialized approach seems to work
    log.error("nsi (static): " + nodeServiceInternal + ", nsp: " + nodeServiceProxied);
    log.error("ssi (static): " + searchServiceInternal + ", ssp: " + searchServiceProxied);
    log.error("nodeService via @Autowired: " + nodeService);
    log.error("categoryService: " + categoryService);

    // This below resulted in valid non-null objects but just initializing would cause a later auth provider unavailable
    // Have encountered the same issue before when stepping through code under some circumstances
    // Several web hits exist, like https://issues.alfresco.com/jira/browse/ALF-2454
    //ServiceRegistry serviceRegistry = getServiceRegistry();
    //NodeService nodeServiceViaRegistry = serviceRegistry.getNodeService();
    //SearchService searchServiceViaRegistry = serviceRegistry.getSearchService();

    // This would log that the registry-provided objects are the same as the Spring initialized ones
    //log.error("node service via registry: " + nodeServiceViaRegistry + ", search: " + searchServiceViaRegistry);

    // RETRIEVE CATEGORY PHASE - Query for the one whole category we want, need it as a NodeRef
    SearchParameters sp = new SearchParameters();
    sp.addStore(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
    sp.setLanguage(SearchService.LANGUAGE_LUCENE);
    sp.setQuery("+TYPE:\"cm:category\" +@cm\\:name:\"Approved\"");

    log.error("Search parameter: " + sp);

    ResultSet results = null;
    ArrayList<NodeRef> categories = new ArrayList<>(1);
    try {
      log.error("Going to call the search on searchServiceInternal: " + searchServiceInternal);
      results = searchServiceInternal.query(sp);
      log.error("Query returned " + results.length() + " results");
      for (ResultSetRow row : results) {
        NodeRef currentNodeRef = row.getNodeRef();
        log.error("Got a node ref from search: " + currentNodeRef);
        categories.add(currentNodeRef);
        log.error("Added category noderef to categories - size is " + categories.size());
      }
    } catch (Exception e) {
      log.error("Hit an exception while searching. Going to continue anyway for more troubleshooting info. " + e);
    } finally {
      log.error("Closing the results");
      if (results != null) {
        results.close();
      }
    }

    // CATEGORIZATION PHASE
    try {
      // Go and fetch some interesting things from the passed task - first the package of .. everything? As a Node?
      log.error("Going to grab the ActivitiScriptNode from the delegateTask");
      ActivitiScriptNode node = delegateTask.getVariable(WorkflowNotificationUtils.PROP_PACKAGE, ActivitiScriptNode.class);
      log.error("Node from the task's bpm_package: " + node);

      // From that we can get a node reference to the ... Node? Wat. Maybe this points to the Alfresco side we want?
      NodeRef nodeRef = node.getNodeRef();
      log.error("NodeRef to the node (which is still somehow the workflow?): " + nodeRef);

      // Each attached something is associated via a child reference
      List <ChildAssociationRef> docList = nodeServiceInternal.getChildAssocs(nodeRef);
      log.error("The list of attached document objects is: " + docList);

      for (ChildAssociationRef ref : docList) {
        log.error("Checking list entry: " + ref);
        NodeRef childRef = ref.getChildRef();
        log.error("List entry's NodeRef (an actual attached document, I think!): " + childRef);

        // Apply the category NodeRef to the flowchart's document NodeRef
        if (!nodeServiceInternal.hasAspect(childRef, ContentModel.ASPECT_GEN_CLASSIFIABLE)) {
          log.error("The node wasn't previously classifiable so adding the aspect then the category");
          HashMap<QName, Serializable> props = new HashMap<>();
          props.put(ContentModel.PROP_CATEGORIES, categories);
          nodeServiceInternal.addAspect(childRef, ContentModel.ASPECT_GEN_CLASSIFIABLE, props);
        } else {
          log.error("Node already was classifiable, so just adding the category");
          nodeServiceInternal.setProperty(childRef, ContentModel.PROP_CATEGORIES, categories);
        }
      }
    } catch (Exception e) {
      log.error("Hit an exception while doing categorization stuff, continuing anyway: " + e);
    }
  }

  public void setNodeServiceInternal(NodeService service) {
    log.error("Setting nodeServiceInternal to : " + service);
    nodeServiceInternal = service;
  }

  public void setNodeServiceProxied(NodeService service) {
    log.error("Setting nodeServiceProxied to : " + service);
    nodeServiceProxied = service;
  }

  public void setSearchServiceInternal(SearchService service) {
    log.error("Setting searchServiceInternal to : " + service);
    searchServiceInternal = service;
  }

  public void setSearchServiceProxied(SearchService service) {
    log.error("Setting searchServiceProxied to : " + service);
    searchServiceProxied = service;
  }

  public void setCategoryService(CategoryService service) {
    log.error("Setting categoryService to : " + service);
    categoryService = service;
  }
}
