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
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.stream.Collectors;

import org.eclipse.milo.opcua.sdk.core.ValueRank;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.serialization.binary.BinaryDecoder;
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
import org.eclipse.milo.opcua.stack.core.types.structured.EUInformation;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointUrlListDataType;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class OpcUaConverter {

  public static ResourceBundle datatypes = ResourceBundle.getBundle("org.comtel2000.opcua.client.service.datatype");
  
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
    final Object value = variant.getValue();

    if (value instanceof byte[]) {
      return toString((byte[]) variant.getValue());
    }
    if (variant.getValue() instanceof String) {
      return (String) variant.getValue();
    }
    if (variant.getValue() instanceof NodeId) {
      return toString((NodeId) variant.getValue());
    }
    if (variant.getValue() instanceof String[]) {
      return toString((String[]) variant.getValue());
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
    if (variant.getValue() instanceof QualifiedName) {
      return toString((QualifiedName) variant.getValue());
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
   * NodeType/id to String
   * 
   * @param node NodeId type/Id
   * @return String representation
   * 
   * @see: {@link Identifiers}
   * @see: {@link NodeIdLookup}
   */
  public static String toString(NodeId node) {
    if (node == null || node.isNull()) {
      return null;
    }
    if (node.getIdentifier() == null || !(node.getIdentifier() instanceof UInteger)) {
      return String.format("%s (%s)", node.getIdentifier(), node.toParseableString());
    }
    int id = ((UInteger) node.getIdentifier()).intValue();
    String nodeName;
    try {
      nodeName = datatypes.getString(Integer.toString(id));
    } catch (Exception e) {
      return null;
    }
    return String.format("%d (%s)", id, nodeName);
  }

  public static String toString(ExpandedNodeId node) {
    if (node == null || node.isNull()) {
      return null;
    }
    if (node.isLocal()){
      return toString(node.local().get());
    }
    if (node.getIdentifier() == null && !(node.getIdentifier() instanceof UInteger)) {
      return String.format("%s (%s)", node.getIdentifier(), node.toParseableString());
    }
    int id = ((UInteger) node.getIdentifier()).intValue();
    String nodeName;
    try {
      nodeName = datatypes.getString(Integer.toString(id));
    } catch (Exception e) {
      nodeName = "Unknown";
    }
    return String.format("%d (%s)", id, nodeName);

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
        return Byte.valueOf(value);
      case 3:
        return Unsigned.ubyte(value);
      case 4:
        return Short.valueOf(value);
      case 5:
        return Unsigned.ushort(value);
      case 6:
        return Integer.valueOf(value);
      case 7:
        return Unsigned.uint(value);
      case 8:
        return Long.valueOf(value);
      case 9:
        return Unsigned.ulong(value);
      case 10:
        return Float.valueOf(value);
      case 11:
        return Double.valueOf(value);
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

  public static String toString(LocalizedText value) {
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
  
  public static String toEUInformationString(ByteString bs) {
    if (bs == null || bs.bytes() == null) {
      return "EUInformation [unknown]";
    }
    BinaryDecoder decoder = new BinaryDecoder();
    decoder.setBuffer(Unpooled.wrappedBuffer(bs.bytes()).order(Unpooled.LITTLE_ENDIAN));
    EUInformation eui = EUInformation.decode(decoder);
    return eui.toString();
  }
  
  
  public static String toString(double d) {
    return d % 1.0 != 0 ? String.format("%s", d) : String.format("%.0f",d);
  }
  
  public static String toString(QualifiedName qname) {
    return qname.toParseableString();
  }
  
  public static String toString(XmlElement xml) {
    return xml.getFragment();
  }

  public static String toString(Object[] data) {
    return Arrays.toString(data.length > 100 ? Arrays.copyOf(data, 100) : data) + (data.length > 100 ? "+" : "");
  }

  public static String toString(byte[] data) {
    return Arrays.toString(data.length > 100 ? Arrays.copyOf(data, 100) : data) + (data.length > 100 ? "+" : "");
  }
  
  public static String toString(ExtensionObject ext) {
    if (ext.getEncodingTypeId() != null && ext.getEncodingTypeId().getIdentifier() != null){
      if (Identifiers.Range_Encoding_DefaultBinary.getIdentifier().equals(ext.getEncodingTypeId().getIdentifier())){
        return toRangeString((ByteString) ext.getEncoded());
      }
      if (Identifiers.EUInformation_Encoding_DefaultBinary.getIdentifier().equals(ext.getEncodingTypeId().getIdentifier())){
        return toEUInformationString((ByteString) ext.getEncoded());
      }
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

  public static String valueRankToString(int rank) {
    Optional<ValueRank> valueRank = Arrays.stream(ValueRank.values()).filter(v -> v.getValue() == rank).findAny();
    return String.format("%d (%s)", rank, valueRank.isPresent() ? valueRank.get() : "Unknown");
  }
}
