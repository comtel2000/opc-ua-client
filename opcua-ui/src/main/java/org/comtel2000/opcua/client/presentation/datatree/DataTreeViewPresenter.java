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

import java.net.URL;
import java.util.ResourceBundle;

import javax.inject.Inject;

import org.comtel2000.opcua.client.presentation.binding.StatusBinding;
import org.comtel2000.opcua.client.service.OpcUaClientConnector;

import com.digitalpetri.opcua.stack.core.types.structured.ReferenceDescription;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;

public class DataTreeViewPresenter implements Initializable {

  @Inject
  OpcUaClientConnector connection;

  @Inject
  StatusBinding state;

  @FXML
  private TreeTableView<ReferenceDescription> tableTree;

  @FXML
  private TreeTableColumn<ReferenceDescription, String> display;

  @FXML
  private TreeTableColumn<ReferenceDescription, String> browse;

  @FXML
  private TreeTableColumn<ReferenceDescription, String> node;

  @Override
  public void initialize(URL url, ResourceBundle rb) {

    tableTree.setRowFactory(new DataTreeNodeRowFactory<ReferenceDescription>());

    display.setCellValueFactory(
        p -> new ReadOnlyStringWrapper(p.getValue().getValue().getDisplayName().getText()));

    browse.setCellValueFactory(p -> new ReadOnlyStringWrapper(
        p.getValue().getValue().getBrowseName().toParseableString()));

    node.setCellValueFactory(
        p -> new ReadOnlyStringWrapper(p.getValue().getValue().getNodeId() != null
            ? p.getValue().getValue().getNodeId().toParseableString() : ""));

    tableTree.rootProperty().bind(state.rootNodeProperty());
    tableTree.getSelectionModel().selectedItemProperty().addListener((l, a, b) -> nodeChanged(b));

    addContextMenu(rb);

  }

  private void nodeChanged(TreeItem<ReferenceDescription> item) {
    if (item != null) {
      // check for sub node
      item.getChildren();
    }
    state.selectedTreeItemProperty().set(item != null ? item.getValue() : null);
  }

  private void addContextMenu(ResourceBundle rb) {

    ContextMenu menu = new ContextMenu();
    MenuItem monitorItem = new MenuItem(rb.getString("datatree.monitor"));
    monitorItem.setOnAction(a -> {
      TreeItem<ReferenceDescription> item = tableTree.getSelectionModel().getSelectedItem();
      if (item != null) {
        state.subscribeTreeItemList().add(item.getValue());
      }
    });
    menu.getItems().addAll(monitorItem);

    monitorItem.disableProperty()
        .bind(tableTree.getSelectionModel().selectedItemProperty().isNull());

    tableTree.setContextMenu(menu);
  }

}
