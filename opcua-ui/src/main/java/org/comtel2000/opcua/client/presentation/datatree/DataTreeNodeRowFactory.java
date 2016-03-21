package org.comtel2000.opcua.client.presentation.datatree;

import java.io.ByteArrayOutputStream;
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.digitalpetri.opcua.stack.core.serialization.xml.XmlEncoder;
import com.digitalpetri.opcua.stack.core.types.enumerated.NodeClass;
import com.digitalpetri.opcua.stack.core.types.structured.ReferenceDescription;

import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.css.PseudoClass;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.transform.Transform;
import javafx.util.Callback;

public class DataTreeNodeRowFactory<T extends ReferenceDescription> implements Callback<TreeTableView<T>, TreeTableRow<T>> {

    private final static Logger logger = LoggerFactory.getLogger(DataTreeNodeRowFactory.class);

    private final static PseudoClass VARIABLE_CLASS = PseudoClass.getPseudoClass("variable");
    private final static PseudoClass OBJECT_CLASS = PseudoClass.getPseudoClass("object");

    private final Callback<TreeTableView<T>, TreeTableRow<T>> callbackFactory;
    private final ObservableSet<T> subscribedItems;

    public DataTreeNodeRowFactory(Callback<TreeTableView<T>, TreeTableRow<T>> callbackFactory) {
	this.callbackFactory = callbackFactory;
	this.subscribedItems = FXCollections.observableSet(new HashSet<T>());
    }

    public DataTreeNodeRowFactory() {
	this(null);
    }

    @Override
    public TreeTableRow<T> call(TreeTableView<T> tableView) {
	TreeTableRow<T> row = callbackFactory != null ? callbackFactory.call(tableView) : new TreeTableRow<>();
	row.itemProperty().addListener((Observable observable) -> updateRowStyle(row));
	subscribedItems.addListener((Observable observable) -> updateRowStyle(row));
	row.setOnDragDetected((event) -> {
	    if (row.getItem() != null && row.getItem().getNodeClass() == NodeClass.Variable) {
		Dragboard db = row.startDragAndDrop(TransferMode.COPY);
		ClipboardContent content = new ClipboardContent();
		SnapshotParameters snapshotParams = new SnapshotParameters();
		snapshotParams.setTransform(Transform.scale(0.8, 0.8));
		WritableImage snapshot = row.snapshot(snapshotParams, null);
		db.setDragView(snapshot, 20, 20);
		try {
		    ByteArrayOutputStream baos = new ByteArrayOutputStream();
		    XmlEncoder encoder = new XmlEncoder(baos);
		    ReferenceDescription.encode(row.getItem(), encoder);
		    content.putString(baos.toString("UTF-8"));
		} catch (Exception e) {
		    logger.error(e.getMessage(), e);
		}
		db.setContent(content);
	    }
	    event.consume();
	});
	return row;
    }

    public ObservableSet<T> getSubscribedItems() {
	return subscribedItems;
    }

    private void updateRowStyle(TreeTableRow<T> row) {
	if (row.getItem() == null) {
	    return;
	}
	switch (row.getItem().getNodeClass()) {
	case Variable:
	    row.pseudoClassStateChanged(OBJECT_CLASS, false);
	    row.pseudoClassStateChanged(VARIABLE_CLASS, true);
	    break;
	case Object:
	    row.pseudoClassStateChanged(VARIABLE_CLASS, false);
	    row.pseudoClassStateChanged(OBJECT_CLASS, true);
	    break;
	default:
	    row.pseudoClassStateChanged(VARIABLE_CLASS, false);
	    row.pseudoClassStateChanged(OBJECT_CLASS, false);
	    break;
	}

    }

}
