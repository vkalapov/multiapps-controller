package org.cloudfoundry.multiapps.controller.core.security.data.termination;

import static java.text.MessageFormat.format;

import java.text.MessageFormat;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.cloudfoundry.multiapps.common.SLException;
import org.cloudfoundry.multiapps.controller.api.model.Operation;
import org.cloudfoundry.multiapps.controller.core.Messages;
import org.cloudfoundry.multiapps.controller.core.auditlogging.MtaConfigurationPurgerAuditLog;
import org.cloudfoundry.multiapps.controller.core.cf.clients.CFOptimizedEventGetter;
import org.cloudfoundry.multiapps.controller.core.cf.clients.WebClientFactory;
import org.cloudfoundry.multiapps.controller.core.util.ApplicationConfiguration;
import org.cloudfoundry.multiapps.controller.core.util.SafeExecutor;
import org.cloudfoundry.multiapps.controller.core.util.SecurityUtil;
import org.cloudfoundry.multiapps.controller.persistence.dto.BackupDescriptor;
import org.cloudfoundry.multiapps.controller.persistence.model.ConfigurationEntry;
import org.cloudfoundry.multiapps.controller.persistence.model.ConfigurationSubscription;
import org.cloudfoundry.multiapps.controller.persistence.services.ConfigurationEntryService;
import org.cloudfoundry.multiapps.controller.persistence.services.ConfigurationSubscriptionService;
import org.cloudfoundry.multiapps.controller.persistence.services.DescriptorBackupService;
import org.cloudfoundry.multiapps.controller.persistence.services.FileService;
import org.cloudfoundry.multiapps.controller.persistence.services.FileStorageException;
import org.cloudfoundry.multiapps.controller.persistence.services.OperationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sap.cloudfoundry.client.facade.CloudCredentials;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@Named
public class DataTerminationService {

    private static final String SPACE_DELETE_EVENT_TYPE = "audit.space.delete-request";
    private static final int NUMBER_OF_DAYS_OF_EVENTS = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger(DataTerminationService.class);
    private static final SafeExecutor SAFE_EXECUTOR = new SafeExecutor(DataTerminationService::log);
    // Required by CF API:
    // https://v3-apidocs.cloudfoundry.org/version/3.128.0/index.html#timestamps
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    @Inject
    private ConfigurationEntryService configurationEntryService;
    @Inject
    private ConfigurationSubscriptionService configurationSubscriptionService;
    @Inject
    private OperationService operationService;
    @Inject
    private FileService fileService;
    @Inject
    private ApplicationConfiguration configuration;
    @Inject
    private WebClientFactory webClientFactory;
    @Inject
    private MtaConfigurationPurgerAuditLog mtaConfigurationPurgerAuditLog;
    @Inject
    private DescriptorBackupService descriptorBackupService;

    private static void log(Exception e) {
        LOGGER.error(format(Messages.ERROR_DURING_DATA_TERMINATION_0, e.getMessage()), e);
    }

    public void deleteOrphanUserData() {
        assertGlobalAuditorCredentialsExist();
        List<String> spaceEventsToBeDeleted = getSpaceDeleteEvents();
        for (String spaceId : spaceEventsToBeDeleted) {
            SAFE_EXECUTOR.execute(() -> deleteConfigurationSubscriptionOrphanData(spaceId));
            SAFE_EXECUTOR.execute(() -> deleteConfigurationEntryOrphanData(spaceId));
            SAFE_EXECUTOR.execute(() -> deleteUserOperationsOrphanData(spaceId));
            SAFE_EXECUTOR.execute(() -> deletedMtaDescriptorsOrphanData(spaceId));
        }
        if (!spaceEventsToBeDeleted.isEmpty()) {
            SAFE_EXECUTOR.execute(() -> deleteSpaceIdsLeftovers(spaceEventsToBeDeleted));
        }
    }

    private void assertGlobalAuditorCredentialsExist() {
        if (configuration.getGlobalAuditorUser() == null || configuration.getGlobalAuditorPassword() == null) {
            throw new IllegalStateException(Messages.MISSING_GLOBAL_AUDITOR_CREDENTIALS);
        }
    }

    private List<String> getSpaceDeleteEvents() {
        CFOptimizedEventGetter cfOptimizedEventGetter = getCfOptimizedEventGetter();
        List<String> spaceDeleteEvents = cfOptimizedEventGetter.findEvents(SPACE_DELETE_EVENT_TYPE,
                                                                           getDateBeforeDays(NUMBER_OF_DAYS_OF_EVENTS));
        LOGGER.info(MessageFormat.format(Messages.RECENT_DELETE_SPACE_REQUEST_EVENTS, spaceDeleteEvents.size()));
        return spaceDeleteEvents;
    }

    protected CFOptimizedEventGetter getCfOptimizedEventGetter() {
        CloudCredentials cloudCredentials = new CloudCredentials(configuration.getGlobalAuditorUser(),
                                                                 configuration.getGlobalAuditorPassword(),
                                                                 SecurityUtil.CLIENT_ID,
                                                                 SecurityUtil.CLIENT_SECRET,
                                                                 configuration.getGlobalAuditorOrigin());
        return new CFOptimizedEventGetter(configuration, webClientFactory, cloudCredentials);
    }

    private String getDateBeforeDays(int numberOfDays) {
        ZonedDateTime dateBeforeTwoDays = ZonedDateTime.now()
                                                       .minus(Duration.ofDays(numberOfDays));
        String result = DATE_TIME_FORMATTER.format(dateBeforeTwoDays);
        LOGGER.info(MessageFormat.format(Messages.PURGE_DELETE_REQUEST_SPACE_FROM_CONFIGURATION_TABLES, result));
        return result;
    }

    private void deleteConfigurationSubscriptionOrphanData(String spaceId) {
        List<ConfigurationSubscription> configurationSubscriptions = configurationSubscriptionService.createQuery()
                                                                                                     .spaceId(spaceId)
                                                                                                     .list();
        if (configurationSubscriptions.isEmpty()) {
            return;
        }
        configurationSubscriptions.forEach(configurationSubscription -> mtaConfigurationPurgerAuditLog.logDeleteSubscription(spaceId,
                                                                                                                             configurationSubscription));
        configurationSubscriptionService.createQuery()
                                        .deleteAll(spaceId);
    }

    private void deleteConfigurationEntryOrphanData(String spaceId) {
        List<ConfigurationEntry> configurationEntities = configurationEntryService.createQuery()
                                                                                  .spaceId(spaceId)
                                                                                  .list();
        if (configurationEntities.isEmpty()) {
            return;
        }
        configurationEntities.forEach(configurationEntity -> mtaConfigurationPurgerAuditLog.logDeleteEntry(spaceId, configurationEntity));
        configurationEntryService.createQuery()
                                 .deleteAll(spaceId);
    }

    private void deleteUserOperationsOrphanData(String deleteEventSpaceId) {
        List<Operation> operationsToBeDeleted = operationService.createQuery()
                                                                .spaceId(deleteEventSpaceId)
                                                                .list();
        operationsToBeDeleted.forEach(operation -> mtaConfigurationPurgerAuditLog.logDeleteOperation(deleteEventSpaceId, operation));
        operationService.createQuery()
                        .spaceId(deleteEventSpaceId)
                        .delete();
    }

    private void deletedMtaDescriptorsOrphanData(String spaceId) {
        List<BackupDescriptor> backupDescriptors = descriptorBackupService.createQuery()
                                                                          .spaceId(spaceId)
                                                                          .list();
        backupDescriptors.forEach(descriptor -> mtaConfigurationPurgerAuditLog.logDeleteBackupDescriptor(spaceId, descriptor));
        int deletedMtaDescriptorsCount = descriptorBackupService.createQuery()
                                                                .spaceId(spaceId)
                                                                .delete();
        LOGGER.info(MessageFormat.format(Messages.DELETED_ORPHANED_MTA_DESCRIPTORS_COUNT, deletedMtaDescriptorsCount));
    }

    private void deleteSpaceIdsLeftovers(List<String> spaceIds) {
        try {
            fileService.deleteBySpaceIds(spaceIds);
        } catch (FileStorageException e) {
            throw new SLException(e, Messages.COULD_NOT_DELETE_SPACEIDS_LEFTOVERS);
        }
    }

}