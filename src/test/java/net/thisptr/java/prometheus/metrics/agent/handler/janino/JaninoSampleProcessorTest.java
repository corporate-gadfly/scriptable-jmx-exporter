package net.thisptr.java.prometheus.metrics.agent.handler.janino;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.TextNode;

import net.thisptr.java.prometheus.metrics.agent.PrometheusMetric;
import net.thisptr.java.prometheus.metrics.agent.Sample;
import net.thisptr.java.prometheus.metrics.agent.config.Config.PrometheusScrapeRule;
import net.thisptr.java.prometheus.metrics.agent.handler.Script;

public class JaninoSampleProcessorTest {
	private final JaninoSampleProcessor sut = new JaninoSampleProcessor();

	private static Sample<PrometheusScrapeRule> sample(final ObjectName objectName, final String attributeName) throws MalformedObjectNameException, InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IntrospectionException {
		final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
		final Object value = server.getAttribute(objectName, attributeName);
		final long timestamp = System.currentTimeMillis();
		final MBeanInfo mbeanInfo = server.getMBeanInfo(objectName);
		final MBeanAttributeInfo attributeInfo = Arrays.stream(mbeanInfo.getAttributes()).filter(a -> attributeName.equals(a.getName())).findFirst().get();
		return new Sample<PrometheusScrapeRule>(null, timestamp, objectName, mbeanInfo, attributeInfo, value);
	}

	@Test
	void testSimple() throws Exception {
		final Sample<PrometheusScrapeRule> sample = sample(new ObjectName("java.lang:type=OperatingSystem"), "ProcessCpuLoad");

		final List<PrometheusMetric> metrics = new ArrayList<>();
		sut.compile("defaultTransformV1(in, out, \"type\")").execute(sample, metrics::add);

		assertThat(metrics.size()).isEqualTo(1);
		assertThat(metrics.get(0).value).isEqualTo((Double) sample.value);
		assertThat(metrics.get(0).name).isEqualTo("java.lang:OperatingSystem:ProcessCpuLoad");
		assertThat(metrics.get(0).labels).isEmpty();
	}

	@Test
	void testArray() throws Exception {
		final Sample<PrometheusScrapeRule> sample = sample(new ObjectName("java.lang:type=Threading"), "AllThreadIds");

		final List<PrometheusMetric> metrics = new ArrayList<>();
		sut.compile("defaultTransformV1(in, out, \"type\")").execute(sample, metrics::add);

		assertThat(metrics.size()).isEqualTo(Array.getLength(sample.value));

		assertThat(metrics.get(0).name).isEqualTo("java.lang:Threading:AllThreadIds");
		assertThat(metrics.get(0).value).isEqualTo(((Number) Array.get(sample.value, 0)).doubleValue());
		assertThat(metrics.get(0).labels.size()).isEqualTo(1);
		assertThat(metrics.get(0).labels.get("index_0")).isEqualTo(TextNode.valueOf("1"));
	}

	@Test
	void testCompositeData() throws Exception {
		final Sample<PrometheusScrapeRule> sample = sample(new ObjectName("java.lang:type=Memory"), "HeapMemoryUsage");

		final List<PrometheusMetric> metrics = new ArrayList<>();
		sut.compile("defaultTransformV1(in, out, \"type\")").execute(sample, metrics::add);

		assertThat(metrics.size()).isEqualTo(4);

		assertThat(metrics.get(0).name).isEqualTo("java.lang:Memory:HeapMemoryUsage_committed");
		assertThat(metrics.get(0).value).isEqualTo(((Number) ((CompositeData) sample.value).get("committed")).doubleValue());
		assertThat(metrics.get(0).labels).isEmpty();

		assertThat(metrics.get(3).name).isEqualTo("java.lang:Memory:HeapMemoryUsage_used");
		assertThat(metrics.get(3).value).isEqualTo(((Number) ((CompositeData) sample.value).get("used")).doubleValue());
		assertThat(metrics.get(3).labels).isEmpty();
	}

	private static ObjectName waitForLastGcInfo() {
		final long start = System.currentTimeMillis();
		byte[] unused;
		long allocated = 0;
		while (true) {
			final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
			for (final ObjectName name : server.queryNames(null, null)) {
				if (!name.getKeyProperty("type").equals("GarbageCollector"))
					continue;
				try {
					if (server.getAttribute(name, "LastGcInfo") == null)
						continue;
				} catch (InstanceNotFoundException | AttributeNotFoundException | MBeanException | ReflectionException e) {
					continue;
				}
				return name;
			}
			unused = new byte[1 * 1024 * 1024];
			allocated += unused.length;
			if (System.currentTimeMillis() > 5 * 1000 + start)
				break;
		}
		throw new IllegalStateException("Time is up. LastGcInfo is still null after 5 seconds (allocated " + allocated + " bytes).");
	}

	@Test
	void testTabularData() throws Exception {
		final Sample<PrometheusScrapeRule> sample = sample(waitForLastGcInfo(), "LastGcInfo");

		final List<PrometheusMetric> metrics = new ArrayList<>();
		sut.compile("defaultTransformV1(in, out, \"type\")").execute(sample, metrics::add);

		System.out.println(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(metrics));

		assertThat(metrics.size()).isEqualTo(4);
	}

	@Test
	@Disabled
	void testAll() throws Exception {
		waitForLastGcInfo();

		final List<Sample<PrometheusScrapeRule>> samples = new ArrayList<>();

		final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
		for (final ObjectName name : server.queryNames(null, null)) {
			for (final MBeanAttributeInfo attribute : server.getMBeanInfo(name).getAttributes()) {
				try {
					samples.add(sample(name, attribute.getName()));
				} catch (Exception e) {
					continue;
				}
			}
		}

		final JaninoSampleProcessor sut1 = new JaninoSampleProcessor();
		// final JsonQuerySampleProcessor sut2 = new JsonQuerySampleProcessor();
		final Script<?> script1 = sut1.compile("defaultTransformV1(in, out, \"type\")");
		// final Script<?> script2 = sut2.compile("default_transform_v1([\"type\"]; true)");

		final long start = System.currentTimeMillis();
		for (int i = 0; i < 1000000; ++i) {
			for (final Sample<PrometheusScrapeRule> sample : samples)
				script1.execute(sample, (a) -> {});
		}
		final long took = System.currentTimeMillis() - start;
		System.out.printf("total %d millsi\n", took);
	}
}