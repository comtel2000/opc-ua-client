package org.comtel2000.opcua.client.presentation.datatree;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.comtel2000.opcua.client.service.OpcUaClientConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.digitalpetri.opcua.stack.core.Identifiers;
import com.digitalpetri.opcua.stack.core.types.structured.BrowseResult;
import com.digitalpetri.opcua.stack.core.types.structured.ReferenceDescription;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.TreeItem;
import javafx.scene.shape.Rectangle;

public class DataTreeNode extends TreeItem<ReferenceDescription> {

    protected final static Logger logger = LoggerFactory.getLogger(DataTreeNode.class);

    final AtomicBoolean updated = new AtomicBoolean(false);
    final AtomicBoolean leaf = new AtomicBoolean(false);

    final OpcUaClientConnector connection;

    private final static java.util.function.Predicate<? super ReferenceDescription> hasNotifierFilter = (r) -> {
	return r != null && !Identifiers.HasNotifier.equals(r.getReferenceTypeId());
    };

    public DataTreeNode(OpcUaClientConnector c, ReferenceDescription rd) {
	super(rd, createGraphic(rd));
	this.connection = c;
    }

    private static Node createGraphic(ReferenceDescription rd) {
	Rectangle rect = new Rectangle(8, 8);
	rect.getStyleClass().add("tree-icon-" + rd.getNodeClass().toString().toLowerCase());
	return rect;
    }

    @Override
    public boolean isLeaf() {
	return leaf.get();
    }

    @Override
    public ObservableList<TreeItem<ReferenceDescription>> getChildren() {
	if (updated.getAndSet(true)) {
	    return super.getChildren();
	}

	CompletableFuture<BrowseResult> result = connection.getHierarchicalReferences(getValue().getNodeId());
	result.thenApply(r -> Arrays.stream(r.getReferences()).filter(hasNotifierFilter).map(this::createNode)
		.collect(Collectors.toList())).whenCompleteAsync(this::updateChildren, Platform::runLater);
	return FXCollections.emptyObservableList();
    }

    private void updateChildren(List<DataTreeNode> list, Throwable t) {
	if (t != null) {
	    logger.error(t.getMessage(), t);
	    updated.set(false);
	    return;
	}
	if (!super.getChildren().isEmpty()) {
	    logger.error("double update detected: {}", list);
	    return;
	}
	leaf.set(list.isEmpty());
	if (!super.getChildren().addAll(list)) {
	    fireEvent(new TreeModificationEvent<ReferenceDescription>(valueChangedEvent(), DataTreeNode.this,
		    getValue()));
	}

    }

    private DataTreeNode createNode(ReferenceDescription ref) {
	return new DataTreeNode(connection, ref);
    }

    private void fireEvent(TreeModificationEvent<ReferenceDescription> evt) {
	Event.fireEvent(this, evt);
    }

    @Override
    public String toString() {
	return "DataTreeNode [updated=" + updated + ", leaf=" + leaf + ", children=" + super.getChildren().size() + "]";
    }
}
