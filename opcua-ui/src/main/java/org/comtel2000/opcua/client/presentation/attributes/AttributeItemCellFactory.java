package org.comtel2000.opcua.client.presentation.attributes;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.Callback;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;

public class AttributeItemCellFactory implements Callback<TableColumn<AttributeItem, String>, TableCell<AttributeItem, String>> {

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
