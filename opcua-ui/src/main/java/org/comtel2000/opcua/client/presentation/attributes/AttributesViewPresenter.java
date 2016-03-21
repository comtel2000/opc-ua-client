package org.comtel2000.opcua.client.presentation.attributes;

import java.net.URL;
import java.util.ResourceBundle;

import javax.inject.Inject;

import org.comtel2000.opcua.client.presentation.binding.StatusBinding;
import org.comtel2000.opcua.client.service.OpcUaClientConnector;
import org.comtel2000.opcua.client.service.OpcUaConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.digitalpetri.opcua.stack.core.types.builtin.DataValue;
import com.digitalpetri.opcua.stack.core.types.enumerated.NodeClass;
import com.digitalpetri.opcua.stack.core.types.structured.ReferenceDescription;
import com.google.common.collect.Lists;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public class AttributesViewPresenter implements Initializable {

    @Inject
    OpcUaClientConnector connection;

    @Inject
    StatusBinding state;

    @FXML
    private TableView<AttributeItem> table;

    @FXML
    private TableColumn<AttributeItem, String> attribute;

    @FXML
    private TableColumn<AttributeItem, String> value;

    private final static Logger logger = LoggerFactory.getLogger(AttributesViewPresenter.class);

    @Override
    public void initialize(URL url, ResourceBundle rb) {

	attribute.setCellValueFactory(param -> param.getValue().getAttribute());
	value.setCellValueFactory(param -> param.getValue().getValue());

	state.selectedTreeItemProperty().addListener((l, a, b) -> updateTable(b));
    }

    private void updateTable(ReferenceDescription b) {
	table.getItems().clear();
	if (b == null) {
	    return;
	}
	table.getItems().add(AttributeItem.get("DisplayName", b.getDisplayName().getText()));
	table.getItems().add(AttributeItem.get("BrowseName", b.getBrowseName().toParseableString()));
	table.getItems().add(AttributeItem.get("NodeId", b.getNodeId().toParseableString()));
	table.getItems().add(AttributeItem.get("NodeClass", b.getNodeClass().toString()));
	table.getItems().add(AttributeItem.get("ReferenceType", b.getReferenceTypeId().toParseableString()));
	table.getItems().add(AttributeItem.get("Forward", b.getIsForward().toString()));
	table.getItems().add(AttributeItem.get("TypeId", b.getTypeId().toParseableString()));
	table.getItems().add(AttributeItem.get("TypeDefinition", b.getTypeDefinition().toParseableString()));

	if (b.getNodeId().isLocal() && b.getNodeClass() == NodeClass.Variable) {

	    connection.readValues(Lists.newArrayList(b.getNodeId().local().get())).whenComplete((d, t) -> {
		if (t != null) {
		    logger.error(t.getMessage(), t);
		    return;
		}
		if (!d.isEmpty()) {
		    DataValue value = d.get(0);
		    table.getItems().add(AttributeItem.get("Value", OpcUaConverter.toString(value.getValue())));
		    if (value.getValue().getDataType().isPresent()) {
			table.getItems().add(AttributeItem.get("DataType", value.getValue().getDataType().get().toParseableString()));
		    }
		    table.getItems().add(AttributeItem.get("ServerTime", OpcUaConverter.toString(value.getServerTime())));
		    table.getItems().add(AttributeItem.get("StatusCode", OpcUaConverter.toString(value.getStatusCode())));
		}
	    });
	}
    }

    private static class AttributeItem {

	final ReadOnlyStringWrapper attributeWrapper;
	final ReadOnlyStringWrapper valueWrapper;

	public static AttributeItem get(String attribute, String value) {
	    return new AttributeItem(attribute, value);
	}

	private AttributeItem(String attribute, String value) {
	    attributeWrapper = new ReadOnlyStringWrapper(attribute);
	    valueWrapper = new ReadOnlyStringWrapper(value);
	}

	ReadOnlyStringWrapper getAttribute() {
	    return attributeWrapper;
	}

	ReadOnlyStringWrapper getValue() {
	    return valueWrapper;
	}

    }
}
