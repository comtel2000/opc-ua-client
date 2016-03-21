package org.comtel2000.opcua.client.presentation.datatree;

import java.net.URL;
import java.util.ResourceBundle;

import javax.inject.Inject;

import org.comtel2000.opcua.client.presentation.binding.StatusBinding;
import org.comtel2000.opcua.client.service.OpcUaClientConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.digitalpetri.opcua.stack.core.types.structured.ReferenceDescription;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;

public class DataTreeViewPresenter implements Initializable {

    @Inject
    OpcUaClientConnector connection;

    @Inject
    StatusBinding state;

    @FXML
    private TreeTableView<ReferenceDescription> tableTree;

    @FXML
    private TreeTableColumn<ReferenceDescription, String> display;

    @FXML
    private TreeTableColumn<ReferenceDescription, String> browse;

    @FXML
    private TreeTableColumn<ReferenceDescription, String> node;

    private final static Logger logger = LoggerFactory.getLogger(DataTreeViewPresenter.class);

    @Override
    public void initialize(URL url, ResourceBundle rb) {

	tableTree.setRowFactory(new DataTreeNodeRowFactory<ReferenceDescription>());

	display.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().getValue().getDisplayName().getText()));

	browse.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().getValue().getBrowseName().toParseableString()));

	node.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().getValue().getNodeId() != null ? p.getValue().getValue().getNodeId().toParseableString() : ""));

	tableTree.rootProperty().bind(state.rootNodeProperty());
	tableTree.getSelectionModel().selectedItemProperty().addListener((l, a, b) -> nodeChanged(b));

	addContextMenu(rb);

    }

    private void nodeChanged(TreeItem<ReferenceDescription> item) {
	if (item != null) {
	    // check for sub node
	    item.getChildren();
	}
	state.selectedTreeItemProperty().set(item != null ? item.getValue() : null);
    }

    private void addContextMenu(ResourceBundle rb) {

	ContextMenu menu = new ContextMenu();
	MenuItem monitorItem = new MenuItem("Monitor");
	monitorItem.setOnAction(a -> {
	    TreeItem<ReferenceDescription> item = tableTree.getSelectionModel().getSelectedItem();
	    if (item != null) {
		state.subscribeTreeItemList().add(item.getValue());
	    }
	});
	MenuItem writeItem = new MenuItem("Write..");
	writeItem.setOnAction(a -> logger.error("write not yet supported"));
	menu.getItems().addAll(monitorItem, writeItem);

	monitorItem.disableProperty().bind(tableTree.getSelectionModel().selectedItemProperty().isNull());
	writeItem.disableProperty().bind(tableTree.getSelectionModel().selectedItemProperty().isNull());

	tableTree.setContextMenu(menu);
    }

}
