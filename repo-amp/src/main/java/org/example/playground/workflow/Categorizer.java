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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Categorizer extends ScriptTaskListener {
  // TODO: log level for this class is stuck on ERROR for some reason, need to sort that out
  private static Logger log = Logger.getLogger(Categorizer.class);

  // TODO - hack: Need to keep these static or they'll be null by the time the class is called from a workflow
  private static SearchService searchService;
  private static NodeService nodeService;

  @Override
  public void notify(DelegateTask delegateTask) {
    // RETRIEVE CATEGORY PHASE - Query for the one category we want, need it as a NodeRef
    SearchParameters sp = new SearchParameters();
    sp.addStore(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
    sp.setLanguage(SearchService.LANGUAGE_LUCENE);
    sp.setQuery("+TYPE:\"cm:category\" +@cm\\:name:\"Approved\"");

    log.error("Search parameter: " + sp);

    ResultSet results = null;
    ArrayList<NodeRef> categories = new ArrayList<>(1);
    try {
      log.error("Going to call the search on searchService: " + searchService);
      results = searchService.query(sp);
      log.error("Query returned " + results.length() + " results");
      for (ResultSetRow row : results) {
        NodeRef currentNodeRef = row.getNodeRef();
        log.error("Got a node ref from search: " + currentNodeRef);
        categories.add(currentNodeRef);
      }
    } catch (Exception e) {
      log.error("Hit an exception while searching. Going to continue anyway for more troubleshooting info. " + e);
    } finally {
      log.error("Closing the results");
      if (results != null) {
        results.close();
      }
    }

    // CATEGORIZATION PHASE - get the right object and apply a category to it
    try {
      // Fetch the package from the workflow. Represents a Node of some sort (on the Activiti side probably)
      ActivitiScriptNode node = delegateTask.getVariable(WorkflowNotificationUtils.PROP_PACKAGE, ActivitiScriptNode.class);
      log.error("Node from the task's bpm_package: " + node);

      // From that we can get a node reference to the Alfresco side of things (theorized - a workflow node there)
      NodeRef nodeRef = node.getNodeRef();
      log.error("NodeRef to the Alfresco node: " + nodeRef);

      // Each attached something is associated via a child reference
      List <ChildAssociationRef> docList = nodeService.getChildAssocs(nodeRef);
      log.error("The list of attached document objects is: " + docList);

      for (ChildAssociationRef ref : docList) {
        NodeRef childRef = ref.getChildRef();
        log.error("Child reference to an attached document: " + childRef);

        // Apply the category NodeRef to the flowchart's attached document NodeRef
        if (!nodeService.hasAspect(childRef, ContentModel.ASPECT_GEN_CLASSIFIABLE)) {
          log.error("The node wasn't previously classifiable so adding the aspect then the category");
          HashMap<QName, Serializable> props = new HashMap<>();
          props.put(ContentModel.PROP_CATEGORIES, categories);
          nodeService.addAspect(childRef, ContentModel.ASPECT_GEN_CLASSIFIABLE, props);
        } else {
          log.error("Node already was classifiable, so just adding the category");
          nodeService.setProperty(childRef, ContentModel.PROP_CATEGORIES, categories);
        }
      }
    } catch (Exception e) {
      log.error("Hit an exception while doing categorization stuff, continuing anyway: " + e);
    }
  }

  public void setNodeService(NodeService service) {
    log.error("Setting nodeService to : " + service);
    nodeService = service;
  }

  public void setSearchService(SearchService service) {
    log.error("Setting searchService to : " + service);
    searchService = service;
  }
}