package org.comtel2000.opcua.client.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.StringReader;

import javax.xml.stream.XMLStreamException;

import org.eclipse.milo.opcua.stack.core.serialization.xml.XmlDecoder;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.junit.Test;

public class OpcUaConverterTest {

  @Test
  public void xmlSerializer() throws XMLStreamException {
    String sample =
        "<ReferenceDescription><ReferenceTypeId><Identifier>ns=0;i=46</Identifier></ReferenceTypeId><IsForward>true</IsForward><NodeId><Identifier>svr=0;i=2994</Identifier></NodeId><BrowseName><NamespaceIndex>0</NamespaceIndex><Name>Auditing</Name></BrowseName><DisplayName><Locale></Locale><Text>Auditing</Text></DisplayName><NodeClass>Variable_2</NodeClass><TypeDefinition><Identifier>svr=0;i=68</Identifier></TypeDefinition></ReferenceDescription>";
    XmlDecoder decoder = new XmlDecoder(new StringReader(sample));
    // skip root element (latest snapshot of milo)
    decoder.skipElement();
    ReferenceDescription rd = ReferenceDescription.decode(decoder);

    assertNotNull(rd);
    assertNotNull(rd.getTypeId());
    assertEquals(Boolean.TRUE, rd.getIsForward());
    assertNotNull(rd.getNodeId());

    assertNotNull(rd.getBrowseName());
    assertEquals("Auditing", rd.getBrowseName().getName());
    assertNotNull(rd.getDisplayName());
    assertEquals("Auditing", rd.getDisplayName().getText());
    assertNotNull(rd.getNodeClass());
    assertEquals(NodeClass.Variable, rd.getNodeClass());

  }


  @Test
  public void toNodeId() throws XMLStreamException {
    ExpandedNodeId eni = new ExpandedNodeId(0, 0, "test", 0);
    assertEquals(new NodeId(0, 0), OpcUaConverter.toNodeId(eni));

  }
}
