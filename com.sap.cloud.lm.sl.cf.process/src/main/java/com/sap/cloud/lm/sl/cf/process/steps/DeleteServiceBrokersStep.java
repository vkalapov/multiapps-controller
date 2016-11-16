package com.sap.cloud.lm.sl.cf.process.steps;

import static com.sap.cloud.lm.sl.cf.process.steps.CreateServiceBrokersStep.getServiceBrokerNames;
import static java.text.MessageFormat.format;

import java.text.MessageFormat;
import java.util.List;
import java.util.function.Function;

import org.activiti.engine.delegate.DelegateExecution;
import org.cloudfoundry.client.lib.CloudFoundryException;
import org.cloudfoundry.client.lib.CloudFoundryOperations;
import org.cloudfoundry.client.lib.domain.CloudApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.sap.activiti.common.ExecutionStatus;
import com.sap.cloud.lm.sl.cf.core.model.SupportedParameters;
import com.sap.cloud.lm.sl.cf.process.message.Messages;
import com.sap.cloud.lm.sl.common.SLException;
import com.sap.cloud.lm.sl.slp.model.StepMetadata;

@Component("deleteServiceBrokersStep")
public class DeleteServiceBrokersStep extends AbstractXS2ProcessStep {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateServiceBrokersStep.class);

    public static StepMetadata getMetadata() {
        return new StepMetadata("deleteServiceBrokersTask", "Delete Service Brokers", "Delete Service Brokers");
    }

    protected Function<DelegateExecution, CloudFoundryOperations> clientSupplier = (context) -> getCloudFoundryClient(context, LOGGER);

    @Override
    protected ExecutionStatus executeStepInternal(DelegateExecution context) throws SLException {
        logActivitiTask(context, LOGGER);
        try {
            info(context, Messages.DELETING_SERVICE_BROKERS, LOGGER);

            List<CloudApplication> appsToUndeploy = StepsUtil.getAppsToUndeploy(context);
            CloudFoundryOperations client = clientSupplier.apply(context);
            List<String> serviceBrokersToCreate = getServiceBrokerNames(StepsUtil.getServiceBrokersToCreate(context));

            for (CloudApplication app : appsToUndeploy) {
                deleteServiceBrokerIfNecessary(context, app, serviceBrokersToCreate, client);
            }

            debug(context, Messages.SERVICE_BROKERS_DELETED, LOGGER);
            return ExecutionStatus.SUCCESS;
        } catch (SLException e) {
            error(context, Messages.ERROR_DELETING_SERVICE_BROKERS, e, LOGGER);
            throw e;
        } catch (CloudFoundryException cfe) {
            SLException e = StepsUtil.createException(cfe);
            error(context, Messages.ERROR_DELETING_SERVICE_BROKERS, e, LOGGER);
            throw e;
        }
    }

    private void deleteServiceBrokerIfNecessary(DelegateExecution context, CloudApplication app, List<String> serviceBrokersToCreate,
        CloudFoundryOperations client) throws SLException {
        if (!StepsUtil.getAppAttribute(app, SupportedParameters.CREATE_SERVICE_BROKER, false)) {
            return;
        }
        String name = StepsUtil.getAppAttribute(app, SupportedParameters.SERVICE_BROKER_NAME, app.getName());

        if (serviceBrokerExists(name, client) && !serviceBrokersToCreate.contains(name)) {
            try {
                info(context, MessageFormat.format(Messages.DELETING_SERVICE_BROKER, name, app.getName()), LOGGER);
                client.deleteServiceBroker(name);
                debug(context, MessageFormat.format(Messages.DELETED_SERVICE_BROKER, name, app.getName()), LOGGER);
            } catch (CloudFoundryException e) {
                switch (e.getStatusCode()) {
                    case FORBIDDEN:
                        warn(context, format(Messages.DELETE_OF_SERVICE_BROKERS_FAILED_403, name), LOGGER);
                        break;
                    default:
                        throw e;
                }
            }
        }
    }

    private boolean serviceBrokerExists(String serviceBrokerName, CloudFoundryOperations client) {
        try {
            client.getServiceBroker(serviceBrokerName);
        } catch (CloudFoundryException e) {
            if (e.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                return false;
            }
            throw e;
        }
        return true;
    }

}
