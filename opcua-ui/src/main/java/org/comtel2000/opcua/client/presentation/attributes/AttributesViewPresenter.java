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
package org.comtel2000.opcua.client.presentation.attributes;

import java.net.URL;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.comtel2000.opcua.client.presentation.binding.StatusBinding;
import org.comtel2000.opcua.client.service.OpcUaClientConnector;
import org.comtel2000.opcua.client.service.OpcUaConverter;
import org.comtel2000.opcua.client.service.OpcUaConverter.AccessLevel;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.jooq.lambda.tuple.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

public class AttributesViewPresenter implements Initializable {

  @Inject
  OpcUaClientConnector connection;

  @Inject
  StatusBinding state;

  @FXML
  private TableView<AttributeItem> table;

  @FXML
  private TableColumn<AttributeItem, String> attribute;

  @FXML
  private TableColumn<AttributeItem, String> value;

  @FXML
  private MenuItem copyItem;

  @FXML
  private MenuItem refreshItem;

  private final ProgressIndicator progress = new ProgressIndicator(-1);

  private final Map<Object, String> customDataTypeCache = new ConcurrentHashMap<>();
  
  private final ObjectProperty<ReferenceDescription> selectedReference = new SimpleObjectProperty<>();
  private final ObjectProperty<DataValue> selectedDataValue = new SimpleObjectProperty<>();

  private final static Logger logger = LoggerFactory.getLogger(AttributesViewPresenter.class);

  @Override
  public void initialize(URL url, ResourceBundle rb) {
    progress.setVisible(false);
    table.setPlaceholder(progress);
    attribute.setCellValueFactory(param -> param.getValue().attributeProperty());
    value.setCellValueFactory(param -> param.getValue().valueProperty());
    value.setCellFactory(new AttributeItemCellFactory());

    value.setOnEditCommit(event -> {
      try {
        event.consume();
        final ReferenceDescription rd = selectedReference.get();
        final DataValue dv = selectedDataValue.get();
        if (dv == null || rd == null) {
          logger.error("nothing selected");
          return;
        }
        Variant v = new Variant(OpcUaConverter.toWritableDataTypeObject(dv.getValue().getDataType().get(), event.getNewValue()));
        DataValue value = new DataValue(v, null, null);
        connection.writeValue(OpcUaConverter.toNodeId(rd.getNodeId()), value).whenCompleteAsync((s, t) -> {
          if (t != null) {
            logger.error(t.getMessage(), t);
          } else {
            logger.info("{} write '{}' -> '{}' [{}]", rd.getBrowseName(), event.getOldValue(), event.getNewValue(), s);
          }
          state.statusTextProperty().set(String.format("write to %s %s", rd.getBrowseName(), (s.isGood() ? "succeed" : "failed: " + s)));
          updateAttributes(rd);
        }, Platform::runLater);

      } catch (Exception e) {
        logger.error(e.getMessage(), e);
      }
    });

    state.showAttributeItemProperty().addListener((l, a, b) -> updateAttributes(b));

    bindContextMenu();
  }

  private void bindContextMenu() {
    copyItem.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
    refreshItem.disableProperty().bind(selectedReference.isNull());
  }

  @FXML
  void copyValue() {
    if (!table.isFocused()) {
      return;
    }
    AttributeItem item = table.getSelectionModel().getSelectedItem();
    if (item != null && item.valueProperty().get() != null) {
      Clipboard clipboard = Clipboard.getSystemClipboard();
      ClipboardContent content = new ClipboardContent();
      content.putString(item.valueProperty().get());
      clipboard.setContent(content);
    }
  }

  @FXML
  void refresh() {
    if (!table.isFocused()) {
      return;
    }
    updateAttributes(selectedReference.get());
  }

  private void updateAttributes(final ReferenceDescription b) {
    selectedReference.set(b);
    selectedDataValue.set(null);
    table.getItems().clear();

    if (b == null) {
      return;
    }
    if (!b.getNodeId().isLocal()) {
      table.getItems().addAll(getAttributes(b));
      return;
    }
    
    final List<AttributeId> atrList;
    
    switch (b.getNodeClass()){
      case Variable:
        atrList = AttributeId.VARIABLE_NODE_ATTRIBUTES.asList();
        break;
      case Object:
        atrList = AttributeId.OBJECT_NODE_ATTRIBUTES.asList();
        break;
      case Method:
        atrList = AttributeId.METHOD_NODE_ATTRIBUTES.asList();
        break;
      case VariableType:
        atrList = AttributeId.VARIABLE_TYPE_NODE_ATTRIBUTES.asList();
        break;
      case ObjectType:
        atrList = AttributeId.OBJECT_TYPE_NODE_ATTRIBUTES.asList();
        break;
      case ReferenceType:
        atrList = AttributeId.REFERENCE_TYPE_NODE_ATTRIBUTES.asList();
        break;
      case DataType:
        atrList = AttributeId.DATA_TYPE_NODE_ATTRIBUTES.asList();
        break;
      case View:
        atrList = AttributeId.VIEW_NODE_ATTRIBUTES.asList();
        break;
      default:
        table.getItems().addAll(getAttributes(b));
        return;
    }


    progress.setVisible(true);
    connection.read(b.getNodeId().local().get(), atrList.stream().map(AttributeId::uid).collect(Collectors.toList())).thenApply(d -> {

      if (d.size() < atrList.size()) {
        throw new RuntimeException(String.format("read node %s failed (%s)", b.getNodeId().local().get(), d.get(0).getStatusCode()));
      }
      List<AttributeItem> additionals = new ArrayList<>();
      DataValue value = null;
      NodeId dataType = null;
      EnumSet<AccessLevel> level = null;
      for (int i = 0; i < d.size(); i++) {
        DataValue tmp = d.get(i);
        AttributeId aid = atrList.get(i);
        if (tmp.getStatusCode().isBad()) {
          logger.error("read attribute: {} failed {}", aid, tmp.getStatusCode());
          continue;
        }
        switch (aid) {
          case AccessLevel:
          case UserAccessLevel:
            level = OpcUaConverter.AccessLevel.fromMask((UByte) tmp.getValue().getValue());
            additionals.add(AttributeItem.get(aid.toString(), OpcUaConverter.toString(level)));
            break;
          case NodeClass:
            NodeClass nc = NodeClass.from((Integer) tmp.getValue().getValue());
            additionals.add(AttributeItem.get(aid.toString(), nc.toString()));
            break;
          case ValueRank:
            int rank = (Integer) tmp.getValue().getValue();
            additionals.add(AttributeItem.get(aid.toString(), OpcUaConverter.valueRankToString(rank)));
            break;
          case DataType:
            dataType = (NodeId) tmp.getValue().getValue();
            break;
          case Value:
            value = tmp;
            break;
          default:
            additionals.add(AttributeItem.get(aid.toString(), OpcUaConverter.toString(tmp.getValue())));
            break;
        }

      }
      if (dataType != null) {
        String type = OpcUaConverter.toString(dataType);
        if (type == null) {
          NodeId node = dataType;
          String cached = customDataTypeCache.computeIfAbsent(dataType.getIdentifier(), (v) -> {
            try {
              logger.debug("search for custom DataType: {}", node.getIdentifier());
              List<DataValue> list = connection.read(node, AttributeId.DisplayName).get();
              return !list.isEmpty() ? OpcUaConverter.toString(list.get(0).getValue()) : null;
            } catch (InterruptedException | ExecutionException e) {
              logger.error(e.getMessage(), e);
            }
            return null;
          });
          type = String.format("%s (%s)", dataType.getIdentifier(), cached);
        }
        additionals.add(AttributeItem.get("Value (DataType)", type));
      }
       
      if (value != null) {
        additionals.add(AttributeItem.get("Value", OpcUaConverter.toString(value.getValue()),
            level != null && level.contains(AccessLevel.CurrentWrite) && isSupported(value)));
        Optional.ofNullable(value.getSourceTime()).ifPresent(v -> additionals.add(AttributeItem.get("Value (SourceTime)", OpcUaConverter.toString(v))));
        Optional.ofNullable(value.getSourcePicoseconds()).ifPresent(v -> additionals.add(AttributeItem.get("Value (SourcePicoseconds)", v.toString())));
        Optional.ofNullable(value.getServerTime()).ifPresent(v -> additionals.add(AttributeItem.get("Value (ServerTime)", OpcUaConverter.toString(v))));
        Optional.ofNullable(value.getServerPicoseconds()).ifPresent(v -> additionals.add(AttributeItem.get("Value (ServerPicoseconds)", v.toString())));

      }
      return new Tuple2<>(additionals, value);
    }).whenCompleteAsync((l, th) -> {
      if (th != null) {
        state.statusTextProperty().set(th.getMessage());
        logger.error(th.getMessage(), th);
      }
      if (selectedReference.get() == b && l != null) {
        progress.setVisible(false);
        table.getItems().addAll(l.v1 != null ? l.v1 : getAttributes(b));
        selectedDataValue.set(l.v2);
      }

    }, Platform::runLater);

  }

  private List<AttributeItem> getAttributes(ReferenceDescription b) {
    final List<AttributeItem> list = new ArrayList<>();
    if (b == null) {
      return list;
    }
    list.add(AttributeItem.get("DisplayName", OpcUaConverter.toString(b.getDisplayName())));
    list.add(AttributeItem.get("BrowseName", OpcUaConverter.toString(b.getBrowseName())));
    list.add(AttributeItem.get("NodeId", OpcUaConverter.toString(b.getNodeId())));
    list.add(AttributeItem.get("NodeClass", String.valueOf(b.getNodeClass())));
    list.add(AttributeItem.get("ReferenceType", OpcUaConverter.toString(b.getReferenceTypeId())));
    list.add(AttributeItem.get("Forward", String.valueOf(b.getIsForward())));
    list.add(AttributeItem.get("TypeId", OpcUaConverter.toString(b.getTypeId())));
    list.add(AttributeItem.get("TypeDefinition", OpcUaConverter.toString(b.getTypeDefinition())));

    return list;
  }

  private boolean isSupported(DataValue value) {
    if (!value.getValue().getDataType().isPresent() || !value.getValue().getDataType().isPresent()) {
      return false;
    }
    if (!(value.getValue().getDataType().get().getIdentifier() instanceof UInteger)) {
      return false;
    }
    int type = ((UInteger) value.getValue().getDataType().get().getIdentifier()).intValue();
    return type > 0 && type < 17;
  }

}
