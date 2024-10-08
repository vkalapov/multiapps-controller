package org.cloudfoundry.multiapps.controller.process.steps;

import static java.text.MessageFormat.format;

import java.io.InputStream;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Named;

import org.cloudfoundry.multiapps.common.SLException;
import org.cloudfoundry.multiapps.controller.client.lib.domain.CloudApplicationExtended;
import org.cloudfoundry.multiapps.controller.client.lib.domain.UploadStatusCallbackExtended;
import org.cloudfoundry.multiapps.controller.core.Constants;
import org.cloudfoundry.multiapps.controller.core.helpers.ApplicationEnvironmentUpdater;
import org.cloudfoundry.multiapps.controller.core.helpers.ApplicationFileDigestDetector;
import org.cloudfoundry.multiapps.controller.core.helpers.MtaArchiveElements;
import org.cloudfoundry.multiapps.controller.core.security.serialization.SecureSerialization;
import org.cloudfoundry.multiapps.controller.core.util.FileUtils;
import org.cloudfoundry.multiapps.controller.persistence.services.FileContentProcessor;
import org.cloudfoundry.multiapps.controller.persistence.services.FileStorageException;
import org.cloudfoundry.multiapps.controller.process.Messages;
import org.cloudfoundry.multiapps.controller.process.util.ApplicationArchiveContext;
import org.cloudfoundry.multiapps.controller.process.util.ApplicationArchiveReader;
import org.cloudfoundry.multiapps.controller.process.util.ApplicationStager;
import org.cloudfoundry.multiapps.controller.process.util.ApplicationZipBuilder;
import org.cloudfoundry.multiapps.controller.process.util.CloudPackagesGetter;
import org.cloudfoundry.multiapps.controller.process.util.TimeoutType;
import org.cloudfoundry.multiapps.controller.process.variables.Variables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;

import com.sap.cloudfoundry.client.facade.CloudControllerClient;
import com.sap.cloudfoundry.client.facade.domain.CloudApplication;
import com.sap.cloudfoundry.client.facade.domain.CloudPackage;
import com.sap.cloudfoundry.client.facade.domain.Status;

@Named("uploadAppStep")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class UploadAppStep extends TimeoutAsyncFlowableStep {
    private static final Logger LOGGER = LoggerFactory.getLogger(UploadAppStep.class);
    @Inject
    protected ApplicationArchiveReader applicationArchiveReader;
    @Inject
    protected ApplicationZipBuilder applicationZipBuilder;
    @Inject
    protected CloudPackagesGetter cloudPackagesGetter;

    @Override
    public StepPhase executeAsyncStep(ProcessContext context) throws FileStorageException {
        CloudApplicationExtended applicationToProcess = context.getVariable(Variables.APP_TO_PROCESS);
        getStepLogger().info(Messages.UPLOADING_APP, applicationToProcess.getName());

        MtaArchiveElements mtaArchiveElements = context.getVariable(Variables.MTA_ARCHIVE_ELEMENTS);
        String moduleFileName = mtaArchiveElements.getModuleFileName(applicationToProcess.getModuleName());
        CloudControllerClient client = context.getControllerClient();
        if (moduleFileName == null) {
            getStepLogger().debug(Messages.NO_CONTENT_TO_UPLOAD);
            if (applicationToProcess.getDockerInfo() != null) {
                CloudPackage cloudPackage = createDockerPackage(client, applicationToProcess);
                context.setVariable(Variables.CLOUD_PACKAGE, cloudPackage);
            }
            return StepPhase.DONE;
        }

        String newApplicationDigest = getNewApplicationDigest(context, moduleFileName);
        CloudApplication cloudApp = client.getApplication(applicationToProcess.getName());

        var appEnv = client.getApplicationEnvironment(cloudApp.getGuid());

        boolean contentChanged = detectApplicationFileDigestChanges(appEnv, newApplicationDigest);
        if (contentChanged) {
            proceedWithUpload(context, applicationToProcess, moduleFileName);
            attemptToUpdateApplicationDigest(context.getControllerClient(), cloudApp, appEnv, newApplicationDigest);
            return StepPhase.POLL;
        }

        Optional<CloudPackage> mostRecentPackage = cloudPackagesGetter.getMostRecentAppPackage(client, cloudApp.getGuid());
        if (mostRecentPackage.isEmpty()) {
            proceedWithUpload(context, applicationToProcess, moduleFileName);
            return StepPhase.POLL;
        }

        CloudPackage latestPackage = mostRecentPackage.get();
        Optional<CloudPackage> currentPackage = cloudPackagesGetter.getAppPackage(client, cloudApp.getGuid());
        if (currentPackage.isEmpty() && isPackageInValidState(latestPackage)) {
            return useLatestPackage(context, latestPackage);
        }

        if (currentPackage.isEmpty() || !isAppStagedCorrectly(context, cloudApp)) {
            proceedWithUpload(context, applicationToProcess, moduleFileName);
            return StepPhase.POLL;
        }

        if (isPackageInValidState(latestPackage)
            && (context.getVariable(Variables.APP_NEEDS_RESTAGE) || !packagesMatch(currentPackage.get(), latestPackage))) {
            return useLatestPackage(context, latestPackage);
        }

        getStepLogger().info(Messages.CONTENT_OF_APPLICATION_0_IS_NOT_CHANGED, applicationToProcess.getName());
        return StepPhase.DONE;
    }

    private boolean packagesMatch(CloudPackage currentPackage, CloudPackage latestPackage) {
        return Objects.equals(currentPackage.getGuid(), latestPackage.getGuid());
    }

    private CloudPackage createDockerPackage(CloudControllerClient client, CloudApplicationExtended application) {
        UUID applicationGuid = client.getApplicationGuid(application.getName());
        return client.createDockerPackage(applicationGuid, application.getDockerInfo());
    }

    private String getNewApplicationDigest(ProcessContext context, String fileName) throws FileStorageException {
        return fileService.processFileContent(context.getVariable(Variables.SPACE_GUID),
                                              context.getRequiredVariable(Variables.APP_ARCHIVE_ID),
                                              createDigestCalculatorFileContentProcessor(fileName));
    }

    private FileContentProcessor<String> createDigestCalculatorFileContentProcessor(String fileName) {
        return appArchiveStream -> {
            long maxSize = configuration.getMaxResourceFileSize();
            ApplicationArchiveContext applicationArchiveContext = createApplicationArchiveContext(appArchiveStream, fileName, maxSize);
            return applicationArchiveReader.calculateApplicationDigest(applicationArchiveContext);
        };
    }

    protected ApplicationArchiveContext createApplicationArchiveContext(InputStream appArchiveStream, String fileName, long maxSize) {
        return new ApplicationArchiveContext(appArchiveStream, fileName, maxSize);
    }

    private boolean detectApplicationFileDigestChanges(Map<String, String> appEnv, String newApplicationDigest) {
        ApplicationFileDigestDetector digestDetector = new ApplicationFileDigestDetector(appEnv);
        String currentApplicationDigest = digestDetector.detectCurrentAppFileDigest();
        return !newApplicationDigest.equals(currentApplicationDigest);
    }

    private void proceedWithUpload(ProcessContext context, CloudApplicationExtended application, String moduleFileName)
        throws FileStorageException {
        getStepLogger().debug(Messages.UPLOADING_FILE_0_FOR_APP_1, moduleFileName, application.getName());

        CloudPackage cloudPackage = asyncUploadFiles(context, application, context.getRequiredVariable(Variables.APP_ARCHIVE_ID),
                                                     moduleFileName);

        getStepLogger().info(Messages.STARTED_ASYNC_UPLOAD_OF_APP_0, application.getName());
        LOGGER.info(format(Messages.UPLOADED_PACKAGE_0, cloudPackage));

        context.setVariable(Variables.CLOUD_PACKAGE, cloudPackage);
        context.setVariable(Variables.APP_CONTENT_CHANGED, true);
    }

    private StepPhase useLatestPackage(ProcessContext context, CloudPackage latestUnusedPackage) {
        getStepLogger().debug(Messages.THE_NEWEST_PACKAGE_WILL_BE_USED_0, SecureSerialization.toJson(latestUnusedPackage));
        context.setVariable(Variables.CLOUD_PACKAGE, latestUnusedPackage);
        return StepPhase.POLL;
    }

    private boolean isPackageInValidState(CloudPackage cloudPackage) {
        LOGGER.info(format(Messages.PACKAGE_STATUS_0_IS_IN_STATE_1, cloudPackage.getGuid(), cloudPackage.getStatus()));
        return cloudPackage.getStatus() != Status.EXPIRED && cloudPackage.getStatus() != Status.FAILED
            && cloudPackage.getStatus() != Status.AWAITING_UPLOAD;
    }

    private boolean isAppStagedCorrectly(ProcessContext context, CloudApplication cloudApp) {
        ApplicationStager appStager = new ApplicationStager(context);
        return appStager.isApplicationStagedCorrectly(cloudApp);
    }

    @Override
    protected String getStepErrorMessage(ProcessContext context) {
        return format(Messages.ERROR_UPLOADING_APP_0, context.getVariable(Variables.APP_TO_PROCESS)
                                                             .getName());
    }

    private CloudPackage asyncUploadFiles(ProcessContext context, CloudApplication app, String appArchiveId, String fileName)
        throws FileStorageException {
        Path extractedAppPath = extractApplicationFromArchive(context, appArchiveId, fileName);
        LOGGER.debug(MessageFormat.format(Messages.APPLICATION_WITH_NAME_0_SAVED_TO_1, app.getName(), extractedAppPath));
        getStepLogger().infoWithoutProgressMessage(Messages.SIZE_OF_APP_0_IS_1_BYTES, app.getName(), extractedAppPath.toFile()
                                                                                                                     .length());
        return upload(context, app, extractedAppPath);
    }

    private Path extractApplicationFromArchive(ProcessContext context, String appArchiveId, String fileName) throws FileStorageException {
        return fileService.processFileContent(context.getVariable(Variables.SPACE_GUID), appArchiveId, appArchiveStream -> {
            long maxSize = configuration.getMaxResourceFileSize();
            ApplicationArchiveContext applicationArchiveContext = createApplicationArchiveContext(appArchiveStream, fileName, maxSize);
            return extractFromMtar(applicationArchiveContext);
        });
    }

    protected Path extractFromMtar(ApplicationArchiveContext applicationArchiveContext) {
        return applicationZipBuilder.extractApplicationInNewArchive(applicationArchiveContext);
    }

    private CloudPackage upload(ProcessContext context, CloudApplication app, Path filePath) {
        try {
            return context.getControllerClient()
                          .asyncUploadApplication(app.getName(), filePath, getMonitorUploadStatusCallback(context, app, filePath));
        } catch (Exception e) {
            FileUtils.cleanUp(filePath, LOGGER);
            throw new SLException(e, Messages.ERROR_WHILE_STARTING_ASYNC_UPLOAD_OF_APP_WITH_NAME_0, app.getName());
        }
    }

    private void attemptToUpdateApplicationDigest(CloudControllerClient client, CloudApplication app, Map<String, String> appEnv,
                                                  String newApplicationDigest) {
        new ApplicationEnvironmentUpdater(app, appEnv, client).updateApplicationEnvironment(Constants.ENV_DEPLOY_ATTRIBUTES,
                                                                                            Constants.ATTR_APP_CONTENT_DIGEST,
                                                                                            newApplicationDigest);
    }

    MonitorUploadStatusCallback getMonitorUploadStatusCallback(ProcessContext context, CloudApplication app, Path file) {
        return new MonitorUploadStatusCallback(context, app, file);
    }

    @Override
    protected List<AsyncExecution> getAsyncStepExecutions(ProcessContext context) {
        return Collections.singletonList(new PollUploadAppStatusExecution());
    }

    @Override
    public Duration getTimeout(ProcessContext context) {
        return calculateTimeout(context, TimeoutType.UPLOAD);
    }

    class MonitorUploadStatusCallback implements UploadStatusCallbackExtended {

        private final CloudApplication app;
        private final Path file;
        private final ProcessContext context;

        public MonitorUploadStatusCallback(ProcessContext context, CloudApplication app, Path file) {
            this.app = app;
            this.file = file;
            this.context = context;
        }

        @Override
        public void onCheckResources() {
            getStepLogger().debug("Resources checked");
        }

        @Override
        public void onMatchedFileNames(Set<String> matchedFileNames) {
            getStepLogger().debug(Messages.MATCHED_FILES_COUNT_0, matchedFileNames.size());
        }

        @Override
        public void onProcessMatchedResources(int length) {
            getStepLogger().debug(Messages.MATCHED_RESOURCES_PROCESSED_TOTAL_SIZE_0, length);
        }

        @Override
        public boolean onProgress(String status) {
            getStepLogger().debug(Messages.UPLOAD_STATUS_0, status);
            if (status.equals(Status.READY.toString())) {
                FileUtils.cleanUp(file, LOGGER);
                getProcessLogsPersister().persistLogs(context.getVariable(Variables.CORRELATION_ID),
                                                      context.getVariable(Variables.TASK_ID));
            }
            return false;
        }

        @Override
        public void onError(Exception e) {
            getStepLogger().error(e, Messages.ERROR_UPLOADING_APP_0, app.getName());
            FileUtils.cleanUp(file, LOGGER);
        }

        @Override
        public void onError(String description) {
            getStepLogger().error(Messages.ERROR_UPLOADING_APP_0_STATUS_1_DESCRIPTION_2, app.getName(), Status.FAILED, description);
            FileUtils.cleanUp(file, LOGGER);
        }

    }

}