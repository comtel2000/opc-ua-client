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

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.Callback;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;

public class AttributeItemCellFactory
    implements Callback<TableColumn<AttributeItem, String>, TableCell<AttributeItem, String>> {

  private StringConverter<String> converter;

  public AttributeItemCellFactory() {
    converter = new DefaultStringConverter();
  }

  @Override
  public TableCell<AttributeItem, String> call(TableColumn<AttributeItem, String> param) {

    return new TextFieldTableCell<AttributeItem, String>(converter) {
      @Override
      public void startEdit() {
        AttributeItem model = getTableView().getSelectionModel().getSelectedItem();
        setEditable(model != null && model.isEditable());
        super.startEdit();
      }
    };
  }

}
