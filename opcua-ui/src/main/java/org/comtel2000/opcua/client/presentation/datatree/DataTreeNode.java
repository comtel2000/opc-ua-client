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
package org.comtel2000.opcua.client.presentation.datatree;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.comtel2000.opcua.client.service.OpcUaClientConnector;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.scene.control.TreeItem;

public class DataTreeNode extends TreeItem<ReferenceDescription> {

  protected final static Logger logger = LoggerFactory.getLogger(DataTreeNode.class);

  boolean updated;
  boolean leaf;

  final OpcUaClientConnector connection;

  private final static java.util.function.Predicate<? super ReferenceDescription> hasNotifierFilterEventSource = r -> {
    return r != null && r.getNodeId() != null && r.getNodeId().isLocal();// && !Identifiers.HasNotifier.equals(r.getReferenceTypeId()) && !Identifiers.HasEventSource.equals(r.getReferenceTypeId());
  };

  public DataTreeNode(OpcUaClientConnector c, ReferenceDescription rd) {
    super(rd);
    this.connection = c;
    leaf = isLeafType(rd);
  }

  @Override
  public boolean isLeaf() {
    return leaf;
  }

  public boolean isUpdated() {
    return updated;
  }

  public void rebrowse() {
    updated = false;
    super.getChildren().clear();
    getChildren();
    
  }

  @Override
  public ObservableList<TreeItem<ReferenceDescription>> getChildren() {
    if (updated || leaf) {
      return super.getChildren();
    }
    updated = true;
    connection.getHierarchicalReferences(getValue().getNodeId()).thenApply(r -> {
      if (r.getStatusCode().isGood() && r.getReferences() != null && r.getReferences().length > 0) {
        return toTreeItemList(r.getReferences());
      }
      if (r.getStatusCode().isBad()) {
        logger.error("getHierarchicalReferences failed with status code: {}", r.getStatusCode());
      }
      return Collections.<DataTreeNode>emptyList();
    }).whenCompleteAsync(this::updateChildren, Platform::runLater);
    return FXCollections.emptyObservableList();
  }

  private NodeId getNodeId(){
    ReferenceDescription rd = getValue();
    if (rd != null && rd.getNodeId() != null && rd.getNodeId().isLocal()){
      return rd.getNodeId().local().get();
    }
    return null;
  }
  
  private void updateChildren(List<DataTreeNode> list, Throwable t) {
    if (t != null) {
      logger.error(t.getMessage(), t);
      updated = false;
      return;
    }
    if (!super.getChildren().isEmpty()) {
      logger.error("double update detected: {}", list);
      return;
    }
    leaf = list.isEmpty();
    if (!super.getChildren().addAll(list)) {
      fireEvent(new TreeModificationEvent<ReferenceDescription>(valueChangedEvent(), DataTreeNode.this, getValue()));
    }
  }

  private static boolean isLeafType(ReferenceDescription rd) {
    if (rd.getTypeDefinition() == null || rd.getTypeDefinition().getIdentifier() == null || !rd.getNodeId().isLocal()) {
      return false;
    }
    Object identifier = rd.getTypeDefinition().getIdentifier();
    if (Identifiers.PropertyType.getIdentifier() == identifier){
      return true;
    }
    if (Identifiers.BaseDataVariableType.getIdentifier() == identifier){
      return true;
    }
    if (Identifiers.DataItemType.getIdentifier() == identifier){
      return true;
    }
    return false;
  }
  
  private List<DataTreeNode> toTreeItemList(ReferenceDescription[] refs){
    return Arrays.stream(refs).filter(hasNotifierFilterEventSource).map(this::createNode).distinct().collect(Collectors.toList());
  }
  
  private DataTreeNode createNode(ReferenceDescription ref) {
    return new DataTreeNode(connection, ref);
  }

  private void fireEvent(TreeModificationEvent<ReferenceDescription> evt) {
    Event.fireEvent(this, evt);
  }

  @Override
  public int hashCode() {
    final NodeId nodeId = getNodeId();
    int result = nodeId != null ? nodeId.hashCode() : 0;
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DataTreeNode that = (DataTreeNode) o;
    return Objects.equal(getNodeId(), that.getNodeId());
  }
  
  @Override
  public String toString() {
    return "DataTreeNode [updated=" + updated + ", leaf=" + leaf + ", children=" + super.getChildren().size() + "]";
  }
}
