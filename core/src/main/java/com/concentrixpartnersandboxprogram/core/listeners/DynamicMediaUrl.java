package com.concentrixpartnersandboxprogram.core.listeners;


import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.WorkItem;
import com.day.cq.workflow.exec.WorkflowProcess;
import com.day.cq.workflow.metadata.MetaDataMap;
import com.day.cq.workflow.WorkflowException;
import org.apache.sling.api.resource.*;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Property;
import java.util.HashMap;
import java.util.Map;

@Component(service = WorkflowProcess.class, property = {"process.label=Custom Path Update Workflows"})
public class DynamicMediaUrl implements WorkflowProcess {

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Override
    public void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap metaDataMap) throws WorkflowException {
        String payloadPath = workItem.getWorkflowData().getPayload().toString();

        Map<String, Object> authInfo = new HashMap<>();
        authInfo.put(ResourceResolverFactory.SUBSERVICE, "providence-user2");

        try (ResourceResolver resourceResolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
            Resource resource = resourceResolver.getResource(payloadPath);
            if (resource != null) {
                Node metadataNode = resource.adaptTo(Node.class);
                if (metadataNode != null) {
                    String prop1 = getProperty(metadataNode, "dam:scene7Domain");
                    String prop2 = getProperty(metadataNode, "dam:scene7File");
                    String prop3 = getProperty(metadataNode, "dc:format");
                    String prop4 = getProperty(metadataNode, "dam:scene7Name");

                    if (prop1 != null && prop2 != null && prop3 != null) {
                        String newUrl = createNewUrl(prop1, prop2, prop3, prop4, metadataNode);
                        if (newUrl != null) {
                            metadataNode.setProperty("dmUrl", newUrl);
                            resourceResolver.commit();
                        }
                    }
                }
            }
        } catch (RepositoryException e) {
            throw new WorkflowException("RepositoryException occurred while accessing JCR properties", e);
        } catch (LoginException e) {
            throw new RuntimeException(e);
        } catch (PersistenceException e) {
            throw new RuntimeException(e);
        }
    }

    private String getProperty(Node node, String propertyName) throws RepositoryException {
        if (node.hasProperty(propertyName)) {
            return node.getProperty(propertyName).getString();
        }
        return null;
    }

    private String createNewUrl(String domain, String file, String format, String scene7Name, Node metadataNode) throws RepositoryException {
        if (format.startsWith("image/")) {
            return domain + "is/image/" + file;
        } else {
            String scene7Folder = getProperty(metadataNode, "dam:scene7Folder");
            if (scene7Folder != null && scene7Name != null) {
                if (scene7Name.endsWith("pdf")) {
                    scene7Name = scene7Name.replace("pdf", ".pdf");
                }
                return domain + "is/content/" + scene7Folder + scene7Name;
            }
        }
        return null;
    }
}
