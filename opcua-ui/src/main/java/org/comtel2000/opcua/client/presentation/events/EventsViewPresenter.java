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
package org.comtel2000.opcua.client.presentation.events;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.xml.stream.XMLStreamException;

import org.comtel2000.opcua.client.presentation.binding.StatusBinding;
import org.comtel2000.opcua.client.service.OpcUaClientConnector;
import org.comtel2000.opcua.client.service.OpcUaConverter;
import org.eclipse.milo.opcua.stack.core.serialization.xml.XmlDecoder;
import org.eclipse.milo.opcua.stack.core.serialization.xml.XmlEncoder;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.effect.BlendMode;
import javafx.scene.input.TransferMode;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;

public class EventsViewPresenter implements Initializable {

  private final static Logger logger = LoggerFactory.getLogger(EventsViewPresenter.class);
  
  @Inject
  OpcUaClientConnector connection;

  @Inject
  StatusBinding state;

  private final ObservableList<MonitoredEvent> monitoredItems = FXCollections.observableArrayList();

  @FXML
  private TableView<MonitoredEvent> table;

  @FXML
  private TableColumn<MonitoredEvent, String> id;

  @FXML
  private TableColumn<MonitoredEvent, String> mode;

  @FXML
  private TableColumn<MonitoredEvent, String> lasterror;

  @FXML
  private TableColumn<MonitoredEvent, String> variable;

  @FXML
  private TableColumn<MonitoredEvent, Double> samplingrate;

  @FXML
  private TableColumn<MonitoredEvent, String> value;

  @FXML
  private TableColumn<MonitoredEvent, String> quality;

  @FXML
  private TableColumn<MonitoredEvent, String> timestamp;

  @FXML
  private MenuItem showItem;

  @FXML
  private MenuItem removeItem;

  @FXML
  private MenuItem removeAllItem;

  @FXML
  private MenuItem exportItem;

  @FXML
  private MenuItem importItem;

  ResourceBundle rb;

  @Override
  public void initialize(URL url, ResourceBundle rb) {
    this.rb = rb;
    id.setCellValueFactory(p -> new ReadOnlyStringWrapper(
        String.format("%s (%s)", p.getValue().getSubscription().getSubscriptionId(),
            p.getValue().getMonitoredItem().getMonitoredItemId())));

    mode.setCellValueFactory(p -> new ReadOnlyStringWrapper(
        p.getValue().getMonitoredItem().getMonitoringMode().toString()));

    variable.setCellValueFactory(p -> p.getValue().nameProperty());

    value.setCellValueFactory(p -> p.getValue().valueProperty());

    samplingrate.setCellValueFactory(p -> new ReadOnlyObjectWrapper<Double>(
        p.getValue().getSubscription().getRevisedPublishingInterval()));

    quality.setCellValueFactory(p -> new ReadOnlyStringWrapper(
        OpcUaConverter.toString(p.getValue().getMonitoredItem().getStatusCode())));

    timestamp.setCellValueFactory(p -> p.getValue().timestampProperty());

    lasterror.setCellValueFactory(p -> p.getValue().lasterrorProperty());

    table.setItems(monitoredItems);

    table.setOnDragOver(event -> {
      event.acceptTransferModes(TransferMode.COPY);
      event.consume();
    });

    table.setOnDragEntered(event -> {
      if (event.getDragboard().hasString()) {
        table.setBlendMode(BlendMode.DARKEN);
      }
    });
    table.setOnDragExited(event -> {
      if (event.getDragboard().hasString()) {
        table.setBlendMode(null);
      }
    });
    table.setOnDragDropped(event -> {
      if (event.getDragboard().hasString()) {
        table.setBlendMode(null);
        event.acceptTransferModes(TransferMode.COPY);
        state.subscribeTreeItemList().add(state.selectedTreeItemProperty().get());
        event.setDropCompleted(true);
      }
    });

    table.setOnMousePressed(event -> {
      if (event.isPrimaryButtonDown() && event.getClickCount() == 2) {
        MonitoredEvent item = table.getSelectionModel().getSelectedItem();
        if (item != null) {
          state.showAttributeItemProperty().set(item.getReferenceDescription());
        }
      }

    });
    state.subscribeTreeItemList()
        .addListener((ListChangeListener.Change<? extends ReferenceDescription> c) -> {
          while (c.next()) {
            if (c.wasAdded()) {
              c.getAddedSubList().stream().forEach(this::subscribe);
            }
          }
        });
    bindContextMenu();

    state.connectedProperty().addListener((l, a, b) -> {
      if (b && !monitoredItems.isEmpty()) {
        List<ReferenceDescription> items = monitoredItems.stream()
            .map(MonitoredEvent::getReferenceDescription).collect(Collectors.toList());
        monitoredItems.clear();
        subscribe(items);
      }
    });
  }

  private void subscribe(ReferenceDescription rd) {
    if (rd == null) {
      return;
    }
    state.subscribeTreeItemList().remove(rd);
    try {
      connection.subscribe(rd).whenCompleteAsync((s, t) -> {
        if (t != null) {
          logger.error(t.getMessage(), t);
        }
        if (s != null) {
          monitoredItems.add(new MonitoredEvent(rd, s.v1, s.v2));
        }
      }, Platform::runLater);

    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }
  }

  private void subscribe(List<ReferenceDescription> references) {
    if (references == null || references.isEmpty()) {
      return;
    }
    if (!state.connectedProperty().get()) {
      state.statusTextProperty().set("subscribe failed: not connected");
      return;
    }
    state.subscribeTreeItemList().removeAll(references);

    try {
      connection.subscribe(references).whenCompleteAsync((s, t) -> {
        if (t != null) {
          logger.error(t.getMessage(), t);
        }
        if (s != null && s.v2.size() == references.size()) {
          for (int i = 0; i < references.size(); i++) {
            monitoredItems.add(new MonitoredEvent(references.get(i), s.v1, s.v2.get(i)));
          }

        }
      }, Platform::runLater);

    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }
  }

  private void bindContextMenu() {

    showItem.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
    removeItem.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
    removeAllItem.disableProperty().bind(Bindings.isEmpty(table.getItems()));
    exportItem.disableProperty().bind(Bindings.isEmpty(table.getItems()));
    importItem.disableProperty().bind(state.connectedProperty().not());

  }


  @FXML
  void exportFile() {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle(rb.getString("events.export.title"));
    fileChooser.setInitialDirectory(Paths.get(System.getProperty("user.home")).toFile());
    fileChooser.getExtensionFilters().addAll(new ExtensionFilter("XML Files", "*.xml"));
    fileChooser.setInitialFileName("subscriptions.xml");
    File file = fileChooser.showSaveDialog(table.getScene().getWindow());
    if (file != null) {
      exportItems(file);
    }
  }

  @FXML
  void importFile() {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle(rb.getString("events.import.title"));
    fileChooser.setInitialDirectory(Paths.get(System.getProperty("user.home")).toFile());
    fileChooser.getExtensionFilters().addAll(new ExtensionFilter("XML Files", "*.xml"));

    File file = fileChooser.showOpenDialog(table.getScene().getWindow());
    if (file != null) {
      importItems(file);
    }
  }

  @FXML
  void showAttributes() {
    MonitoredEvent item = table.getSelectionModel().getSelectedItem();
    if (item == null) {
      return;
    }
    state.showAttributeItemProperty().set(item.getReferenceDescription());
  }

  @FXML
  void removeAllItems() {
    if (!table.isFocused()){
      return;
    }
    connection.unsubscribeAll().whenCompleteAsync((s, t) -> monitoredItems.clear(),
        Platform::runLater);
  }

  @FXML
  void removeItem() {
    if (!table.isFocused()){
      return;
    }
    MonitoredEvent item = table.getSelectionModel().getSelectedItem();
    if (item == null) {
      return;
    }
    connection.unsubscribe(item.getSubscription().getSubscriptionId(), item.getMonitoredItem())
        .whenCompleteAsync((s, t) -> monitoredItems.remove(item), Platform::runLater);
  }


  private void exportItems(File file) {

    try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING)) {
      XmlEncoder encoder = new XmlEncoder();
      encoder.setOutput(writer);
      monitoredItems.stream().map(MonitoredEvent::getReferenceDescription).forEach(rd -> {
        try {
          writer.write("<ReferenceDescription>");
          ReferenceDescription.encode(rd, encoder);
          writer.write("</ReferenceDescription>");
          writer.write(System.lineSeparator());
        } catch (IOException e) {
          logger.error(e.getMessage(), e);
        }
      });
      writer.flush();
    } catch (IOException | XMLStreamException e) {
      logger.error(e.getMessage(), e);
    }
  }

  private void importItems(File file) {

    List<ReferenceDescription> references = new ArrayList<>();
    try (BufferedReader reader = Files.newBufferedReader(file.toPath())) {
      reader.lines().filter(l -> l.startsWith("<ReferenceDescription>")).map(StringReader::new)
          .forEach(r -> {
            try {
              XmlDecoder decoder = new XmlDecoder(r);
              decoder.skipElement();
              references.add(ReferenceDescription.decode(decoder));
            } catch (Exception e) {
              logger.error(e.getMessage(), e);
            }
          });
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
    }

    subscribe(references);
  }
}
