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

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.XmlElement;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

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
      return Arrays.stream(values()).filter(al -> (al.value & accessLevel) != 0).collect(Collectors.toCollection(() -> EnumSet.noneOf(AccessLevel.class)));
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
    if (variant.getValue() instanceof Object[]) {
      return toString((Object[]) variant.getValue());
    }
    if (variant.getValue() instanceof LocalizedText) {
      return toString((LocalizedText) variant.getValue());
    }
    return String.valueOf(variant.getValue());
  }

  /**
   * NodeType to String
   * 
   * @param node NodeIdType
   * @return String representation
   * 
   * @see: {@link Identifiers}
   */
  public static String toDataTypeString(NodeId node) {
    if (node == null || node.isNull()) {
      return null;
    }
    if (node.getIdentifier() == null && !(node.getIdentifier() instanceof UInteger)) {
      return String.format("%s (%s)", node.getIdentifier(), node.toParseableString());
    }
    int id = ((UInteger) node.getIdentifier()).intValue();
    switch (id) {
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
      case 17:
        return "NodeId";
      case 18:
        return "ExpandedNodeId";
      case 19:
        return "StatusCode";
      case 20:
        return "QualifiedName";
      case 21:
        return "LocalizedText";
      case 22:
        return "Structure";
      case 23:
        return "DataValue";
      case 24:
      case 25:
        return "BaseDataType";
      case 26:
        return "Number";
      case 27:
        return "Integer";
      case 28:
        return "UInteger";
      case 29:
        return "Enumeration";
      case 30:
        return "Image";
      default:
        return String.format("%d (%s)", id, node.toParseableString());
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
        return new DateTime(java.util.Date.from(ZonedDateTime.from(DateTimeFormatter.ISO_DATE_TIME.parse(value)).toInstant()));
      case 14:
        return UUID.fromString(value);
      case 15:
        return ByteString.of(value.getBytes());
      case 16:
        return XmlElement.of(value);
      case 17:
        return NodeId.parse(value);
      case 18:
        return ExpandedNodeId.parse(value);
      case 19:
        if (value != null && value.equalsIgnoreCase("good")) {
          return StatusCode.GOOD;
        }
        return StatusCode.BAD;
      case 20:
        return QualifiedName.parse(value);
      case 21:
        return LocalizedText.english(value);
      case 22:
      case 23:
      case 24:
      case 25:
        return value;
      case 26:
      case 27:
        return Integer.valueOf(value);
      case 28:
        return UInteger.valueOf(value);
      case 29:
      case 30:
      default:
        return value;
    }

  }

  public static ZonedDateTime toZonedDateTime(DateTime time) {
    return Instant.ofEpochMilli(time.getJavaTime()).atZone(ZoneId.systemDefault());
  }

  private static String toString(LocalizedText value) {
    return value.getText();
  }

  public static String toString(DateTime time) {
    return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(toZonedDateTime(time));
  }

  public static String toString(ByteString bs) {
    return bs.bytes() != null ? Arrays.toString(bs.bytes()) : bs.toString();
  }

  public static String toRangeString(ByteString bs) {
    if (bs == null || bs.bytes() == null || bs.bytes().length != 16) {
      return "Range [unknown]";
    }
    ByteBuf range = Unpooled.wrappedBuffer(bs.bytes()).order(Unpooled.LITTLE_ENDIAN);
    double low = range.readDouble();
    double  high = range.readDouble();
    return String.format("Range [%s, %s]", toString(low), toString(high));
  }
  
  public static String toString(double d) {
    return d % 1.0 != 0 ? String.format("%s", d) : String.format("%.0f",d);
  }
  
  public static String toString(XmlElement xml) {
    return xml.getFragment();
  }

  public static String toString(Object[] data) {
    return Arrays.toString(data);
  }

  public static String toString(ExtensionObject ext) {
    if (ext.getEncodingTypeId() != null && Identifiers.Range_Encoding_DefaultBinary.getIdentifier().equals(ext.getEncodingTypeId().getIdentifier())){
      return toRangeString((ByteString) ext.getEncoded());
    }
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
