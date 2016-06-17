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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;

import javafx.scene.Node;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.shape.Rectangle;
import javafx.util.Callback;

public class DataTreeNodeCellFactory implements
    Callback<TreeTableColumn<ReferenceDescription, ReferenceDescription>, TreeTableCell<ReferenceDescription, ReferenceDescription>> {

  protected final static Logger logger = LoggerFactory.getLogger(DataTreeNodeCellFactory.class);

  @Override
  public TreeTableCell<ReferenceDescription, ReferenceDescription> call(
      TreeTableColumn<ReferenceDescription, ReferenceDescription> param) {
    return new TreeTableCell<ReferenceDescription, ReferenceDescription>() {

      @Override
      protected void updateItem(ReferenceDescription item, boolean empty) {
        super.updateItem(item, empty);
        if (!empty && item != null) {
          setText(item.getDisplayName().getText());
          setGraphic(createGraphicNode(item));
        } else {
          setText(null);
          setGraphic(null);
        }
      }
    };
  }

  private Node createGraphicNode(ReferenceDescription rd) {
    Rectangle rect = new Rectangle(4, 4, 8, 8);
    rect.getStyleClass().add("tree-icon-" + rd.getNodeClass().toString().toLowerCase());
    return rect;
  }
}
