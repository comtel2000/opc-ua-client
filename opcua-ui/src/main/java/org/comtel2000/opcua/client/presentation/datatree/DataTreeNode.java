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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.comtel2000.opcua.client.service.OpcUaClientConnector;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.scene.control.TreeItem;

public class DataTreeNode extends TreeItem<ReferenceDescription> {

  protected final static Logger logger = LoggerFactory.getLogger(DataTreeNode.class);

  boolean updated = false;
  boolean leaf = false;

  final OpcUaClientConnector connection;

  private final static java.util.function.Predicate<? super ReferenceDescription> hasNotifierFilter = (r) -> {
    return r != null && !Identifiers.HasNotifier.equals(r.getReferenceTypeId());
  };

  public DataTreeNode(OpcUaClientConnector c, ReferenceDescription rd) {
    super(rd);
    this.connection = c;
  }

  @Override
  public boolean isLeaf() {
    return leaf;
  }

  @Override
  public ObservableList<TreeItem<ReferenceDescription>> getChildren() {
    if (updated) {
      return super.getChildren();
    }
    updated = true;
    CompletableFuture<BrowseResult> result = connection.getHierarchicalReferences(getValue().getNodeId());
    result.thenApply(r -> Arrays.stream(r.getReferences()).filter(hasNotifierFilter).map(this::createNode).collect(Collectors.toList()))
        .whenCompleteAsync(this::updateChildren, Platform::runLater);
    return FXCollections.emptyObservableList();
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

  private DataTreeNode createNode(ReferenceDescription ref) {
    return new DataTreeNode(connection, ref);
  }

  private void fireEvent(TreeModificationEvent<ReferenceDescription> evt) {
    Event.fireEvent(this, evt);
  }

  @Override
  public String toString() {
    return "DataTreeNode [updated=" + updated + ", leaf=" + leaf + ", children=" + super.getChildren().size() + "]";
  }
}
