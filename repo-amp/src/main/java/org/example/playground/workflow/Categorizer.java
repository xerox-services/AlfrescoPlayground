package org.example.playground.workflow;

import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.delegate.TaskListener;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.search.*;
import org.apache.log4j.Logger;


public class Categorizer implements TaskListener {
  private static Logger log = Logger.getLogger(Categorizer.class);

  private static SearchService searchService;
  private NodeService nodeService;

  @Override
  public void notify(DelegateTask delegateTask) {
    log.error("Got called by a task from Activiti. At this point our variables are:");
    log.error("searchService (static): " + searchService);
    log.error("nodeService (not): " + nodeService);
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