package org.example.playground.workflow;

import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.delegate.Expression;
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

/**
 * Listener for flowcharts with events requesting an update of the category on their attachments.
 * Expects categories to be unique and removes any old category already in place.
 */
public class Categorizer extends ScriptTaskListener {
  // TODO: log level for this class is stuck on ERROR for some reason, need to sort that out
  private static Logger log = Logger.getLogger(Categorizer.class);

  // TODO - hack: Need to keep these static or they'll be null by the time the class is called from a workflow
  private static SearchService searchService;
  private static NodeService nodeService;

  /** Incoming field from the flowchart task - what category to join */
  private Expression categoryToJoin;

  /**
   * Called when a workflow wants to update its category. Will replace any existing ones.
   * @param delegateTask the calling workflow task
   */
  @Override
  public void notify(DelegateTask delegateTask) {
    // TODO: Any better way to get this when I know I picked a fixed value of a String type?
    String category = (String) categoryToJoin.getValue(delegateTask.getExecution());
    log.error("Value of passed in field - the category to set: " + category);

    // RETRIEVE CATEGORY PHASE - we need to get the category as a NodeRef
    NodeRef categoryRef = findCategory(category);
    if (categoryRef == null) {
      // Try to fall back on using a category named UNKNOWN, otherwise we'll leave category blank
      log.error("Failed to find a desired category. Attempting to use UNKNOWN instead");
      categoryRef = findCategory("UNKNOWN");
    }

    ArrayList<NodeRef> categories = new ArrayList<>(1);
    if (categoryRef != null) {
      categories.add(categoryRef);
    } else {
      log.error("Failed to find a category or the fallback option. Adding blank classification");
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
          log.error("Node already was classifiable, so just supplying a replacement category");
          nodeService.setProperty(childRef, ContentModel.PROP_CATEGORIES, categories);
        }
      }
    } catch (Exception e) {
      log.error("Hit an exception while doing categorization stuff, continuing anyway: " + e);
    }
  }

  /**
   * Utility method to look up the NodeRef corresponding to a particular category named via String.
   * Expects to find a single result, w
   * @param category String for the category to search for
   * @return resulting NodeRef or null if not found OR more than one result is returned.
   * TODO: Consider caching the categories since they aren't expected to change. Could hack it via static
   */
  private NodeRef findCategory(String category) {
    SearchParameters sp = new SearchParameters();
    sp.addStore(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
    sp.setLanguage(SearchService.LANGUAGE_LUCENE);
    sp.setQuery("+TYPE:\"cm:category\" +@cm\\:name:\"" + category + "\"");

    log.error("Search parameter: " + sp);
    ResultSet results = null;
    try {
      log.error("Going to call the search on searchService: " + searchService);
      results = searchService.query(sp);
      log.error("Query returned " + results.length() + " results");
      if (results.length() == 1) {
        NodeRef currentNodeRef = results.getRow(0).getNodeRef();
        log.error("Got a node ref from search: " + currentNodeRef);
        return currentNodeRef;
      } else {
        log.error("Failed to find the desired category or found more than one. Returning null");
        return null;
      }
    } catch (Exception e) {
      log.error("Hit an exception while searching: " + e);
    } finally {
      log.error("Closing the results");
      if (results != null) {
        results.close();
      }
    }
    return null;
  }

  public void setNodeService(NodeService service) {
    log.error("Setting nodeService to : " + service);
    nodeService = service;
  }

  public void setSearchService(SearchService service) {
    log.error("Setting searchService to : " + service);
    searchService = service;
  }

  /**
   * Injected from the workflow this class is listening for. Method will likely get flagged unused.
   * See http://docs.alfresco.com/activiti/topics/executionListenerFieldInjection.html for doc.
   * @param exp the expression containing the category to set
   */
  public void setCategoryToJoin(Expression exp) {
    log.error("Setting categoryToJoin to " + exp.toString());
    categoryToJoin = exp;
  }
}