package net.thisptr.jmx.exporter.agent;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PrometheusMetric {

	@JsonProperty("name")
	public String name;

	@JsonProperty("labels")
	public Map<String, String> labels;

	@JsonProperty("value")
	public double value;

	@JsonProperty("timestamp")
	public long timestamp = 0;

	@JsonProperty("help")
	public String help;

	@JsonProperty("type")
	public String type;
}