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
