package net.thisptr.jmx.exporter.agent.handler.janino;

import java.util.Map;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;

import org.codehaus.janino.ExpressionEvaluator;
import org.codehaus.janino.ScriptEvaluator;

import net.thisptr.jmx.exporter.agent.PrometheusMetric;
import net.thisptr.jmx.exporter.agent.PrometheusMetricOutput;
import net.thisptr.jmx.exporter.agent.Sample;
import net.thisptr.jmx.exporter.agent.config.Config.PrometheusScrapeRule;
import net.thisptr.jmx.exporter.agent.handler.ConditionScript;
import net.thisptr.jmx.exporter.agent.handler.ScriptEngine;
import net.thisptr.jmx.exporter.agent.handler.TransformScript;
import net.thisptr.jmx.exporter.agent.handler.janino.api.AttributeValue;
import net.thisptr.jmx.exporter.agent.handler.janino.api.MetricValue;
import net.thisptr.jmx.exporter.agent.handler.janino.api.MetricValueOutput;
import net.thisptr.jmx.exporter.agent.handler.janino.api._InternalUseDoNotImportProxyAccessor;
import net.thisptr.jmx.exporter.agent.handler.janino.api.fn.LogFunction;
import net.thisptr.jmx.exporter.agent.handler.janino.api.v1.V1;

public class JaninoScriptEngine implements ScriptEngine {

	private static final String SCRIPT_HEADER = ""
			+ "import static " + LogFunction.class.getName() + ".*" + ";";

	private static final String SCRIPT_FOOTER = ""
			+ ";";

	public interface Transformer {
		void transform(AttributeValue in, MetricValueOutput out, Map<String, String> match) throws Exception;
	}

	private static class TransformScriptImpl implements TransformScript {
		private final Transformer transformer;

		public TransformScriptImpl(final Transformer transformer) {
			this.transformer = transformer;
		}

		@Override
		public void execute(final Sample<PrometheusScrapeRule> sample, final PrometheusMetricOutput output) {
			// We copy all the fields to decouple !java scripts and the rest of the code base.
			final AttributeValue in = new AttributeValue();
			in.attributeDescription = sample.attribute.getDescription();
			in.attributeName = sample.attribute.getName();
			in.attributeType = sample.attribute.getType();
			in.beanDescription = sample.info.getDescription();
			in.beanClass = sample.info.getClassName();
			in.domain = sample.name.domain();
			in.keyProperties = sample.name.keyProperties();
			in.timestamp = sample.timestamp;
			in.value = sample.value;
			try {
				transformer.transform(in, (m) -> {
					final PrometheusMetric metric = new PrometheusMetric();
					metric.name = m.name;
					metric.labels = m.labels;
					metric.value = m.value;
					metric.timestamp = m.timestamp;
					metric.help = m.help;
					metric.type = m.type;
					metric.suffix = m.suffix;
					metric.nameWriter = _InternalUseDoNotImportProxyAccessor.getNameWriter(m);
					output.emit(metric);
				}, sample.captures);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Override
	public TransformScript compileTransformScript(final String script) throws ScriptCompileException {
		final ScriptEvaluator se = new ScriptEvaluator();
		se.setDefaultImports(new String[] {
				AttributeValue.class.getName(),
				MetricValue.class.getName(),
				MetricValueOutput.class.getName(),
				V1.class.getName(),
		});
		try {
			final Transformer compiledScript = (Transformer) se.createFastEvaluator(SCRIPT_HEADER + script + SCRIPT_FOOTER, Transformer.class, new String[] { "in", "out", "match" });
			return new TransformScriptImpl(compiledScript);
		} catch (Exception e) {
			throw new ScriptCompileException(e);
		}
	}

	public interface ConditionExpression {
		boolean evaluate(final MBeanInfo mbean, final MBeanAttributeInfo attribute);
	}

	private static class ConditionScriptImpl implements ConditionScript {
		private final ConditionExpression expr;

		public ConditionScriptImpl(final ConditionExpression expr) {
			this.expr = expr;
		}

		@Override
		public boolean evaluate(final MBeanInfo mbean, final MBeanAttributeInfo attribute) {
			return expr.evaluate(mbean, attribute);
		}
	}

	@Override
	public ConditionScript compileConditionScript(final String script) throws ScriptCompileException {
		final ExpressionEvaluator ee = new ExpressionEvaluator();
		try {
			final ConditionExpression expr = ee.createFastEvaluator(script, ConditionExpression.class, "mbeanInfo", "attributeInfo");
			return new ConditionScriptImpl(expr);
		} catch (final Exception e) {
			throw new ScriptCompileException(e);
		}
	}
}
