package org.cloudfoundry.multiapps.controller.process.steps;

import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.cloudfoundry.client.v3.Metadata;
import org.cloudfoundry.multiapps.controller.core.cf.metadata.ImmutableMtaMetadata;
import org.cloudfoundry.multiapps.controller.core.cf.metadata.MtaMetadataAnnotations;
import org.cloudfoundry.multiapps.controller.core.cf.metadata.MtaMetadataLabels;
import org.cloudfoundry.multiapps.controller.core.cf.metadata.util.MtaMetadataUtil;
import org.cloudfoundry.multiapps.controller.core.model.DeployedMta;
import org.cloudfoundry.multiapps.controller.core.model.DeployedMtaApplication.ProductizationState;
import org.cloudfoundry.multiapps.controller.core.model.ImmutableDeployedMta;
import org.cloudfoundry.multiapps.controller.core.model.ImmutableDeployedMtaApplication;
import org.cloudfoundry.multiapps.controller.process.variables.Variables;
import org.cloudfoundry.multiapps.mta.model.Version;
import org.junit.jupiter.api.Test;

import com.sap.cloudfoundry.client.facade.domain.ImmutableCloudMetadata;

class RemoveMtaPreservedMetadataStepTest extends SyncFlowableStepTest<RemoveMtaPreservedMetadataStep> {

    private static final String MTA_ID = "test-mta";
    private static final String MTA_VERSION = "0.0.1";
    private static final String MODULE_NAME = "test-module";
    private static final String PRESERVED_APP_NAME = "mta-preserved-test-app";
    private static final String APP_CHECKSUM = "1";
    private static final UUID APP_GUID = UUID.randomUUID();

    @Test
    void testRemoveSystemNamespace() {
        DeployedMta preservedMta = createPreservedMta("mta-preserved");
        context.setVariable(Variables.PRESERVED_MTA, preservedMta);

        step.execute(execution);

        assertStepFinishedSuccessfully();
        verify(client).updateApplicationMetadata(APP_GUID, Metadata.builder()
                                                                   .label(MtaMetadataLabels.MTA_DESCRIPTOR_CHECKSUM, APP_CHECKSUM)
                                                                   .label(MtaMetadataLabels.MTA_NAMESPACE, null)
                                                                   .annotation(MtaMetadataAnnotations.MTA_NAMESPACE, null)
                                                                   .build());
    }

    @Test
    void testRemoveSystemNamespaceAndKeepUserNamespace() {
        String userNamespace = "user-namespace";
        DeployedMta preservedMta = createPreservedMta("mta-preserved-" + userNamespace);
        context.setVariable(Variables.PRESERVED_MTA, preservedMta);
        context.setVariable(Variables.MTA_NAMESPACE, userNamespace);

        step.execute(execution);

        assertStepFinishedSuccessfully();
        verify(client).updateApplicationMetadata(APP_GUID, Metadata.builder()
                                                                   .label(MtaMetadataLabels.MTA_DESCRIPTOR_CHECKSUM, APP_CHECKSUM)
                                                                   .label(MtaMetadataLabels.MTA_NAMESPACE,
                                                                          MtaMetadataUtil.getHashedLabel(userNamespace))
                                                                   .annotation(MtaMetadataAnnotations.MTA_NAMESPACE, userNamespace)
                                                                   .build());
    }

    private DeployedMta createPreservedMta(String namespace) {
        return ImmutableDeployedMta.builder()
                                   .addApplication(ImmutableDeployedMtaApplication.builder()
                                                                                  .moduleName(MODULE_NAME)
                                                                                  .name(PRESERVED_APP_NAME)
                                                                                  .v3Metadata(Metadata.builder()
                                                                                                      .label(MtaMetadataLabels.MTA_DESCRIPTOR_CHECKSUM,
                                                                                                             APP_CHECKSUM)
                                                                                                      .label(MtaMetadataLabels.MTA_NAMESPACE,
                                                                                                             namespace)
                                                                                                      .annotation(MtaMetadataAnnotations.MTA_NAMESPACE,
                                                                                                                  namespace)
                                                                                                      .build())
                                                                                  .productizationState(ProductizationState.LIVE)
                                                                                  .metadata(ImmutableCloudMetadata.builder()
                                                                                                                  .guid(APP_GUID)
                                                                                                                  .build())
                                                                                  .build())
                                   .metadata(ImmutableMtaMetadata.builder()
                                                                 .id(MTA_ID)
                                                                 .version(Version.parseVersion(MTA_VERSION))
                                                                 .namespace(namespace)
                                                                 .build())
                                   .build();
    }

    @Override
    protected RemoveMtaPreservedMetadataStep createStep() {
        return new RemoveMtaPreservedMetadataStep();
    }

}
