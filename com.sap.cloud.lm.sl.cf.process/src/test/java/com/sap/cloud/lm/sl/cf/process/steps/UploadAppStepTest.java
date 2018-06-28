package com.sap.cloud.lm.sl.cf.process.steps;

import static java.text.MessageFormat.format;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import org.cloudfoundry.client.lib.CloudControllerException;
import org.cloudfoundry.client.lib.CloudFoundryException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpStatus;

import com.sap.cloud.lm.sl.cf.client.util.InputStreamProducer;
import com.sap.cloud.lm.sl.cf.core.util.ApplicationConfiguration;
import com.sap.cloud.lm.sl.cf.process.Constants;
import com.sap.cloud.lm.sl.cf.process.message.Messages;
import com.sap.cloud.lm.sl.cf.process.steps.ScaleAppStepTest.SimpleApplication;
import com.sap.cloud.lm.sl.common.SLException;
import com.sap.cloud.lm.sl.persistence.processors.FileDownloadProcessor;

@RunWith(Parameterized.class)
public class UploadAppStepTest extends SyncActivitiStepTest<UploadAppStep> {

    private static final IOException IO_EXCEPTION = new IOException();
    private static final CloudFoundryException CF_EXCEPTION = new CloudFoundryException(HttpStatus.BAD_REQUEST);

    private static final String APP_NAME = "sample-app-backend";
    private static final String APP_FILE = "web.zip";
    private static final String TOKEN = "token";
    private static final String SPACE = "space";
    private static final String APP_ARCHIVE = "sample-app.mtar";

    public TemporaryFolder tempDir = new TemporaryFolder();
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Parameters
    public static Iterable<Object[]> getParameters() {
        return Arrays.asList(new Object[][] {
// @formatter:off
                // (00)
                {
                    null, null,
                },
                // (01)
                {
                    null, null,
                },
                // (02)
                {
                    format(Messages.ERROR_RETRIEVING_MTA_MODULE_CONTENT, APP_FILE), null,
                },
                // (03)
                {
                    format(Messages.ERROR_RETRIEVING_MTA_MODULE_CONTENT, APP_FILE), null,
                },
                // (04)
                {
                    null, StepsUtil.createException(CF_EXCEPTION).getMessage(),
                },
                // (05)
                {
                    null, StepsUtil.createException(CF_EXCEPTION).getMessage(),
                },
// @formatter:on
        });
    }

    private String expectedIOExceptionMessage;
    private String expectedCFExceptionMessage;

    private File appFile;

    public UploadAppStepTest(String expectedIOExceptionMessage, String expectedCFExceptionMessage) {
        this.expectedIOExceptionMessage = expectedIOExceptionMessage;
        this.expectedCFExceptionMessage = expectedCFExceptionMessage;
    }

    @Before
    public void setUp() throws Exception {
        loadParameters();
        prepareFileService();
        prepareContext();
        prepareClients();
    }

    @Test
    public void test() throws Throwable {
        try {
            step.execute(context);
        } catch (Throwable e) {
            e.printStackTrace();
            assertFalse(appFile.exists());
            throw e;
        }

        assertCall(Constants.VAR_UPLOAD_TOKEN, TOKEN);
    }

    private void assertCall(String variableName, String variableValue) {
        Mockito.verify(context)
            .setVariable(variableName, variableValue);
    }

    public void loadParameters() throws Exception {
        if (expectedIOExceptionMessage != null) {
            expectedException.expectMessage(expectedIOExceptionMessage);
            expectedException.expect(SLException.class);
        }
        if (expectedCFExceptionMessage != null) {
            expectedException.expectMessage(expectedCFExceptionMessage);
            expectedException.expect(CloudControllerException.class);
        }
    }

    public void prepareContext() throws Exception {
        when(configuration.getUploadAppTimeout()).thenReturn(ApplicationConfiguration.DEFAULT_UPLOAD_APP_TIMEOUT);
        StepsUtil.setApp(context, new SimpleApplication(APP_NAME, 2).toCloudApplication());
        context.setVariable(Constants.VAR_APPS_INDEX, 0);
        context.setVariable(Constants.PARAM_APP_ARCHIVE_ID, APP_ARCHIVE);
        context.setVariable(com.sap.cloud.lm.sl.persistence.message.Constants.VARIABLE_NAME_SPACE_ID, SPACE);
        StepsUtil.setModuleFileName(context, APP_NAME, APP_FILE);
        StepsUtil.setAppPropertiesChanged(context, false);
    }

    public void prepareClients() throws Exception {
        if (expectedIOExceptionMessage == null && expectedCFExceptionMessage == null) {
            when(client.asyncUploadApplication(eq(APP_NAME), eq(appFile), any())).thenReturn(TOKEN);
        } else if (expectedIOExceptionMessage != null) {
            when(client.asyncUploadApplication(eq(APP_NAME), eq(appFile), any())).thenThrow(IO_EXCEPTION);
        } else if (expectedCFExceptionMessage != null) {
            when(client.asyncUploadApplication(eq(APP_NAME), eq(appFile), any())).thenThrow(CF_EXCEPTION);
        }
        when(client.getApplication(APP_NAME)).thenReturn(new SimpleApplication(APP_NAME, 2).toCloudApplication());
    }

    public void prepareFileService() throws Exception {
        tempDir.create();
        appFile = tempDir.newFile(APP_FILE);
        doAnswer(new Answer<Void>() {
            public Void answer(InvocationOnMock invocation) throws Exception {
                ((FileDownloadProcessor) invocation.getArguments()[0]).processContent(null);
                return null;
            }
        }).when(fileService)
            .processFileContent(any());
    }

    private class UploadAppStepMock extends UploadAppStep {

        @Override
        public File saveToFile(String fileName, InputStreamProducer streamProducer) throws IOException {
            InputStream stream = streamProducer.getNextInputStream();
            assertNotNull(stream);
            Files.copy(stream, appFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return appFile;
        }

        @Override
        InputStreamProducer getInputStreamProducer(InputStream appArchiveStream, String fileName, long maxStreamSize) throws SLException {
            if (!fileName.equals(APP_FILE)) {
                return super.getInputStreamProducer(appArchiveStream, fileName, maxStreamSize);
            }
            return new InputStreamProducer(getClass().getResourceAsStream(APP_FILE), fileName,
                ApplicationConfiguration.DEFAULT_MAX_RESOURCE_FILE_SIZE) {
                @Override
                public InputStream getNextInputStream() {
                    return getClass().getResourceAsStream(APP_FILE);
                }
            };
        }
    }

    @Override
    protected UploadAppStep createStep() {
        return new UploadAppStepMock();
    }

}
