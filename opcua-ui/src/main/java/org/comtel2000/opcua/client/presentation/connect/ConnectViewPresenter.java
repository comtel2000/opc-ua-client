package org.comtel2000.opcua.client.presentation.connect;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import org.comtel2000.opcua.client.presentation.binding.StatusBinding;
import org.comtel2000.opcua.client.presentation.datatree.DataTreeNode;
import org.comtel2000.opcua.client.service.OpcUaClientConnector;
import org.comtel2000.opcua.client.service.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;

public class ConnectViewPresenter implements Initializable {

    @Inject
    PersistenceService session;

    @Inject
    OpcUaClientConnector connection;

    @Inject
    StatusBinding state;

    @FXML
    ComboBox<String> address;

    @FXML
    Button connectButton;

    @FXML
    Button disconnectButton;

    protected static Executor FX_PLATFORM_EXECUTOR = Platform::runLater;

    private final StringProperty addressUrl = new SimpleStringProperty(this, "addressUrl");

    private final static Logger logger = LoggerFactory.getLogger(ConnectViewPresenter.class);

    @Override
    public void initialize(URL url, ResourceBundle rb) {

	address.getItems().addAll("opc.tcp://opcua.demo-this.com:51210/UA/SampleServer",
		"opc.tcp://localhost:62547/Quickstarts/DataAccessServer",
		"opc.tcp://localhost:62550/Quickstarts/HistoricalAccessServer",
		"opc.tcp://localhost:62541/Quickstarts/ReferenceServer");
	session.bind(addressUrl);

	if (addressUrl.get() != null) {
	    address.getSelectionModel().select(addressUrl.get());
	    if (!addressUrl.get().equals(address.getSelectionModel().getSelectedItem())) {
		address.getItems().add(addressUrl.get());
		address.getSelectionModel().select(addressUrl.get());
	    }
	}

	state.connectedProperty().addListener((l, a, b) -> state.statusTextProperty()
		.set(b ? String.format("connected to: [%s]", addressUrl.get()) : "disconnected"));

	address.disableProperty().bind(state.connectedProperty());
	connectButton.disableProperty().bind(state.connectedProperty());
	disconnectButton.disableProperty().bind(state.connectedProperty().not());

	connection.onConnectionChanged((b, t) -> Platform.runLater(() -> state.connectedProperty().set(b)));

    }

    @FXML
    public void connect() {
	state.progressVisibleProperty().set(true);
	state.rootNodeProperty().set(null);
	addressUrl.set(address.getSelectionModel().getSelectedItem());
	connection.connect(addressUrl.get()).whenCompleteAsync((c, e) -> {
	    state.progressVisibleProperty().set(false);
	    if (e != null) {
		state.statusTextProperty().set(e.getMessage());
		logger.error(e.getMessage(), e);
	    } else {
		readHierarchy();
	    }
	} , FX_PLATFORM_EXECUTOR);

    }

    @FXML
    public void disconnect() {
	state.progressVisibleProperty().set(true);
	connection.disconnect().thenAcceptAsync(c -> state.progressVisibleProperty().set(false), FX_PLATFORM_EXECUTOR);

    }

    private void readHierarchy() {
	DataTreeNode root = new DataTreeNode(connection, connection.getRootNode(connection.getUrl()));
	state.rootNodeProperty().set(root);
	root.setExpanded(true);

    }

}
