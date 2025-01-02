package org.cloudfoundry.multiapps.controller.process.util;

import org.cloudfoundry.multiapps.common.SLException;
import org.cloudfoundry.multiapps.controller.core.Constants;
import org.cloudfoundry.multiapps.controller.core.helpers.MtaArchiveHelper;
import org.cloudfoundry.multiapps.controller.core.util.ApplicationConfiguration;
import org.cloudfoundry.multiapps.controller.process.Messages;
import org.cloudfoundry.multiapps.controller.process.steps.ProcessContext;
import org.cloudfoundry.multiapps.controller.process.variables.Variables;
import org.cloudfoundry.multiapps.mta.model.DeploymentDescriptor;
import org.cloudfoundry.multiapps.mta.model.Module;
import org.cloudfoundry.multiapps.mta.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MtaArchiveContentResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(MtaArchiveContentResolver.class);

    private final DeploymentDescriptor descriptor;

    private final ApplicationConfiguration configuration;

    private final ContentLengthTracker sizeTracker;

    private final ExternalFileProcessor fileProcessor;

    private final ProcessContext context;

    public MtaArchiveContentResolver(DeploymentDescriptor descriptor, ApplicationConfiguration configuration, ExternalFileProcessor fileProcessor, ContentLengthTracker sizeTracker, ProcessContext context) {
        this.descriptor = descriptor;
        this.configuration = configuration;
        this.fileProcessor = fileProcessor;
        this.sizeTracker = sizeTracker;
        this.context = context;
    }

    public void resolveMtaArchiveFilesInDescriptor(String space, String appArchiveId, MtaArchiveHelper helper) {
        var resolvedFiles = new HashMap<String, Object>();
        resolveResourcesContent(space, appArchiveId, helper, resolvedFiles);
        resolveRequiresDependenciesContent(space, appArchiveId, helper, resolvedFiles);
        long totalSizeOfResolvedEntries = sizeTracker.getTotalSize();
        LOGGER.debug(MessageFormat.format(Messages.TOTAL_SIZE_OF_ALL_RESOLVED_CONTENT_0, totalSizeOfResolvedEntries));
        if (totalSizeOfResolvedEntries > configuration.getMaxResolvedExternalContentSize()) {
            throw new SLException(Messages.ERROR_RESOLVED_FILE_CONTENT_IS_0_WHICH_IS_LARGER_THAN_MAX_1, totalSizeOfResolvedEntries,
                                  configuration.getMaxResolvedExternalContentSize());
        }
        context.setVariable(Variables.RESOLVED_EXTERNAL_FILES, resolvedFiles);
    }

    private void resolveResourcesContent(String space, String appArchiveId, MtaArchiveHelper helper, Map<String, Object> resolvedFiles) {
        Map<String, List<String>> resourceFileAttributes = helper.getResourceFileAttributes();
        var resourcesExternalConfigList = new HashMap<String, String>();
        for (var entry : resourceFileAttributes.entrySet()) {
            Map<String, Object> fileContentForEntry = fileProcessor.processFileContent(space, appArchiveId, entry.getKey());
            mergeResourcesFromFile(entry, resourcesExternalConfigList);
            resolvedFiles.put(entry.getKey(), fileContentForEntry);
        }
        context.setVariable(Variables.EXTERNAL_CONFIGURATION_RESOURCES, resourcesExternalConfigList);
    }

    private void mergeResourcesFromFile(Map.Entry<String, List<String>> entry, HashMap<String, String> resourceFiles) {
        for (Resource resource : descriptor.getResources()) {
            if (entryMatchesResource(entry, resource)) {
                resourceFiles.put(resource.getName(), entry.getKey());
                sizeTracker.incrementFileSize();
            }
        }
    }

    private boolean entryMatchesResource(Map.Entry<String, List<String>> entry, Resource resource) {
        return entry.getValue()
                    .stream()
                    .anyMatch(s -> s.equals(resource.getName()));

    }

    private void resolveRequiresDependenciesContent(String space, String appArchiveId, MtaArchiveHelper helper, HashMap<String, Object> resourceFiles) {
        var requiresDependencyExternalConfigList = new HashMap<String, String>();
        Map<String, List<String>> dependencyFileAttributes = helper.getRequiresDependenciesFileAttributes();
        for (var entry : dependencyFileAttributes.entrySet()) {
            Map<String, Object> fileContentForEntry = fileProcessor.processFileContent(space, appArchiveId, entry.getKey());
            resourceFiles.put(entry.getKey(), fileContentForEntry);
            mergeMtaRequiresDependencyInModules(entry, requiresDependencyExternalConfigList);
        }
        context.setVariable(Variables.EXTERNAL_CONFIGURATION_REQUIRES_DEPENDENCY, requiresDependencyExternalConfigList);
    }

    private void mergeMtaRequiresDependencyInModules(Map.Entry<String, List<String>> entry, HashMap<String, String> requiresDependencyExternalConfigList) {
        for (Module module : descriptor.getModules()) {
            resolveRequiredDependencyFileParameters(module, entry, requiresDependencyExternalConfigList);
        }
    }

    private void resolveRequiredDependencyFileParameters(Module module, Map.Entry<String, List<String>> entry, HashMap<String, String> requiresDependencyExternalConfigList) {
        for (String requiresPath : entry.getValue()) {
            String[] splitEntry = requiresPath.split(Constants.MTA_ELEMENT_SEPARATOR);
            String requiresDependencyModuleName = splitEntry[0];
            if (module.getName()
                      .equals(requiresDependencyModuleName)) {
                requiresDependencyExternalConfigList.put(requiresPath, entry.getKey());
                sizeTracker.incrementFileSize();
            }
        }
    }

}
