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

import java.io.StringWriter;
import java.net.URL;
import java.util.ResourceBundle;

import javax.inject.Inject;

import org.comtel2000.opcua.client.presentation.binding.StatusBinding;
import org.comtel2000.opcua.client.service.OpcUaClientConnector;
import org.eclipse.milo.opcua.stack.core.serialization.xml.XmlEncoder;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

public class DataTreeViewPresenter implements Initializable {

  private final static Logger logger = LoggerFactory.getLogger(DataTreeViewPresenter.class);

  @Inject
  OpcUaClientConnector connection;

  @Inject
  StatusBinding state;

  @FXML
  private TreeTableView<ReferenceDescription> tableTree;

  @FXML
  private TreeTableColumn<ReferenceDescription, ReferenceDescription> display;

  @FXML
  private TreeTableColumn<ReferenceDescription, String> browse;

  @FXML
  private TreeTableColumn<ReferenceDescription, String> node;

  @FXML
  private MenuItem monitorItem;

  @FXML
  private MenuItem copyItem;

  @Override
  public void initialize(URL url, ResourceBundle rb) {

    tableTree.setRowFactory(new DataTreeNodeRowFactory<ReferenceDescription>());

    display.setCellValueFactory(p -> new ReadOnlyObjectWrapper<ReferenceDescription>(p.getValue().getValue()));


    display.setCellFactory(new DataTreeNodeCellFactory());

    browse.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().getValue().getBrowseName().toParseableString()));

    node.setCellValueFactory(
        p -> new ReadOnlyStringWrapper(p.getValue().getValue().getNodeId() != null ? p.getValue().getValue().getNodeId().toParseableString() : ""));

    tableTree.rootProperty().bind(state.rootNodeProperty());
    tableTree.getSelectionModel().selectedItemProperty().addListener((l, a, b) -> nodeChanged(b));

    bindContextMenu();

    // copy key not registered in ContenxtMenu
    registerKeys();
  }


  private void nodeChanged(TreeItem<ReferenceDescription> item) {
    if (item != null) {
      // check for sub node
      item.getChildren();
      state.showAttributeItemProperty().set(item.getValue());
    }
    state.selectedTreeItemProperty().set(item != null ? item.getValue() : null);
  }

  private void bindContextMenu() {
    monitorItem.disableProperty().bind(state.connectedProperty().not().and(tableTree.getSelectionModel().selectedItemProperty().isNull()));
    copyItem.disableProperty().bind(tableTree.getSelectionModel().selectedItemProperty().isNull());
  }

  private void registerKeys() {
    KeyCombination copyEvent = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);
    tableTree.setOnKeyPressed(e -> {
      if (copyEvent.match(e)) {
        copyValue();
      }
    });
  }

  @FXML
  void copyValue() {
    if (!tableTree.isFocused()) {
      return;
    }
    TreeItem<ReferenceDescription> item = tableTree.getSelectionModel().getSelectedItem();
    if (item != null && item.getValue() != null) {
      try {
        StringWriter writer = new StringWriter();
        XmlEncoder encoder = new XmlEncoder();
        encoder.setOutput(writer);
        writer.write("<ReferenceDescription>");
        ReferenceDescription.encode(item.getValue(), encoder);
        writer.write("</ReferenceDescription>");
        writer.flush();

        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(writer.toString());
        clipboard.setContent(content);
      } catch (Exception e) {
        logger.error(e.getMessage(), e);
      }
    }
  }

  @FXML
  void monitorItem() {
    TreeItem<ReferenceDescription> item = tableTree.getSelectionModel().getSelectedItem();
    if (item != null) {
      state.subscribeTreeItemList().add(item.getValue());
    }
  }

  @FXML
  void showAttributes() {
    TreeItem<ReferenceDescription> item = tableTree.getSelectionModel().getSelectedItem();
    if (item != null) {
      state.showAttributeItemProperty().set(item.getValue());
    }
  }
}
