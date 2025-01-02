package org.cloudfoundry.multiapps.controller.process.util;

import com.sap.cloudfoundry.client.facade.CloudControllerClient;
import com.sap.cloudfoundry.client.facade.CloudOperationException;
import com.sap.cloudfoundry.client.facade.domain.CloudApplication;
import com.sap.cloudfoundry.client.facade.domain.CloudServiceBinding;
import org.cloudfoundry.multiapps.common.SLException;
import org.cloudfoundry.multiapps.controller.client.lib.domain.BindingDetails;
import org.cloudfoundry.multiapps.controller.client.lib.domain.CloudApplicationExtended;
import org.cloudfoundry.multiapps.controller.client.lib.domain.CloudServiceInstanceExtended;
import org.cloudfoundry.multiapps.controller.core.Constants;
import org.cloudfoundry.multiapps.controller.core.security.serialization.SecureSerialization;
import org.cloudfoundry.multiapps.controller.process.Messages;
import org.cloudfoundry.multiapps.controller.process.steps.ProcessContext;
import org.cloudfoundry.multiapps.controller.process.variables.Variables;
import org.cloudfoundry.multiapps.mta.util.PropertiesUtil;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ServiceBindingParametersGetter {

    private final ProcessContext context;

    public ServiceBindingParametersGetter(ProcessContext context) {
        this.context = context;
    }

    public Map<String, Object> getServiceBindingParametersFromMta(CloudApplicationExtended app, String serviceName) {
        Optional<CloudServiceInstanceExtended> service = getService(context.getVariable(Variables.SERVICES_TO_BIND), serviceName);
        if (service.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> descriptorProvidedBindingParameters = getDescriptorProvidedBindingParameters(app, service.get());
        Map<String, Object> fileProvidedBindingParameters = getFileProvidedBindingParameters(app, service.get());
        Map<String, Object> bindingParameters = PropertiesUtil.mergeExtensionProperties(fileProvidedBindingParameters, descriptorProvidedBindingParameters);
        context.getStepLogger()
               .error(Messages.BINDING_PARAMETERS_FOR_APPLICATION, app.getName(), SecureSerialization.toJson(bindingParameters));
        return bindingParameters;
    }

    private Optional<CloudServiceInstanceExtended> getService(List<CloudServiceInstanceExtended> services, String serviceName) {
        return services.stream()
                       .filter(service -> service.getName()
                                                 .equals(serviceName))
                       .findFirst();
    }

    private Map<String, Object> getDescriptorProvidedBindingParameters(CloudApplicationExtended app, CloudServiceInstanceExtended service) {
        BindingDetails bindingDetails = getDescriptorProvidedBindingParametersAndBindingName(app, service);
        return (bindingDetails != null && bindingDetails.getConfig() != null) ? bindingDetails.getConfig() : Collections.emptyMap();
    }

    private BindingDetails getDescriptorProvidedBindingParametersAndBindingName(CloudApplicationExtended app, CloudServiceInstanceExtended service) {
        return app.getBindingParameters()
                  .get(service.getResourceName());
    }

    private Map<String, Object> getFileProvidedBindingParameters(CloudApplicationExtended app, CloudServiceInstanceExtended service) {
        Map<String, String> requiresConfigurationFiles = context.getVariable(Variables.EXTERNAL_CONFIGURATION_REQUIRES_DEPENDENCY);
        for (Map.Entry<String, String> entry : requiresConfigurationFiles.entrySet()) {
            if (hasParametersFromFile(entry, app, service)) {
                Map<String, Object> resolvedFilesForDependency = getExternalFileForEntry(entry);
                if (resolvedFilesForDependency != null) {
                    return resolvedFilesForDependency;
                }
            }

        }
        return Collections.emptyMap();
    }

    private boolean hasParametersFromFile(Map.Entry<String, String> entry, CloudApplicationExtended app, CloudServiceInstanceExtended service) {
        String requiresPath = entry.getKey();
        String[] splitEntry = requiresPath.split(Constants.MTA_ELEMENT_SEPARATOR);
        String requiresDependencyModuleName = splitEntry[0];
        String requiresDependencyName = splitEntry[1];
        return app.getName()
                  .equals(requiresDependencyModuleName) && requiresDependencyName.equals(service.getResourceName());
    }

    private Map<String, Object> getExternalFileForEntry(Map.Entry<String, String> entry) {
        return (Map<String, Object>) context.getVariable(Variables.RESOLVED_EXTERNAL_FILES)
                                            .get(entry.getValue());
    }

    public String getDescriptorProvidedBindingName(CloudApplicationExtended app, String serviceName) {
        Optional<CloudServiceInstanceExtended> service = getService(context.getVariable(Variables.SERVICES_TO_BIND), serviceName);
        if (service.isEmpty()) {
            return null;
        }
        BindingDetails bindingDetails = getDescriptorProvidedBindingParametersAndBindingName(app, service.get());
        return (bindingDetails != null && bindingDetails.getBindingName() != null) ? bindingDetails.getBindingName() : null;
    }

    public Map<String, Object> getServiceBindingParametersFromExistingInstance(CloudApplication application, String serviceName) {
        CloudControllerClient client = context.getControllerClient();
        UUID serviceGuid = client.getRequiredServiceInstanceGuid(serviceName);
        CloudServiceBinding serviceBinding = client.getServiceBindingForApplication(application.getGuid(), serviceGuid);
        if (serviceBinding == null) {
            throw new SLException(Messages.SERVICE_INSTANCE_0_NOT_BOUND_TO_APP_1, serviceName, application.getName());
        }

        try {
            return client.getServiceBindingParameters(serviceBinding.getGuid());
        } catch (CloudOperationException e) {
            if (HttpStatus.NOT_IMPLEMENTED == e.getStatusCode() || HttpStatus.BAD_REQUEST == e.getStatusCode()) {
                // ignore 501 and 400 error codes from service brokers
                context.getStepLogger()
                       .warnWithoutProgressMessage(Messages.CANNOT_RETRIEVE_PARAMETERS_OF_BINDING_BETWEEN_APPLICATION_0_AND_SERVICE_INSTANCE_1,
                                                   application.getName(), serviceName);
                return null;
            } else if (HttpStatus.BAD_GATEWAY == e.getStatusCode()) {
                // TODO: this is a temporary fix for external error code mapping leading to incorrect 502 errors 
                context.getStepLogger()
                       .warnWithoutProgressMessage(Messages.CANNOT_RETRIEVE_PARAMETERS_OF_BINDING_BETWEEN_APPLICATION_0_AND_SERVICE_INSTANCE_1_FIX,
                                                   application.getName(), serviceName);
                return null;
            }
            throw e;
        }
    }

}
