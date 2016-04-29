/*******************************************************************************
 * Copyright (c) 2016 comtel2000
 *
 * Licensed under the Apache License, version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.comtel2000.opcua.client.presentation.attributes;

import java.net.URL;
import java.util.ResourceBundle;

import javax.inject.Inject;

import org.comtel2000.opcua.client.presentation.binding.StatusBinding;
import org.comtel2000.opcua.client.service.OpcUaClientConnector;
import org.comtel2000.opcua.client.service.OpcUaConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.digitalpetri.opcua.stack.core.AttributeId;
import com.digitalpetri.opcua.stack.core.types.builtin.DataValue;
import com.digitalpetri.opcua.stack.core.types.builtin.Variant;
import com.digitalpetri.opcua.stack.core.types.builtin.unsigned.UInteger;
import com.digitalpetri.opcua.stack.core.types.enumerated.NodeClass;
import com.digitalpetri.opcua.stack.core.types.structured.ReferenceDescription;
import com.digitalpetri.opcua.stack.core.types.structured.WriteValue;
import com.google.common.collect.Lists;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellEditEvent;
import javafx.scene.control.TableView;

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

  private final ObjectProperty<ReferenceDescription> selectedReference =
      new SimpleObjectProperty<>();
  private final ObjectProperty<DataValue> selectedDataValue = new SimpleObjectProperty<>();

  private final static Logger logger = LoggerFactory.getLogger(AttributesViewPresenter.class);

  @Override
  public void initialize(URL url, ResourceBundle rb) {

    attribute.setCellValueFactory(param -> param.getValue().attributeProperty());
    value.setCellValueFactory(param -> param.getValue().valueProperty());
    value.setCellFactory(new AttributeItemCellFactory());

    value.setOnEditCommit((CellEditEvent<AttributeItem, String> event) -> {
      try {
        event.consume();
        final ReferenceDescription rd = selectedReference.get();
        final DataValue dv = selectedDataValue.get();
        if (dv == null || rd == null) {
          return;
        }
        Variant v = new Variant(OpcUaConverter
            .toWritableDataTypeObject(dv.getValue().getDataType().get(), event.getNewValue()));
        WriteValue wv = new WriteValue(rd.getNodeId().local().get(), AttributeId.Value.uid(), null,
            new DataValue(v));
        connection.write(wv).whenCompleteAsync((s, t) -> {
          if (t != null) {
            logger.error(t.getMessage(), t);
          } else {
            logger.info("{} write '{}' -> '{}' [{}]", rd.getBrowseName(), event.getOldValue(),
                event.getNewValue(), s);
          }
          state.statusTextProperty().set(String.format("write to %s %s", rd.getBrowseName(),
              (s.isGood() ? "succeed" : "failed: " + s)));
          updateAttributes(rd);
        } , Platform::runLater);
      } catch (Exception e) {
        logger.error(e.getMessage(), e);
      }
    });

    state.selectedTreeItemProperty().addListener((l, a, b) -> updateAttributes(b));
  }

  private void updateAttributes(ReferenceDescription b) {
    table.getItems().clear();
    selectedReference.set(b);
    selectedDataValue.set(null);
    if (b == null) {
      return;
    }
    table.getItems().add(AttributeItem.get("DisplayName", b.getDisplayName().getText()));
    table.getItems().add(AttributeItem.get("BrowseName", b.getBrowseName().toParseableString()));
    table.getItems().add(AttributeItem.get("NodeId", b.getNodeId().toParseableString()));
    table.getItems().add(AttributeItem.get("NodeClass", b.getNodeClass().toString()));
    table.getItems()
        .add(AttributeItem.get("ReferenceType", b.getReferenceTypeId().toParseableString()));
    table.getItems().add(AttributeItem.get("Forward", b.getIsForward().toString()));
    table.getItems().add(AttributeItem.get("TypeId", b.getTypeId().toParseableString()));
    table.getItems()
        .add(AttributeItem.get("TypeDefinition", b.getTypeDefinition().toParseableString()));

    if (b.getNodeId().isLocal() && b.getNodeClass() == NodeClass.Variable) {
      connection.readValues(Lists.newArrayList(b.getNodeId().local().get()))
          .whenComplete((d, t) -> {
            if (t != null) {
              logger.error(t.getMessage(), t);
              return;
            }
            if (!d.isEmpty()) {
              DataValue value = d.get(0);
              selectedDataValue.set(value);
              table.getItems().add(AttributeItem.get("Value",
                  OpcUaConverter.toString(value.getValue()), isSupported(value)));
              if (value.getValue().getDataType().isPresent()) {
                table.getItems().add(AttributeItem.get("DataType",
                    OpcUaConverter.toDataTypeString(value.getValue().getDataType().get())));
              }
              table.getItems().add(
                  AttributeItem.get("ServerTime", OpcUaConverter.toString(value.getServerTime())));
              table.getItems().add(
                  AttributeItem.get("StatusCode", OpcUaConverter.toString(value.getStatusCode())));
            }
          });
    }
  }

  private boolean isSupported(DataValue value) {
    if (!value.getValue().getDataType().isPresent()
        || !value.getValue().getDataType().isPresent()) {
      return false;
    }
    if (!(value.getValue().getDataType().get().getIdentifier() instanceof UInteger)) {
      return false;
    }
    int type = ((UInteger) value.getValue().getDataType().get().getIdentifier()).intValue();
    return type > 0 && type < 17;
  }

}
