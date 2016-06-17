/*******************************************************************************
 * Copyright (c) 2016 comtel2000
 *
 * Licensed under the Apache License, version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *******************************************************************************/
package org.comtel2000.opcua.client.service;

import java.sql.Date;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.XmlElement;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;

public class OpcUaConverter {

  public enum AccessLevel {

    CurrentRead(0x01),

    CurrentWrite(0x02),

    HistoryRead(0x04),

    HistoryWrite(0x08),

    SemanticChange(0x10);

    private final int value;

    AccessLevel(int value) {
      this.value = value;
    }

    public int getValue() {
      return value;
    }

    public static EnumSet<AccessLevel> fromMask(int accessLevel) {
      return Arrays.stream(values()).filter(al -> (al.value & accessLevel) != 0)
          .collect(Collectors.toCollection(() -> EnumSet.noneOf(AccessLevel.class)));
    }

    public static EnumSet<AccessLevel> fromMask(UByte accessLevel) {
      return fromMask(accessLevel.intValue());
    }
  }

  private OpcUaConverter() {}

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

  public static String toDataTypeString(NodeId node) {
    if (node == null || node.isNull()) {
      return null;
    }
    if (node.getIdentifier() == null && !(node.getIdentifier() instanceof UInteger)) {
      return node.toParseableString();
    }
    switch (((UInteger) node.getIdentifier()).intValue()) {
      case 1:
        return "Boolean";
      case 2:
        return "SByte";
      case 3:
        return "Byte";
      case 4:
        return "Int16";
      case 5:
        return "UInt16";
      case 6:
        return "Int32";
      case 7:
        return "UInt32";
      case 8:
        return "Int64";
      case 9:
        return "UInt64";
      case 10:
        return "Float";
      case 11:
        return "Double";
      case 12:
        return "String";
      case 13:
        return "DateTime";
      case 14:
        return "Guid";
      case 15:
        return "ByteString";
      case 16:
        return "XmlElement";

      default:
        return node.toParseableString();
    }

  }

  public static Object toWritableDataTypeObject(NodeId node, String value) throws Exception {
    if (node == null || node.isNull()) {
      throw new Exception("not parsable value: " + String.valueOf(value));
    }
    if (node.getIdentifier() == null && !(node.getIdentifier() instanceof UInteger)) {
      throw new Exception("indentifier missing for value: " + String.valueOf(value));
    }
    switch (((UInteger) node.getIdentifier()).intValue()) {
      case 1:
        if ("0".equals(value)) {
          return Boolean.FALSE;
        }
        if ("1".equals(value)) {
          return Boolean.TRUE;
        }
        return Boolean.valueOf(value);
      case 2:
        return Byte.parseByte(value);
      case 3:
        return Unsigned.ubyte(value);
      case 4:
        return Short.parseShort(value);
      case 5:
        return Unsigned.ushort(value);
      case 6:
        return Integer.parseInt(value);
      case 7:
        return Unsigned.uint(value);
      case 8:
        return Long.parseLong(value);
      case 9:
        return Unsigned.ulong(value);
      case 10:
        return Float.parseFloat(value);
      case 11:
        return Double.parseDouble(value);
      case 12:
        return value;
      case 13:
        return new DateTime(Date
            .from(ZonedDateTime.from(DateTimeFormatter.ISO_DATE_TIME.parse(value)).toInstant()));
      case 14:
        return UUID.fromString(value);
      case 15:
        return ByteString.of(value.getBytes());
      case 16:
        return XmlElement.of(value);

      default:
        return value;
    }

  }

  public static ZonedDateTime toZonedDateTime(DateTime time) {
    return Instant.ofEpochMilli(time.getJavaTime()).atZone(ZoneOffset.systemDefault());
  }

  public static String toString(DateTime time) {
    return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(toZonedDateTime(time));
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

  public static String toString(EnumSet<AccessLevel> al) {
    if (al == null || al.isEmpty()) {
      return null;
    }
    return al.stream().map(i -> i.toString()).collect(Collectors.joining(", "));
  }
}
