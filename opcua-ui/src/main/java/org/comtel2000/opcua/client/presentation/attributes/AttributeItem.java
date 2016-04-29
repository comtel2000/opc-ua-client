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

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

public class AttributeItem {

  final boolean editable;
  final ReadOnlyStringWrapper attributeWrapper;
  final ReadOnlyStringWrapper valueWrapper;

  static AttributeItem get(String attribute, String value, boolean editable) {
    return new AttributeItem(attribute, value, editable);
  }

  static AttributeItem get(String attribute, String value) {
    return new AttributeItem(attribute, value, false);
  }

  private AttributeItem(String attribute, String value, boolean editable) {
    attributeWrapper = new ReadOnlyStringWrapper(attribute);
    valueWrapper = new ReadOnlyStringWrapper(value);
    this.editable = editable;
  }

  ReadOnlyStringProperty attributeProperty() {
    return attributeWrapper.getReadOnlyProperty();
  }

  void setValue(String newValue) {
    valueWrapper.set(newValue);
  }

  ReadOnlyStringProperty valueProperty() {
    return valueWrapper.getReadOnlyProperty();
  }

  boolean isEditable() {
    return editable;
  }

}
