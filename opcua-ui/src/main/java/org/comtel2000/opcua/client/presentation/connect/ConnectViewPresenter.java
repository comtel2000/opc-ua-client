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
package org.comtel2000.opcua.client.presentation.connect;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import org.comtel2000.opcua.client.presentation.binding.StatusBinding;
import org.comtel2000.opcua.client.presentation.datatree.DataTreeNode;
import org.comtel2000.opcua.client.service.OpcUaClientConnector;
import org.comtel2000.opcua.client.service.PersistenceService;
import org.eclipse.milo.opcua.sdk.client.api.identity.UsernameProvider;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.structured.ApplicationDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;

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
  MenuButton menu;

  @FXML
  private MenuItem connectItem;

  @FXML
  private MenuItem disconnectItem;

  @FXML
  private RadioMenuItem securityItem;

  @FXML
  private MenuItem aboutItem;

  @FXML
  private MenuItem closeItem;

  @FXML
  ComboBox<String> address;


  @FXML
  private ToggleButton security;


  @FXML
  private HBox securityPane;

  @FXML
  Button connectButton;

  @FXML
  Button disconnectButton;

  @FXML
  private TextField user;

  @FXML
  private PasswordField password;

  @FXML
  private CheckBox anonymous;

  private ResourceBundle resource;

  protected static Executor FX_PLATFORM_EXECUTOR = Platform::runLater;

  private final StringProperty addressUrl = new SimpleStringProperty(this, "addressUrl");

  private final static Logger logger = LoggerFactory.getLogger(ConnectViewPresenter.class);

  @Override
  public void initialize(URL url, ResourceBundle rb) {
    resource = rb;
    session.bind(address.getItems(), "addressHistory");
    session.bind(addressUrl);

    session.bind(user.textProperty(), "user");
    session.bind(password.textProperty(), "password");

    user.disableProperty().bind(connectButton.disabledProperty().or(anonymous.selectedProperty()));
    password.disableProperty().bind(connectButton.disabledProperty().or(anonymous.selectedProperty()));
    anonymous.disableProperty().bind(connectButton.disabledProperty());

    anonymous.selectedProperty().addListener((l, a, b) -> connection.setIdentityProvider(b ? null : new UsernameProvider(user.getText(), password.getText())));
    anonymous.setSelected(true);
    session.bind(anonymous.selectedProperty(), "anonymous");
    if (address.getItems().isEmpty()) {
      address.getItems().addAll(urls.split(";"));
    }

    address.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ENTER) {
        connect();
      }
    });

    updateAddressHistory();

    state.connectedProperty()
        .addListener((l, a, b) -> state.statusTextProperty()
            .set(b ? String.format("connected to: [%s]", connection.getEndpointDescription().map(EndpointDescription::getServer)
                .map(ApplicationDescription::getApplicationName).map(LocalizedText::getText).orElse(addressUrl.get())) : "disconnected"));

    address.disableProperty().bind(state.connectedProperty().or(state.progressVisibleProperty()));
    connectButton.disableProperty().bind(state.connectedProperty().or(state.progressVisibleProperty()));
    disconnectButton.disableProperty().bind(state.connectedProperty().not().or(state.progressVisibleProperty()));

    connectItem.disableProperty().bind(connectButton.disabledProperty());
    disconnectItem.disableProperty().bind(disconnectButton.disabledProperty());

    connection.onConnectionChanged((b, t) -> Platform.runLater(() -> state.connectedProperty().set(b)));

    securityItem.selectedProperty().bindBidirectional(security.selectedProperty());
    securityPane.visibleProperty().bind(security.selectedProperty());
    securityPane.setPrefHeight(0);
    securityPane.visibleProperty().addListener(l -> securityPane.setPrefHeight(securityPane.isVisible() ? 50 : 0));
  }

  @FXML
  void connect() {
    state.progressVisibleProperty().set(true);
    state.rootNodeProperty().set(null);
    state.showAttributeItemProperty().set(null);
    
    addressUrl.set(address.getSelectionModel().getSelectedItem());
    logger.debug("try to open url: {}", addressUrl.get());
    connection.getEndpoints(addressUrl.get()).thenCompose(endpoints -> {
      EndpointDescription endpoint =
          connection.findLowestEndpoint(endpoints).orElseThrow(() -> new CompletionException(new Exception("no endpoint found: " + addressUrl.get())));
      return connection.connect(addressUrl.get(), endpoint);
    }).whenCompleteAsync((c, ex) -> {
      state.progressVisibleProperty().set(false);
      if (ex != null) {
        state.statusTextProperty().set(ex.getMessage());
        logger.error(ex.getMessage(), ex);
      } else {
        readHierarchy();
        updateAddressHistory();
      }
    }, FX_PLATFORM_EXECUTOR);
  }

  @FXML
  void disconnect() {
    state.progressVisibleProperty().set(true);
    Executors.newSingleThreadExecutor().execute(() -> {
      connection.disconnect().thenAcceptAsync(c -> state.progressVisibleProperty().set(false), FX_PLATFORM_EXECUTOR);
    });
  }

  @FXML
  void about() {
    Alert info = new Alert(AlertType.INFORMATION);
    info.setTitle(resource.getString("connect.about"));
    Optional<String> version = Optional.ofNullable(ConnectViewPresenter.class.getPackage().getImplementationVersion());
    info.setHeaderText(String.format(resource.getString("about.header"), version.orElse("DEV")));
    info.setContentText(String.format(resource.getString("about.text"), System.getProperty("java.runtime.version"), System.getProperty("java.vendor"),
        System.getProperty("os.name"), System.getProperty("os.arch"), System.getProperty("os.version")));
    info.show();
  }

  @FXML
  void exit() {
    disconnect();
    System.exit(0);
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
    DataTreeNode root =
        new DataTreeNode(connection, connection.getRootNode(connection.getEndpointDescription().map(EndpointDescription::getEndpointUrl).orElse("-")));
    state.rootNodeProperty().set(root);
    root.setExpanded(true);
  }

}
