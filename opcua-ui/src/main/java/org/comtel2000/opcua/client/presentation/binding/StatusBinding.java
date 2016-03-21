package org.comtel2000.opcua.client.presentation.binding;

import org.comtel2000.opcua.client.presentation.datatree.DataTreeNode;

import com.digitalpetri.opcua.stack.core.types.structured.ReferenceDescription;

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

    private final ObservableList<ReferenceDescription> subscribeTreeItem;

    public StatusBinding() {
	progressVisible = new SimpleBooleanProperty(false);
	connected = new SimpleBooleanProperty(false);
	statusText = new SimpleStringProperty("");
	rootNode = new SimpleObjectProperty<>(null);
	selectedTreeItem = new SimpleObjectProperty<>(null);
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

    public ObservableList<ReferenceDescription> subscribeTreeItemList() {
	return subscribeTreeItem;
    }
}
