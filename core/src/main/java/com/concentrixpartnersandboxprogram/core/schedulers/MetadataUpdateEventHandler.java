package com.concentrixpartnersandboxprogram.core.schedulers;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkflowData;
import com.adobe.granite.workflow.model.WorkflowModel;
import com.day.cq.dam.api.DamEvent;
import org.apache.sling.api.resource.*;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component(
        immediate = true,
        service = EventHandler.class,
        property = EventConstants.EVENT_TOPIC + "=" + DamEvent.EVENT_TOPIC)
public class MetadataUpdateEventHandler implements EventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(MetadataUpdateEventHandler.class);
    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Override
    public void handleEvent(Event event) {
        DamEvent damEvent = DamEvent.fromEvent(event);
        ResourceResolver resourceResolver = null;
        if (isMetadataUpdated(damEvent)) {
            String assetPath = damEvent.getAssetPath();
            LOG.debug("MetadataUpdateEventHandler has registered an event on  {}", assetPath);
            Map<String, Object> authInfo = new HashMap<>();
            authInfo.put(ResourceResolverFactory.SUBSERVICE, AssetConstants.LLOYDSASSETSERVICE);
            try {
                resourceResolver = resourceResolverFactory.getServiceResourceResolver(authInfo);
                Resource assetResource = resourceResolver.getResource(assetPath + "/jcr:content");
                String assetMetadataPath = assetPath + AssetConstants.METADATA_PATH;
                Resource metadataResource = resourceResolver.getResource(assetMetadataPath);
                ValueMap jcrContentVm = Objects.requireNonNull(assetResource).getValueMap();
                ValueMap metadataVm = Objects.requireNonNull(metadataResource).getValueMap();
                String workfrontDocumentId = metadataVm.containsKey(AssetConstants.WORKFRONT_DOC_ID) ? metadataVm.get(AssetConstants.WORKFRONT_DOC_ID, String.class) : null;
                if (jcrContentVm.get(AssetConstants.DAM_ASSET_STATE).toString().equals("processed") && checkValidAssetState(metadataVm) && workfrontDocumentId != null) {
                    final WorkflowSession workflowSession = resourceResolver.adaptTo(WorkflowSession.class);
                    final WorkflowModel workflowModel = Objects.requireNonNull(workflowSession).getModel(AssetConstants.METADATA_SYNC_MODEL);
                    WorkflowData wfData = workflowSession.newWorkflowData("JCR_PATH", assetMetadataPath);

                    workflowSession.startWorkflow(workflowModel, wfData);
                    LOG.debug("Bidirectional Sync workflow has been started by the MetadataUpdateEventHandler {}", wfData.getPayload());
                } else {
                    LOG.debug("Not a valid asset to sync {} ", assetPath);
                }

            } catch (LoginException | WorkflowException e) {
                LOG.error("Exception occurred in MetadataUpdateEventHandler {} ", e.getMessage());
            }

        }
    }

    private boolean isMetadataUpdated(DamEvent damEvent) {
        LOG.debug("MetadataUpdateEventHandler {}", damEvent.getAdditionalInfo());
        return DamEvent.Type.METADATA_UPDATED.equals(damEvent.getType()) && (!damEvent.getUserId().equals(AssetConstants.WORKFLOW_SERVICE_USER)) && ((damEvent.getAdditionalInfo() != null) && (!damEvent.getAdditionalInfo().contains(AssetConstants.LAST_MODIFIED_BY_JCR)));
    }

    boolean checkValidAssetState(ValueMap valueMap) {
        if (valueMap.containsKey((AssetConstants.LBGSTATUS))) {
            String assetStatus = valueMap.get(AssetConstants.LBGSTATUS, String.class);
            boolean isValid;
            isValid = !Objects.requireNonNull(assetStatus).equals("Expired") && !assetStatus.equals("Withdrawn") && !assetStatus.equals("Under Review");
            return isValid;
        } else {
            LOG.debug("LBG Status not found");
            return false;
        }

    }
}


