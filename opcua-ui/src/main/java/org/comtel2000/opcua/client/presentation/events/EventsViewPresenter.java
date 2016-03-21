package org.comtel2000.opcua.client.presentation.events;

import java.net.URL;
import java.util.ResourceBundle;

import javax.inject.Inject;

import org.comtel2000.opcua.client.presentation.binding.StatusBinding;
import org.comtel2000.opcua.client.service.OpcUaClientConnector;
import org.comtel2000.opcua.client.service.OpcUaConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.digitalpetri.opcua.stack.core.types.builtin.unsigned.UInteger;
import com.digitalpetri.opcua.stack.core.types.structured.ReferenceDescription;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.effect.BlendMode;
import javafx.scene.input.TransferMode;

public class EventsViewPresenter implements Initializable {

    @Inject
    OpcUaClientConnector connection;

    @Inject
    StatusBinding state;

    private final ObservableList<MonitoredEvent> monitoredItems = FXCollections.observableArrayList();

    @FXML
    private TableView<MonitoredEvent> table;

    @FXML
    private TableColumn<MonitoredEvent, UInteger> id;

    @FXML
    private TableColumn<MonitoredEvent, String> mode;

    @FXML
    private TableColumn<MonitoredEvent, String> lasterror;

    @FXML
    private TableColumn<MonitoredEvent, String> variable;

    @FXML
    private TableColumn<MonitoredEvent, Double> samplingrate;

    @FXML
    private TableColumn<MonitoredEvent, String> value;

    @FXML
    private TableColumn<MonitoredEvent, String> quality;

    @FXML
    private TableColumn<MonitoredEvent, String> timestamp;

    private final static Logger logger = LoggerFactory.getLogger(EventsViewPresenter.class);

    @Override
    public void initialize(URL url, ResourceBundle rb) {

	id.setCellValueFactory(p -> new ReadOnlyObjectWrapper<UInteger>(p.getValue().getSubscription().getSubscriptionId()));

	mode.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().getMonitoredItem().isPresent() ? p.getValue().getMonitoredItem().get().getMonitoringMode().toString() : ""));

	variable.setCellValueFactory(p -> p.getValue().nameProperty());

	value.setCellValueFactory(p -> p.getValue().valueProperty());

	samplingrate.setCellValueFactory(p -> new ReadOnlyObjectWrapper<Double>(p.getValue().getSubscription().getRevisedPublishingInterval()));

	quality.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().getMonitoredItem().isPresent() ? OpcUaConverter.toString(p.getValue().getMonitoredItem().get().getStatusCode()) : ""));

	timestamp.setCellValueFactory(p -> p.getValue().timestampProperty());

	lasterror.setCellValueFactory(p -> p.getValue().lasterrorProperty());

	table.setItems(monitoredItems);

	table.setOnDragOver(event -> {
	    event.acceptTransferModes(TransferMode.COPY);
	    event.consume();
	});

	table.setOnDragEntered(event -> {
	    if (event.getDragboard().hasString()) {
		table.setBlendMode(BlendMode.DARKEN);
	    }
	});
	table.setOnDragExited(event -> {
	    if (event.getDragboard().hasString()) {
		table.setBlendMode(null);
	    }
	});
	table.setOnDragDropped(event -> {
	    if (event.getDragboard().hasString()) {
		table.setBlendMode(null);
		event.acceptTransferModes(TransferMode.COPY);
		state.subscribeTreeItemList().add(state.selectedTreeItemProperty().get());
		event.setDropCompleted(true);
	    }
	});

	state.subscribeTreeItemList().addListener((ListChangeListener.Change<? extends ReferenceDescription> c) -> {
	    while (c.next()) {
		if (c.wasAdded()) {
		    c.getAddedSubList().stream().forEach(this::subscribe);
		}
	    }
	});
	addContextMenu(rb);

    }

    private void subscribe(ReferenceDescription rd) {
	if (rd == null) {
	    return;
	}
	state.subscribeTreeItemList().remove(rd);
	try {
	    connection.subscribe(1000.0, rd).whenCompleteAsync((s, t) -> {
		if (t != null) {
		    logger.error(t.getMessage(), t);
		}
		if (s != null) {
		    monitoredItems.add(new MonitoredEvent(rd, s));
		}
	    } , Platform::runLater);

	} catch (Exception e) {
	    logger.error(e.getMessage(), e);
	}
    }

    private void addContextMenu(ResourceBundle rb) {

	ContextMenu menu = new ContextMenu();
	MenuItem removeItem = new MenuItem("Remove");
	removeItem.setOnAction(a -> remove(table.getSelectionModel().getSelectedItem()));
	MenuItem removeAllItem = new MenuItem("Remove All");
	removeAllItem.setOnAction(a -> removeAll());
	MenuItem writeItem = new MenuItem("Write..");
	writeItem.setOnAction(a -> logger.error("write not supported"));
	menu.getItems().addAll(writeItem, removeItem, removeAllItem);

	removeItem.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
	removeAllItem.disableProperty().bind(Bindings.isEmpty(table.getItems()));
	writeItem.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

	table.setContextMenu(menu);
    }

    private void remove(MonitoredEvent item) {
	if (item == null) {
	    return;
	}
	try {
	    connection.unsubscribe(item.getSubscription().getSubscriptionId());
	    monitoredItems.remove(item);
	} catch (Exception e) {
	    logger.error(e.getMessage(), e);
	}
    }

    private void removeAll() {
	try {
	    connection.unsubscribeAll();
	    monitoredItems.clear();
	} catch (Exception e) {
	    logger.error(e.getMessage(), e);
	}
    }

}
