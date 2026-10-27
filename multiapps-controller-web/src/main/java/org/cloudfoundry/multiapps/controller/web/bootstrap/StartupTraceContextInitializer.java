package org.cloudfoundry.multiapps.controller.web.bootstrap;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;

/**
 * Dev-only startup profiler. Inert unless {@code -Dds.startup.trace=true} is set
 * (e.g. via JBP_CONFIG_JAVA_OPTS for a dev push). When enabled, it attaches a
 * recording {@link ApplicationStartup} to the context and, once the context has
 * refreshed, dumps each recorded startup step and its duration to {@code System.out}.
 *
 * <p>stdout is used deliberately: it reaches {@code cf logs} and is independent of
 * the logging-level-change-agent, which only rewrites SLF4J levels and therefore
 * cannot suppress these lines. This is the only way to attribute the otherwise-silent
 * Spring context-refresh window from a live CF container.
 *
 * <p>This app is a classic Spring WAR, not Spring Boot, so Boot's
 * {@code BufferingApplicationStartup} is not on the classpath; the recorder below is
 * implemented directly against the {@code spring-core} {@link ApplicationStartup} SPI.
 *
 * <p>Registered via the {@code contextInitializerClasses} context-param in web.xml.
 */
public class StartupTraceContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final String TRACE_FLAG = "ds.startup.trace";
    // Steps faster than this are noise; drop them so the dump stays readable.
    private static final long MIN_REPORTED_MILLIS = 5L;

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        if (!Boolean.getBoolean(TRACE_FLAG)) {
            return;
        }
        RecordingApplicationStartup startup = new RecordingApplicationStartup();
        // Set on the context, not context.getBeanFactory(): before refresh the bean
        // factory is not yet available on a web context and getBeanFactory() throws
        // IllegalStateException. ConfigurableApplicationContext.setApplicationStartup
        // propagates to the factory during refresh.
        context.setApplicationStartup(startup);
        context.addApplicationListener(event -> {
            if (event instanceof ContextRefreshedEvent) {
                startup.dump();
            }
        });
    }

    private static final class RecordingApplicationStartup implements ApplicationStartup {

        private final List<Recorded> recorded = new ArrayList<>();
        private final AtomicLong ids = new AtomicLong();

        @Override
        public StartupStep start(String name) {
            Recorded step = new Recorded(name, ids.incrementAndGet(), System.nanoTime());
            synchronized (recorded) {
                recorded.add(step);
            }
            return step;
        }

        void dump() {
            System.out.println("=== [ds.startup.trace] Spring startup steps (>= " + MIN_REPORTED_MILLIS + "ms, slowest first) ===");
            List<Recorded> snapshot;
            synchronized (recorded) {
                snapshot = new ArrayList<>(recorded);
            }
            snapshot.stream()
                    .filter(Recorded::isEnded)
                    .filter(r -> r.millis() >= MIN_REPORTED_MILLIS)
                    .sorted((a, b) -> Long.compare(b.millis(), a.millis()))
                    .forEach(r -> System.out.printf("[ds.startup.trace] %6d ms  %-40s %s%n", r.millis(), r.name, r.tagsString()));
            System.out.println("=== [ds.startup.trace] end ===");
        }

        private static final class Recorded implements StartupStep, StartupStep.Tags {

            private final String name;
            private final long id;
            private final long startNanos;
            private volatile long endNanos = -1L;
            private final java.util.Map<String, String> tags = new java.util.LinkedHashMap<>();

            Recorded(String name, long id, long startNanos) {
                this.name = name;
                this.id = id;
                this.startNanos = startNanos;
            }

            boolean isEnded() {
                return endNanos >= 0;
            }

            long millis() {
                return (endNanos - startNanos) / 1_000_000L;
            }

            String tagsString() {
                return tags.isEmpty() ? "" : tags.toString();
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public long getId() {
                return id;
            }

            @Override
            public Long getParentId() {
                return null;
            }

            @Override
            public StartupStep tag(String key, String value) {
                tags.put(key, value);
                return this;
            }

            @Override
            public StartupStep tag(String key, java.util.function.Supplier<String> value) {
                tags.put(key, value.get());
                return this;
            }

            @Override
            public Tags getTags() {
                return this;
            }

            @Override
            public void end() {
                this.endNanos = System.nanoTime();
            }

            @Override
            public java.util.Iterator<Tag> iterator() {
                return java.util.Collections.emptyIterator();
            }
        }
    }
}
