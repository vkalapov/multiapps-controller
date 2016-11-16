package com.sap.cloud.lm.sl.cf.process.metadata;

import java.util.HashSet;
import java.util.Set;

import com.sap.cloud.lm.sl.cf.process.Constants;
import com.sap.cloud.lm.sl.cf.process.steps.AddDomainsStep;
import com.sap.cloud.lm.sl.cf.process.steps.BuildCloudDeployModelStep;
import com.sap.cloud.lm.sl.cf.process.steps.BuildCloudUndeployModelStep;
import com.sap.cloud.lm.sl.cf.process.steps.CheckForCreationConflictsStep;
import com.sap.cloud.lm.sl.cf.process.steps.CollectSystemParametersStep;
import com.sap.cloud.lm.sl.cf.process.steps.CreateServiceBrokersStep;
import com.sap.cloud.lm.sl.cf.process.steps.CreateServicesStep;
import com.sap.cloud.lm.sl.cf.process.steps.CreateSubscriptionsStep;
import com.sap.cloud.lm.sl.cf.process.steps.DeletePublishedDependenciesStep;
import com.sap.cloud.lm.sl.cf.process.steps.DeleteServiceBrokersStep;
import com.sap.cloud.lm.sl.cf.process.steps.DeleteServicesStep;
import com.sap.cloud.lm.sl.cf.process.steps.DeleteSubscriptionsStep;
import com.sap.cloud.lm.sl.cf.process.steps.DeleteUnusedReservedRoutesStep;
import com.sap.cloud.lm.sl.cf.process.steps.DeployAppsStep;
import com.sap.cloud.lm.sl.cf.process.steps.DetectDeployedMtaStep;
import com.sap.cloud.lm.sl.cf.process.steps.DetectMtaSchemaVersionStep;
import com.sap.cloud.lm.sl.cf.process.steps.DetectPlatformStep;
import com.sap.cloud.lm.sl.cf.process.steps.MergeDescriptorsStep;
import com.sap.cloud.lm.sl.cf.process.steps.PrepareDeployParametersStep;
import com.sap.cloud.lm.sl.cf.process.steps.ProcessDescriptorStep;
import com.sap.cloud.lm.sl.cf.process.steps.ProcessGitSourceStep;
import com.sap.cloud.lm.sl.cf.process.steps.ProcessMtaArchiveStep;
import com.sap.cloud.lm.sl.cf.process.steps.ProcessMtaExtensionDescriptorsStep;
import com.sap.cloud.lm.sl.cf.process.steps.PublishProvidedDependenciesStep;
import com.sap.cloud.lm.sl.cf.process.steps.RegisterServiceUrlsStep;
import com.sap.cloud.lm.sl.cf.process.steps.SetCtsArchiveExtensionsStep;
import com.sap.cloud.lm.sl.cf.process.steps.SetCtsProcessExtensionsStep;
import com.sap.cloud.lm.sl.cf.process.steps.UndeployAppsStep;
import com.sap.cloud.lm.sl.cf.process.steps.UnregisterServiceUrlsStep;
import com.sap.cloud.lm.sl.cf.process.steps.UpdateSubscribersStep;
import com.sap.cloud.lm.sl.cf.process.steps.ValidateDeployParametersStep;
import com.sap.cloud.lm.sl.mta.model.VersionRule;
import com.sap.cloud.lm.sl.slp.model.ParameterMetadata;
import com.sap.cloud.lm.sl.slp.model.ParameterMetadata.ParameterType;
import com.sap.cloud.lm.sl.slp.model.RoadmapStepMetadata;
import com.sap.cloud.lm.sl.slp.model.ServiceMetadata;
import com.sap.cloud.lm.sl.slp.model.StepMetadata;

public class CtsDeployServiceMetadata extends ServiceMetadata {

    private static final StepMetadata STEP_START = new StepMetadata("startEvent", "Start", "Start");
    private static final StepMetadata WAIT_FOR_PARAMETERS_STEP = new StepMetadata("waitForParametersTask", "Wait For Parameters",
        "Wait For Parameters");
    private static final StepMetadata STEP_END = new StepMetadata("endEvent", "End", "End");

    private static final RoadmapStepMetadata ROADMAP_PREPARE = new RoadmapStepMetadata("roadmap_prepare", "Prepare", "Prepare",
        new StepMetadata[] { STEP_START, WAIT_FOR_PARAMETERS_STEP, PrepareDeployParametersStep.getMetadata(),
            ProcessGitSourceStep.getMetadata(), ValidateDeployParametersStep.getMetadata(), ProcessMtaArchiveStep.getMetadata(),
            ProcessMtaExtensionDescriptorsStep.getMetadata(), DetectMtaSchemaVersionStep.getMetadata(), DetectPlatformStep.getMetadata(),
            MergeDescriptorsStep.getMetadata(), DetectDeployedMtaStep.getMetadata(), CollectSystemParametersStep.getMetadata(),
            ProcessDescriptorStep.getMetadata(), BuildCloudDeployModelStep.getMetadata(), BuildCloudUndeployModelStep.getMetadata(),
            CheckForCreationConflictsStep.getMetadata(), });
    private static final RoadmapStepMetadata ROADMAP_EXECUTE = new RoadmapStepMetadata("roadmap_execute", "Execute", "Execute",
        new StepMetadata[] { AddDomainsStep.getMetadata(), DeleteUnusedReservedRoutesStep.getMetadata(), CreateServicesStep.getMetadata(),
            DeployAppsStep.getMetadata(), CreateSubscriptionsStep.getMetadata(), PublishProvidedDependenciesStep.getMetadata(),
            RegisterServiceUrlsStep.getMetadata(), DeleteSubscriptionsStep.getMetadata(), DeletePublishedDependenciesStep.getMetadata(),
            UnregisterServiceUrlsStep.getMetadata(), UndeployAppsStep.getMetadata(), DeleteServicesStep.getMetadata(),
            CreateServiceBrokersStep.getMetadata(), DeleteServiceBrokersStep.getMetadata(), UpdateSubscribersStep.getMetadata(),
            SetCtsArchiveExtensionsStep.getMetadata(), SetCtsProcessExtensionsStep.getMetadata(), STEP_END });

    private final static Set<ParameterMetadata> PARAMS = new HashSet<ParameterMetadata>();

    static {
        PARAMS.add(new ParameterMetadata(Constants.PARAM_APP_ARCHIVE_ID, false));
        PARAMS.add(new ParameterMetadata(Constants.PARAM_PLATFORM_NAME, false));
        PARAMS.add(new ParameterMetadata(Constants.PARAM_EXT_DESCRIPTOR_FILE_ID, false));
        PARAMS.add(new ParameterMetadata(Constants.PARAM_NO_START, false, ParameterType.BOOLEAN, false));
        PARAMS.add(new ParameterMetadata(Constants.PARAM_START_TIMEOUT, false, ParameterType.INTEGER));
        PARAMS.add(new ParameterMetadata(Constants.PARAM_UPLOAD_TIMEOUT, false, ParameterType.INTEGER));
        PARAMS.add(new ParameterMetadata(Constants.PARAM_USE_NAMESPACES, false, ParameterType.BOOLEAN, false));
        PARAMS.add(new ParameterMetadata(Constants.PARAM_USE_NAMESPACES_FOR_SERVICES, false, ParameterType.BOOLEAN, false));
        PARAMS.add(new ParameterMetadata(Constants.PARAM_ALLOW_INVALID_ENV_NAMES, false, ParameterType.BOOLEAN, false));
        PARAMS.add(new ParameterMetadata(Constants.PARAM_KEEP_APP_ATTRIBUTES, false, ParameterType.BOOLEAN, false));
        PARAMS.add(new ParameterMetadata(Constants.PARAM_STREAM_APP_LOGS, false, ParameterType.BOOLEAN, false));
        PARAMS.add(new ParameterMetadata(Constants.PARAM_VERSION_RULE, false, ParameterType.STRING, VersionRule.SAME_HIGHER.toString()));
        PARAMS.add(new ParameterMetadata(Constants.PARAM_DELETE_SERVICES, false, ParameterType.BOOLEAN, false));
        PARAMS.add(new ParameterMetadata(Constants.PARAM_DELETE_SERVICE_KEYS, false, ParameterType.BOOLEAN, false));
        PARAMS.add(new ParameterMetadata(Constants.PARAM_DELETE_SERVICE_BROKERS, false, ParameterType.BOOLEAN, false));
        PARAMS.add(new ParameterMetadata(Constants.PARAM_FAIL_ON_CRASHED, false, ParameterType.BOOLEAN, false));
        PARAMS.add(new ParameterMetadata(Constants.PARAM_MTA_ID, false));
        PARAMS.add(new ParameterMetadata(Constants.PARAM_KEEP_FILES, false, ParameterType.BOOLEAN, false));
        PARAMS.add(new ParameterMetadata(Constants.PARAM_NO_RESTART_SUBSCRIBED_APPS, false, ParameterType.BOOLEAN, false));
        PARAMS.add(new ParameterMetadata(Constants.PARAM_GIT_URI, false, ParameterType.STRING, ""));
        PARAMS.add(new ParameterMetadata(Constants.PARAM_GIT_REF, false));
        PARAMS.add(new ParameterMetadata(Constants.PARAM_GIT_REPO_PATH, false));
        PARAMS.add(new ParameterMetadata(Constants.PARAM_GIT_SKIP_SSL, false, ParameterType.BOOLEAN, false));
        // Special CTS+ parameters:
        PARAMS.add(new ParameterMetadata(Constants.PARAM_CTS_PROCESS_ID, true));
        PARAMS.add(new ParameterMetadata(Constants.PARAM_FILE_LIST, false, ParameterType.TABLE));
        PARAMS.add(new ParameterMetadata(Constants.PARAM_DEPLOY_URI, false));
        PARAMS.add(new ParameterMetadata(Constants.PARAM_USERNAME, false));
        PARAMS.add(new ParameterMetadata(Constants.PARAM_PASSWORD, false));
        PARAMS.add(new ParameterMetadata(Constants.PARAM_APPLICATION_TYPE, false));
        PARAMS.add(new ParameterMetadata(Constants.PARAM_TRANSFER_TYPE, false));
    }

    public CtsDeployServiceMetadata() {
        super(Constants.CTS_DEPLOY_SERVICE_ID, Constants.DEPLOY_SERVICE_ID, "CTS+ Deploy", "CTS+ Deploy",
            new RoadmapStepMetadata[] { ROADMAP_PREPARE, ROADMAP_EXECUTE }, Constants.SERVICE_VERSION_1_0, PARAMS, null);
    }

}
