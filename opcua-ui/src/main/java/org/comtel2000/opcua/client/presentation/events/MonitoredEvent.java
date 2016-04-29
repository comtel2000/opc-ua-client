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
package org.comtel2000.opcua.client.presentation.events;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.comtel2000.opcua.client.service.OpcUaConverter;

import com.digitalpetri.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import com.digitalpetri.opcua.sdk.client.api.subscriptions.UaSubscription;
import com.digitalpetri.opcua.stack.core.types.builtin.DataValue;
import com.digitalpetri.opcua.stack.core.types.builtin.StatusCode;
import com.digitalpetri.opcua.stack.core.types.structured.ReferenceDescription;

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.StringProperty;

public class MonitoredEvent implements Consumer<DataValue> {

  private ReadOnlyStringWrapper name;
  private ReadOnlyStringWrapper value;
  private ReadOnlyStringWrapper timestamp;
  private ReadOnlyStringWrapper lasterror;

  private final ReferenceDescription reference;
  private final UaSubscription subsciption;

  public MonitoredEvent(ReferenceDescription reference, UaSubscription subsciption) {
    this.reference = Objects.requireNonNull(reference);
    this.subsciption = Objects.requireNonNull(subsciption);
    this.subsciption.getMonitoredItems().stream().findFirst()
        .ifPresent(m -> m.setValueConsumer(this));
  }

  @Override
  public void accept(DataValue v) {
    timestampProperty().set(DateTimeFormatter.ISO_LOCAL_TIME.format(LocalTime.now()));
    valueProperty().set(OpcUaConverter.toString(v.getValue()));
    if (v.getStatusCode() != StatusCode.GOOD) {
      lasterrorProperty().set(v.getStatusCode().toString());
    }
  }

  public String getName() {
    return nameProperty().get();
  }

  public ReadOnlyStringProperty nameProperty() {
    if (name == null) {
      name = new ReadOnlyStringWrapper(reference.getDisplayName().getText());
    }
    return name.getReadOnlyProperty();
  }

  public String getValue() {
    return valueProperty().get();
  }

  public StringProperty valueProperty() {
    if (value == null) {
      value = new ReadOnlyStringWrapper();
    }
    return value;
  }

  public String getTimestamp() {
    return timestampProperty().get();
  }

  public StringProperty timestampProperty() {
    if (timestamp == null) {
      timestamp = new ReadOnlyStringWrapper();
    }
    return timestamp;
  }

  public String getLastError() {
    return lasterrorProperty().get();
  }

  public StringProperty lasterrorProperty() {
    if (lasterror == null) {
      lasterror = new ReadOnlyStringWrapper();
    }
    return lasterror;
  }

  public ReferenceDescription getReferenceDescription() {
    return reference;
  }

  public UaSubscription getSubscription() {
    return subsciption;
  }

  public Optional<UaMonitoredItem> getMonitoredItem() {
    return subsciption.getMonitoredItems().stream().findFirst();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((reference == null) ? 0 : reference.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    MonitoredEvent other = (MonitoredEvent) obj;
    if (reference == null) {
      if (other.reference != null)
        return false;
    } else if (!reference.equals(other.reference))
      return false;
    return true;
  }

}
