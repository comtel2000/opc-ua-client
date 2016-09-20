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
package org.comtel2000.opcua.client.presentation.binding;

import org.comtel2000.opcua.client.presentation.datatree.DataTreeNode;

import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class StatusBinding {

  private final BooleanProperty progressVisible;
  private final BooleanProperty connected;
  private final StringProperty statusText;

  private final ObjectProperty<DataTreeNode> rootNode;

  private final ObjectProperty<ReferenceDescription> selectedTreeItem;

  private final ObjectProperty<ReferenceDescription> showAttributeItem;

  private final ObservableList<ReferenceDescription> subscribeTreeItem;

  public StatusBinding() {
    progressVisible = new SimpleBooleanProperty(false);
    connected = new SimpleBooleanProperty(false);
    statusText = new SimpleStringProperty("");
    rootNode = new SimpleObjectProperty<>(null);
    selectedTreeItem = new SimpleObjectProperty<>(null);
    showAttributeItem = new SimpleObjectProperty<>(null);
    subscribeTreeItem = FXCollections.observableArrayList();
  }

  public final BooleanProperty connectedProperty() {
    return connected;
  }

  public final BooleanProperty progressVisibleProperty() {
    return progressVisible;
  }

  public final StringProperty statusTextProperty() {
    return statusText;
  }

  public ObjectProperty<DataTreeNode> rootNodeProperty() {
    return rootNode;
  }

  public ObjectProperty<ReferenceDescription> selectedTreeItemProperty() {
    return selectedTreeItem;
  }

  public ObjectProperty<ReferenceDescription> showAttributeItemProperty() {
    return showAttributeItem;
  }

  public ObservableList<ReferenceDescription> subscribeTreeItemList() {
    return subscribeTreeItem;
  }
}
