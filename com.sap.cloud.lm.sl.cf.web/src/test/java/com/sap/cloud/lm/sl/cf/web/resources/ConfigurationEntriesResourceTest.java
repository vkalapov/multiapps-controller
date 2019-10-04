package com.sap.cloud.lm.sl.cf.web.resources;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.cloudfoundry.client.lib.CloudControllerClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Answers;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.sap.cloud.lm.sl.cf.core.auditlogging.AuditLoggingProvider;
import com.sap.cloud.lm.sl.cf.core.auditlogging.impl.AuditLoggingFacadeSLImpl;
import com.sap.cloud.lm.sl.cf.core.cf.CloudControllerClientProvider;
import com.sap.cloud.lm.sl.cf.core.dto.serialization.ConfigurationEntryDto;
import com.sap.cloud.lm.sl.cf.core.model.CloudTarget;
import com.sap.cloud.lm.sl.cf.core.model.ConfigurationEntry;
import com.sap.cloud.lm.sl.cf.core.persistence.query.ConfigurationEntryQuery;
import com.sap.cloud.lm.sl.cf.core.persistence.service.ConfigurationEntryService;
import com.sap.cloud.lm.sl.cf.core.util.ApplicationConfiguration;
import com.sap.cloud.lm.sl.cf.core.util.MockBuilder;
import com.sap.cloud.lm.sl.cf.core.util.UserInfo;
import com.sap.cloud.lm.sl.common.util.TestCase;
import com.sap.cloud.lm.sl.common.util.TestInput;
import com.sap.cloud.lm.sl.common.util.TestUtil;
import com.sap.cloud.lm.sl.common.util.Tester;
import com.sap.cloud.lm.sl.common.util.Tester.Expectation;
import com.sap.cloud.lm.sl.common.util.XmlUtil;

@RunWith(Parameterized.class)
public class ConfigurationEntriesResourceTest {

    private static final Tester TESTER = Tester.forClass(ConfigurationEntriesResourceTest.class);

    private final TestCase<TestInput> test;

    public ConfigurationEntriesResourceTest(TestCase<TestInput> test) {
        this.test = test;
    }

    @Parameters
    public static List<Object[]> getParameters() throws Exception {
        return Arrays.asList(new Object[][] {
// @formatter:off
            // (0)
            {
                new PostRequestTest(new PostRequestTestInput("configuration-entry-01.xml"),
                    new Expectation(Expectation.Type.JSON, "configuration-entries-resource-test-output-01.json")),
            },
            // (1)
            {
               new PostRequestTest(new PostRequestTestInput("configuration-entry-02.xml"),
                   new Expectation(Expectation.Type.EXCEPTION, "cvc-complex-type.2.4.b: The content of element configuration-entry is not complete")),
            },
            // (2)
            {
                new PostRequestTest(new PostRequestTestInput("configuration-entry-03.xml"),
                    new Expectation(Expectation.Type.EXCEPTION, "Target does not contain 'org' and 'space' parameters")),
            },
            // (3)
            {
                new PostRequestTest(new PostRequestTestInput("configuration-entry-04.xml"),
                    new Expectation(Expectation.Type.JSON, "configuration-entries-resource-test-output-04.json")),
            },
            // (4)
            {
                new PostRequestTest(new PostRequestTestInput("configuration-entry-05.xml"),
                    new Expectation(Expectation.Type.EXCEPTION, "Target does not contain 'org' and 'space' parameters")),
            },
            // (5)
            {
                new GetRequestTest(new GetRequestTestInput(100, "configuration-entry-05.json"),
                    new Expectation(Expectation.Type.JSON, "configuration-entries-resource-test-output-05.json")),
            },
            // (6)
            {
                new GetRequestTest(new GetRequestTestInput(100, "configuration-entry-06.json"),
                    new Expectation(Expectation.Type.JSON, "configuration-entries-resource-test-output-06.json")),
           },
            // (7)
            {
                new DeleteRequestTest(new DeleteRequestTestInput(100),
                    new Expectation(Expectation.Type.JSON, "configuration-entries-resource-test-output-07.json")),
            },
            // (8)
            {
                new SearchRequestTest(new SearchRequestTestInput(Arrays.asList("foo:bar", "baz:qux"), "parsed-properties-01.json"),
                    new Expectation(Expectation.Type.JSON, "configuration-entries-resource-test-output-08.json")),
            },
            // (9)
            {
                new PutRequestTest(new PutRequestTestInput(100, "configuration-entries-resource-test-input-09.xml"),
                    new Expectation(Expectation.Type.JSON, "configuration-entries-resource-test-output-09.json")),
            },
            // (10)
            {
                new PutRequestTest(new PutRequestTestInput(100, "configuration-entries-resource-test-input-10.xml"),
                    new Expectation(Expectation.Type.JSON, "configuration-entries-resource-test-output-10.json")),
            },
            // (11)
            {
                new PutRequestTest(new PutRequestTestInput(100, "configuration-entries-resource-test-input-11.xml"),
                    new Expectation(Expectation.Type.EXCEPTION, "A configuration entry's id cannot be updated")),
            },
            // (12)
            {
                new SearchRequestTest(new SearchRequestTestInput(Arrays.asList("{\"foo\":\"bar\",\"baz\":\"qux\"}"), "parsed-properties-01.json"),
                    new Expectation(Expectation.Type.JSON, "configuration-entries-resource-test-output-08.json")),
            },
            // (13)
            {
                new SearchRequestTest(new SearchRequestTestInput(Arrays.asList("a"), "parsed-properties-01.json"),
                    new Expectation(Expectation.Type.EXCEPTION, "Could not parse content query parameter as JSON or list")),
            },
            // (14)
            {
                new PostRequestTest(new PostRequestTestInput("configuration-entry-06.xml"),
                    new Expectation(Expectation.Type.JSON, "configuration-entries-resource-test-output-01.json")),
            },
            // (15)
            {
                new PutRequestTest(new PutRequestTestInput(100, "configuration-entries-resource-test-input-12.xml"),
                    new Expectation(Expectation.Type.JSON, "configuration-entries-resource-test-output-09.json")),
            },
            // (16)
            {
                new PostRequestTest(new PostRequestTestInput("configuration-entry-07.xml"),
                    new Expectation(Expectation.Type.JSON, "configuration-entries-resource-test-output-01.json")),
            },
            
// @formatter:on
        });
    }

    @Test
    public void test() throws Exception {
        test.run();
    }

    private static class GetRequestTestInput extends TestInput {

        private final long id;
        private final ConfigurationEntry entry;

        public GetRequestTestInput(long id, String entryJsonLocation) {
            this.id = id;
            this.entry = loadJsonInput(entryJsonLocation, ConfigurationEntry.class, getClass());
        }

        public long getId() {
            return id;
        }

        public ConfigurationEntry getEntry() {
            return entry;
        }

    }

    private static class SearchRequestTestInput extends TestInput {

        private final List<String> requiredContent;

        public SearchRequestTestInput(List<String> requiredContent, String parsedRequiredContentLocation) {
            this.requiredContent = requiredContent;
        }

        public List<String> getRequiredContent() {
            return requiredContent;
        }
    }

    private static class PostRequestTestInput extends TestInput {

        private final String entryXml;

        public PostRequestTestInput(String entryXmlLocation) {
            this.entryXml = TestUtil.getResourceAsString(entryXmlLocation, getClass());
        }

        public String getEntryXml() {
            return entryXml;
        }

    }

    private static class PutRequestTestInput extends PostRequestTestInput {

        private long id;

        public PutRequestTestInput(long id, String entryXmlLocation) throws Exception {
            super(entryXmlLocation);
        }

        public long getId() {
            return id;
        }

    }

    private static class DeleteRequestTestInput extends TestInput {

        private final long id;

        public DeleteRequestTestInput(long id) {
            this.id = id;
        }

        public long getId() {
            return id;
        }

    }

    private static class GetRequestTest extends TestCase<GetRequestTestInput> {

        @Mock
        private ConfigurationEntryService configurationEntryService;
        @Mock(answer = Answers.RETURNS_SELF)
        private ConfigurationEntryQuery configurationEntryQuery;
        @InjectMocks
        private ConfigurationEntriesResource resource = new ConfigurationEntriesResource();

        public GetRequestTest(GetRequestTestInput input, Expectation expectation) {
            super(input, expectation);
        }

        @Override
        protected void test() {
            TESTER.test(() -> {

                return new RestResponse(resource.getConfigurationEntry(input.getId()));

            }, expectation);
        }

        @Override
        protected void setUp() {
            MockitoAnnotations.initMocks(this);
            when(configurationEntryService.createQuery()).thenReturn(configurationEntryQuery);
            Mockito.doReturn(input.getEntry())
                   .when(configurationEntryQuery)
                   .singleResult();
        }

    }

    private static class PostRequestTest extends TestCase<PostRequestTestInput> {

        @Mock
        private ConfigurationEntryService configurationEntryService;
        @Mock
        private AuditLoggingFacadeSLImpl auditLoggingFacade;
        @Mock
        private ApplicationConfiguration configuration;
        @InjectMocks
        private ConfigurationEntriesResource resource = new ConfigurationEntriesResource();

        public PostRequestTest(PostRequestTestInput input, Expectation expectation) {
            super(input, expectation);
        }

        @Override
        protected void test() {

            TESTER.test(() -> {

                return new RestResponse(resource.createConfigurationEntry(input.getEntryXml()));

            }, expectation);
        }

        @Override
        protected void setUp() {
            MockitoAnnotations.initMocks(this);
            AuditLoggingProvider.setFacade(auditLoggingFacade);
        }

    }

    private static class PutRequestTest extends TestCase<PutRequestTestInput> {

        @Mock
        private ConfigurationEntryService configurationEntryService;
        @Mock
        private ApplicationConfiguration configuration;
        @InjectMocks
        private ConfigurationEntriesResource resource = new ConfigurationEntriesResource();

        public PutRequestTest(PutRequestTestInput input, Expectation expectation) {
            super(input, expectation);
        }

        @Override
        protected void test() {
            TESTER.test(() -> {

                return new RestResponse(resource.updateConfigurationEntry(input.getId(), input.getEntryXml()));

            }, expectation);
        }

        @Override
        protected void setUp() throws Exception {
            MockitoAnnotations.initMocks(this);
            ConfigurationEntryDto dto = getDto();
            ConfigurationEntryMatcher entryMatcher = new ConfigurationEntryMatcher(dto);
            when(configurationEntryService.update(eq(input.getId()), argThat(entryMatcher))).thenReturn(dto.toConfigurationEntry());
        }

        private ConfigurationEntryDto getDto() {
            return provideDefaultsForFields(XmlUtil.fromXml(input.getEntryXml(), ConfigurationEntryDto.class));
        }

        private ConfigurationEntryDto provideDefaultsForFields(ConfigurationEntryDto dto) {
            return new ConfigurationEntryDto(dto.toConfigurationEntry());
        }

    }

    private static class SearchRequestTest extends TestCase<SearchRequestTestInput> {

        private static final String PROVIDER_NID = "N";
        private static final String ORG = "O";
        private static final String SPACE = "S";
        private static final CloudTarget TARGET_SPACE = new CloudTarget("O", "S");
        private static final String PROVIDER_VERSION = "V";
        private static final String PROVIDER_ID = "I";

        @Mock
        private CloudControllerClient client;
        @Mock
        private CloudControllerClientProvider clientProvider;
        @Mock
        private ConfigurationEntryService configurationEntryService;
        @Mock(answer = Answers.RETURNS_SELF)
        private ConfigurationEntryQuery configurationEntryQuery;
        @Mock
        private UserInfo userInfo;
        @Mock
        private ApplicationConfiguration configuration;
        @InjectMocks
        private ConfigurationEntriesResource resource = new ConfigurationEntriesResource();

        public SearchRequestTest(SearchRequestTestInput input, Expectation expectation) {
            super(input, expectation);
        }

        @Override
        protected void test() {
            TESTER.test(() -> {

                return new RestResponse(resource.getConfigurationEntries(PROVIDER_NID, PROVIDER_ID, PROVIDER_VERSION,
                                                                         input.getRequiredContent(), null, ORG, SPACE));

            }, expectation);
        }

        @Override
        protected void setUp() {
            MockitoAnnotations.initMocks(this);
            resource.userInfoSupplier = () -> userInfo;
            when(userInfo.getName()).thenReturn("");
            when(clientProvider.getControllerClient("")).thenReturn(client);
            when(client.getSpaces()).thenReturn(Collections.emptyList());
            when(configurationEntryService.createQuery()).thenReturn(configurationEntryQuery);
        }
    }

    private static class DeleteRequestTest extends TestCase<DeleteRequestTestInput> {

        @Mock
        private ConfigurationEntryService configurationEntryService;
        @Mock(answer = Answers.RETURNS_SELF)
        private ConfigurationEntryQuery configurationEntryQuery;
        @Mock
        private AuditLoggingFacadeSLImpl auditLoggingFacade;
        @Mock
        private ApplicationConfiguration configuration;
        @InjectMocks
        private ConfigurationEntriesResource resource = new ConfigurationEntriesResource();

        public DeleteRequestTest(DeleteRequestTestInput input, Expectation expectation) {
            super(input, expectation);
        }

        @Override
        protected void test() {
            TESTER.test(() -> {

                return new RestResponse(resource.deleteConfigurationEntry(input.getId()));

            }, expectation);
        }

        @Override
        protected void setUp() {
            MockitoAnnotations.initMocks(this);
            when(configurationEntryService.createQuery()).thenReturn(configurationEntryQuery);
            ConfigurationEntryQuery inputQuery = new MockBuilder<>(configurationEntryQuery).on(queryMock -> queryMock.id(input.getId()))
                                                                                           .build();
            Mockito.doReturn(new ConfigurationEntry(input.getId(), null, null, null, null, null, null, null))
                   .when(inputQuery)
                   .singleResult();
            AuditLoggingProvider.setFacade(auditLoggingFacade);
        }

        protected void tearDown() {
            verify(configurationEntryService.createQuery()
                                            .id(input.getId())).delete();
        }

    }

    private static class ConfigurationEntryMatcher implements ArgumentMatcher<ConfigurationEntry> {

        private final String xml;

        public ConfigurationEntryMatcher(ConfigurationEntryDto dto) {
            this.xml = XmlUtil.toXml(dto, true);
        }

        @Override
        public boolean matches(ConfigurationEntry entry) {
            try {
                return xml.trim()
                          .equals(XmlUtil.toXml(new ConfigurationEntryDto(entry), true)
                                         .trim());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

}
