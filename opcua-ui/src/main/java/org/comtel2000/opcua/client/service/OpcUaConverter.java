package org.comtel2000.opcua.client.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

import com.digitalpetri.opcua.stack.core.types.builtin.ByteString;
import com.digitalpetri.opcua.stack.core.types.builtin.DateTime;
import com.digitalpetri.opcua.stack.core.types.builtin.ExpandedNodeId;
import com.digitalpetri.opcua.stack.core.types.builtin.ExtensionObject;
import com.digitalpetri.opcua.stack.core.types.builtin.NodeId;
import com.digitalpetri.opcua.stack.core.types.builtin.StatusCode;
import com.digitalpetri.opcua.stack.core.types.builtin.Variant;
import com.digitalpetri.opcua.stack.core.types.builtin.XmlElement;

public class OpcUaConverter {

    private OpcUaConverter() {
    }

    public static NodeId toNodeId(ExpandedNodeId node) {
	if (node == null || node.isNull() || !node.isLocal()) {
	    return NodeId.NULL_VALUE;
	}
	return node.local().get();
    }

    public static String toString(Variant variant) {
	if (variant == null || variant.isNull()) {
	    return null;
	}

	if (variant.getValue() instanceof String) {
	    return (String) variant.getValue();
	}
	if (variant.getValue() instanceof String[]) {
	    return Arrays.toString((String[]) variant.getValue());
	}
	if (variant.getValue() instanceof DateTime) {
	    return toString((DateTime) variant.getValue());
	}
	if (variant.getValue() instanceof ByteString) {
	    return toString((ByteString) variant.getValue());
	}
	if (variant.getValue() instanceof XmlElement) {
	    return toString((XmlElement) variant.getValue());
	}
	if (variant.getValue() instanceof ExtensionObject) {
	    return toString((ExtensionObject) variant.getValue());
	}

	return String.valueOf(variant.getValue());

    }

    public static ZonedDateTime toZonedDateTime(DateTime time) {
	return Instant.ofEpochMilli(time.getJavaTime()).atZone(ZoneOffset.systemDefault());
    }

    public static String toString(DateTime time) {
	return DateTimeFormatter.ISO_DATE_TIME.format(toZonedDateTime(time));
    }

    public static String toString(ByteString bs) {
	return bs.bytes() != null ? new String(bs.bytes()) : bs.toString();
    }

    public static String toString(XmlElement xml) {
	return xml.getFragment();
    }

    public static String toString(ExtensionObject ext) {
	if (ext.getEncoded() != null && ext.getEncoded() instanceof ByteString) {
	    return toString((ByteString) ext.getEncoded());
	}
	if (ext.getEncoded() != null && ext.getEncoded() instanceof XmlElement) {
	    return toString((XmlElement) ext.getEncoded());
	}
	return ext.toString();
    }

    public static String toString(StatusCode statusCode) {
	if (statusCode.isGood()) {
	    return "good";
	}
	if (statusCode.isBad()) {
	    return "bad";
	}
	if (statusCode.isUncertain()) {
	    return "uncertain";
	}
	return "unknown";
    }
}
