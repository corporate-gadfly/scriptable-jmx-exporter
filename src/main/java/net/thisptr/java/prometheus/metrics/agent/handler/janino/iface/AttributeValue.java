package net.thisptr.java.prometheus.metrics.agent.handler.janino.iface;

import java.io.IOException;
import java.util.Map;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.ObjectName;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * This class represents a single MBean attribute value and its metadata.
 * 
 * <p>
 * Jackson annotations is added only to implement toString() for logging and debugging.
 * Do not expect consistent serialization/deserialization behavior.
 * JSON field names are subject to change without any notice, unlike Java field names which are part of the
 * Java scripting interface.
 * </p>
 */
public class AttributeValue {
	private static final ObjectMapper MAPPER = new ObjectMapper();

	/**
	 * {@link ObjectName#getDomain()}
	 */
	@JsonProperty("domain")
	public String domain;

	/**
	 * {@link ObjectName#getKeyPropertyList()}
	 */
	@JsonProperty("key_properties")
	public Map<String, String> keyProperties;

	/**
	 * {@link MBeanInfo#getDescription()}
	 */
	@JsonProperty("bean_description")
	public String beanDescription;

	/**
	 * {@link MBeanAttributeInfo#getName()}
	 */
	@JsonProperty("attribute_name")
	public String attributeName;

	/**
	 * {@link MBeanAttributeInfo#getDescription()}
	 */
	@JsonProperty("attribute_description")
	public String attributeDescription;

	/**
	 * Represents a class name of the attribute, equivalent to {@link MBeanAttributeInfo#getType()}. The format is same as {@link Class#getName()}.
	 */
	@JsonProperty("attribute_type")
	public String attributeType;

	/**
	 * Time in milliseconds at which the attribute value is obtained.
	 */
	@JsonProperty("timestamp")
	public long timestamp;

	public static class ToStringSerializer extends StdSerializer<Object> {
		private static final long serialVersionUID = -3355464005202101593L;

		public ToStringSerializer() {
			super(Object.class);
		}

		@Override
		public void serialize(final Object value, final JsonGenerator gen, final SerializerProvider provider) throws IOException {
			if (value == null)
				gen.writeNull();
			gen.writeString(value.toString());
		}
	}

	/**
	 * The value of the MBean attribute.
	 */
	@JsonProperty("value")
	@JsonSerialize(using = ToStringSerializer.class)
	public Object value;

	@Override
	public String toString() {
		try {
			return MAPPER.writeValueAsString(this);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e); // not expected to happen
		}
	}
}
