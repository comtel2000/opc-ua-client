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
package org.comtel2000.opcua.client.presentation.connect;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import org.comtel2000.opcua.client.presentation.binding.StatusBinding;
import org.comtel2000.opcua.client.presentation.datatree.DataTreeNode;
import org.comtel2000.opcua.client.service.OpcUaClientConnector;
import org.comtel2000.opcua.client.service.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.input.KeyCode;

public class ConnectViewPresenter implements Initializable {

  @Inject
  PersistenceService session;

  @Inject
  OpcUaClientConnector connection;

  @Inject
  StatusBinding state;

  @Inject
  String urls;

  @FXML
  ComboBox<String> address;

  @FXML
  Button connectButton;

  @FXML
  Button disconnectButton;

  protected static Executor FX_PLATFORM_EXECUTOR = Platform::runLater;

  private final StringProperty addressUrl = new SimpleStringProperty(this, "addressUrl");

  private final static Logger logger = LoggerFactory.getLogger(ConnectViewPresenter.class);

  @Override
  public void initialize(URL url, ResourceBundle rb) {


    session.bind(address.getItems(), "addressHistory");
    session.bind(addressUrl);

    if (address.getItems().isEmpty()) {
      address.getItems().addAll(urls.split(";"));
    }

    address.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ENTER) {
        connect();
      }
    });

    updateAddressHistory();

    state.connectedProperty().addListener((l, a, b) -> state.statusTextProperty()
        .set(b ? String.format("connected to: [%s]", addressUrl.get()) : "disconnected"));

    address.disableProperty().bind(state.connectedProperty().or(state.progressVisibleProperty()));
    connectButton.disableProperty()
        .bind(state.connectedProperty().or(state.progressVisibleProperty()));
    disconnectButton.disableProperty()
        .bind(state.connectedProperty().not().or(state.progressVisibleProperty()));

    connection
        .onConnectionChanged((b, t) -> Platform.runLater(() -> state.connectedProperty().set(b)));

  }

  @FXML
  public void connect() {
    state.progressVisibleProperty().set(true);
    state.rootNodeProperty().set(null);
    addressUrl.set(address.getSelectionModel().getSelectedItem());
    logger.debug("try to open url: {}", addressUrl.get());
    Executors.newSingleThreadExecutor().execute(() -> {
      connection.connect(addressUrl.get()).whenCompleteAsync((c, e) -> {
        state.progressVisibleProperty().set(false);
        if (e != null) {
          state.statusTextProperty().set(e.getMessage());
          logger.error(e.getMessage(), e);
        } else {
          readHierarchy();
          updateAddressHistory();
        }
      } , FX_PLATFORM_EXECUTOR);
    });
  }

  @FXML
  public void disconnect() {
    state.progressVisibleProperty().set(true);
    Executors.newSingleThreadExecutor().execute(() -> {
      connection.disconnect().thenAcceptAsync(c -> state.progressVisibleProperty().set(false),
          FX_PLATFORM_EXECUTOR);
    });
  }

  private void updateAddressHistory() {
    String adr = addressUrl.get();
    if (adr == null || adr.length() < 1) {
      return;
    }
    if (!address.getItems().contains(adr)) {
      address.getItems().add(0, adr);
      if (address.getItems().size() > 20) {
        address.getItems().remove(address.getItems().size() - 1);
      }
    }
    address.getSelectionModel().select(adr);
  }

  private void readHierarchy() {
    DataTreeNode root = new DataTreeNode(connection, connection.getRootNode(connection.getUrl()));
    state.rootNodeProperty().set(root);
    root.setExpanded(true);
  }

}
