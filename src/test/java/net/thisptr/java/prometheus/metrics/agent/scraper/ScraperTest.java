package net.thisptr.java.prometheus.metrics.agent.scraper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import net.thisptr.java.prometheus.metrics.agent.misc.AttributeNamePattern;

public class ScraperTest {

	private static class Rule implements ScrapeRule {
		private final boolean skip;
		private final AttributeNamePattern pattern;

		public Rule(final AttributeNamePattern pattern, final boolean skip) {
			this.pattern = pattern;
			this.skip = skip;
		}

		@Override
		public List<AttributeNamePattern> patterns() {
			if (pattern == null)
				return null;
			return Arrays.asList(pattern);
		}

		@Override
		public boolean skip() {
			return skip;
		}
	}

	@Test
	void testName() throws Exception {
		final List<Rule> rules = Arrays.asList(new Rule(AttributeNamePattern.compile("java.lang:type=OperatingSystem:SystemCpuLoad"), false), new Rule(null, true));
		final Scraper<Rule> scraper = new Scraper<>(ManagementFactory.getPlatformMBeanServer(), rules);

		final Set<JsonNode> actual = new HashSet<>();
		scraper.scrape((rule, timestamp, out) -> {
			actual.add(out);
		});

		assertEquals(1, actual.size());
	}
}
