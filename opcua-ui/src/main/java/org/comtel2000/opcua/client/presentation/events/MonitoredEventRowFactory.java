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

import javafx.beans.Observable;
import javafx.css.PseudoClass;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.util.Callback;

public class MonitoredEventRowFactory<T extends MonitoredEvent> implements Callback<TableView<T>, TableRow<T>> {


  private final static PseudoClass BAD_CLASS = PseudoClass.getPseudoClass("bad");

  private final Callback<TableView<T>, TableRow<T>> callbackFactory;

  public MonitoredEventRowFactory(Callback<TableView<T>, TableRow<T>> callbackFactory) {
    this.callbackFactory = callbackFactory;
  }

  public MonitoredEventRowFactory() {
    this(null);
  }

  @Override
  public TableRow<T> call(TableView<T> tableView) {
    TableRow<T> row = callbackFactory != null ? callbackFactory.call(tableView) : new TableRow<>();
    row.itemProperty().addListener((Observable observable) -> updateRowStyle(row));
    return row;
  }

  private void updateRowStyle(TableRow<T> row) {
    if (row.getItem() == null) {
      return;
    }
    row.pseudoClassStateChanged(BAD_CLASS, row.getItem().getLastError() != null);
    row.getItem().lasterrorProperty().addListener(l -> row.pseudoClassStateChanged(BAD_CLASS, row.getItem().getLastError() != null));
  }

}
