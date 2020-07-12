package net.thisptr.java.prometheus.metrics.agent.handler.janino;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.thisptr.java.prometheus.metrics.agent.handler.janino.DefaultTransformV1Function.Labels;

public class DefaultTransformV1FunctionTest {

	@Test
	void testLabels() throws Exception {
		final Labels labels = new Labels();
		labels.push("label", "1");
		labels.push("key", "1");
		labels.push("label", "2");
		labels.push("key", "2");
		labels.push("label", "3");
		labels.push("foo", "1");
		assertThat(labels.size()).isEqualTo(6);
		labels.pop();
		assertThat(labels.size()).isEqualTo(5);
		labels.pop();
		assertThat(labels.size()).isEqualTo(4);
		labels.push("label", "3");
		assertThat(labels.size()).isEqualTo(5);

		final List<String> actual = new ArrayList<>();
		labels.forEach((label, value) -> {
			actual.add(label + "=" + value);
		});

		assertThat(actual).containsExactly("label=1", "key=1", "label_1=2", "key_1=2", "label_2=3");
	}

}
