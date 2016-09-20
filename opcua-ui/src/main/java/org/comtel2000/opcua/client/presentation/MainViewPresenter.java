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
package org.comtel2000.opcua.client.presentation;

import java.net.URL;
import java.util.ResourceBundle;

import javax.inject.Inject;

import org.comtel2000.opcua.client.presentation.attributes.AttributesView;
import org.comtel2000.opcua.client.presentation.binding.StatusBinding;
import org.comtel2000.opcua.client.presentation.connect.ConnectView;
import org.comtel2000.opcua.client.presentation.datatree.DataTreeView;
import org.comtel2000.opcua.client.presentation.events.EventsView;
import org.comtel2000.opcua.client.service.PersistenceService;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;

public class MainViewPresenter implements Initializable {

  @Inject
  PersistenceService session;

  @Inject
  StatusBinding state;

  @FXML
  ProgressIndicator progress;

  @FXML
  Label status;

  @FXML
  BorderPane mainPane;

  @FXML
  private SplitPane vSplitPane;

  @FXML
  private SplitPane hSplitPane;

  @Override
  public void initialize(URL location, ResourceBundle resources) {

    progress.visibleProperty().bind(state.progressVisibleProperty());
    status.textProperty().bind(state.statusTextProperty());

    ConnectView connect = new ConnectView();
    mainPane.setTop(connect.getView());

    DataTreeView treeView = new DataTreeView();
    AttributesView attr = new AttributesView();
    hSplitPane.setDividerPosition(0, 0.7);
    hSplitPane.getItems().addAll(treeView.getView(), attr.getView());

    EventsView events = new EventsView();
    vSplitPane.setDividerPosition(0, 0.8);
    vSplitPane.getItems().add(events.getView());

  }

}
