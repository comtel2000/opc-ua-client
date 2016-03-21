package org.comtel2000.opcua.client.service;

import static com.digitalpetri.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static com.google.common.collect.Lists.newArrayList;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import javax.annotation.PreDestroy;

import org.jooq.lambda.tuple.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.digitalpetri.opcua.sdk.client.OpcUaClient;
import com.digitalpetri.opcua.sdk.client.SessionActivityListener;
import com.digitalpetri.opcua.sdk.client.api.UaClient;
import com.digitalpetri.opcua.sdk.client.api.UaSession;
import com.digitalpetri.opcua.sdk.client.api.config.OpcUaClientConfig;
import com.digitalpetri.opcua.sdk.client.api.identity.AnonymousProvider;
import com.digitalpetri.opcua.sdk.client.api.subscriptions.UaSubscription;
import com.digitalpetri.opcua.stack.client.UaTcpStackClient;
import com.digitalpetri.opcua.stack.core.AttributeId;
import com.digitalpetri.opcua.stack.core.Identifiers;
import com.digitalpetri.opcua.stack.core.Stack;
import com.digitalpetri.opcua.stack.core.types.builtin.DataValue;
import com.digitalpetri.opcua.stack.core.types.builtin.DateTime;
import com.digitalpetri.opcua.stack.core.types.builtin.ExpandedNodeId;
import com.digitalpetri.opcua.stack.core.types.builtin.LocalizedText;
import com.digitalpetri.opcua.stack.core.types.builtin.NodeId;
import com.digitalpetri.opcua.stack.core.types.builtin.QualifiedName;
import com.digitalpetri.opcua.stack.core.types.builtin.StatusCode;
import com.digitalpetri.opcua.stack.core.types.builtin.unsigned.UInteger;
import com.digitalpetri.opcua.stack.core.types.enumerated.BrowseDirection;
import com.digitalpetri.opcua.stack.core.types.enumerated.BrowseResultMask;
import com.digitalpetri.opcua.stack.core.types.enumerated.MonitoringMode;
import com.digitalpetri.opcua.stack.core.types.enumerated.NodeClass;
import com.digitalpetri.opcua.stack.core.types.enumerated.ServerState;
import com.digitalpetri.opcua.stack.core.types.enumerated.TimestampsToReturn;
import com.digitalpetri.opcua.stack.core.types.structured.BrowseDescription;
import com.digitalpetri.opcua.stack.core.types.structured.BrowseResult;
import com.digitalpetri.opcua.stack.core.types.structured.EndpointDescription;
import com.digitalpetri.opcua.stack.core.types.structured.MonitoredItemCreateRequest;
import com.digitalpetri.opcua.stack.core.types.structured.MonitoringParameters;
import com.digitalpetri.opcua.stack.core.types.structured.ReadValueId;
import com.digitalpetri.opcua.stack.core.types.structured.ReferenceDescription;
import com.digitalpetri.opcua.stack.core.types.structured.WriteResponse;
import com.digitalpetri.opcua.stack.core.types.structured.WriteValue;
import com.google.common.collect.Lists;
import com.google.common.collect.UnmodifiableIterator;

public class OpcUaClientConnector implements SessionActivityListener {

    protected final static Logger logger = LoggerFactory.getLogger(OpcUaClientConnector.class);

    private final AtomicReference<String> urlRef = new AtomicReference<>();

    private final AtomicLong clientHandles = new AtomicLong();

    private OpcUaClient client;

    private final String name;

    private BiConsumer<Boolean, Throwable> listener;

    public OpcUaClientConnector() {
	this("OPC-UA Client");
    }

    public OpcUaClientConnector(String name) {
	this.name = name;
    }

    public void onConnectionChanged(BiConsumer<Boolean, Throwable> consumer) {
	this.listener = consumer;
    }

    public CompletableFuture<UaClient> connect(String url) {
	urlRef.set(url);
	EndpointDescription endpoint;
	try {
	    EndpointDescription[] endpoints = UaTcpStackClient.getEndpoints(url).get();
	    endpoint = Arrays.stream(endpoints).sorted((e1, e2) -> e2.getSecurityLevel().intValue() - e1.getSecurityLevel().intValue()).findFirst()
		    .orElseThrow(() -> new Exception("no endpoints returned"));
	} catch (Exception e) {
	    CompletableFuture<UaClient> f = new CompletableFuture<>();
	    f.completeExceptionally(e);
	    return f;
	}

	OpcUaClientConfig config = OpcUaClientConfig.builder().setApplicationName(LocalizedText.english(name)).setApplicationUri("urn:comtel:opcua:client").setEndpoint(endpoint)
		.setIdentityProvider(new AnonymousProvider()).setRequestTimeout(uint(5000)).build();

	client = new OpcUaClient(config);

	client.addFaultListener(fault -> logger.error("fault on {}", fault.getResponseHeader().getServiceResult()));
	client.addSessionActivityListener(this);
	return client.connect();
    }

    public String getUrl() {
	return urlRef.get();
    }

    public Optional<OpcUaClient> getClient() {
	return Optional.ofNullable(client);
    }

    public CompletableFuture<UaSubscription> subscribe(double interval, ReferenceDescription rd) throws InterruptedException, ExecutionException {
	NodeId nodeId = rd.getNodeId().local().get();
	Optional<UaSubscription> subItem = client.getSubscriptionManager().getSubscriptions().stream()
		.filter(s -> s.getMonitoredItems().stream().anyMatch(m -> m.getReadValueId().getNodeId().equals(nodeId))).findFirst();
	if (subItem.isPresent()) {
	    throw new ExecutionException(new Exception("ReferenceDescription already monitored: " + rd.getBrowseName().getName()));
	}
	ReadValueId readValueId = new ReadValueId(nodeId, AttributeId.Value.uid(), null, QualifiedName.NULL_VALUE);
	UaSubscription subscription = client.getSubscriptionManager().createSubscription(interval).get();
	UInteger clientHandle = uint(clientHandles.getAndIncrement());
	MonitoringParameters parameters = new MonitoringParameters(clientHandle,

	interval, // sampling interval

	null, // filter, null means use default

	uint(10), // queue size

	true); // discard oldest

	MonitoredItemCreateRequest request = new MonitoredItemCreateRequest(readValueId, MonitoringMode.Reporting, parameters);
	return subscription.createMonitoredItems(TimestampsToReturn.Both, newArrayList(request)).thenApply(r -> subscription);
    }

    public CompletableFuture<UaSubscription> unsubscribe(UInteger subscriptionId) {
	logger.debug("remove subscriptionId: {}", subscriptionId);
	return client.getSubscriptionManager().deleteSubscription(subscriptionId);
    }

    public void unsubscribeAll() {
	UnmodifiableIterator<UaSubscription> it = client.getSubscriptionManager().getSubscriptions().iterator();
	List<CompletableFuture<UaSubscription>> futures = new ArrayList<>();
	while (it.hasNext()) {
	    futures.add(unsubscribe(it.next().getSubscriptionId()));
	}
	try {
	    CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).get();
	} catch (InterruptedException | ExecutionException e) {
	    logger.error(e.getMessage(), e);
	}
    }

    public CompletableFuture<UaClient> disconnect() {
	return client.disconnect();
    }

    public CompletableFuture<Tuple2<ServerState, ZonedDateTime>> readServerStateAndTime() {
	List<NodeId> nodeIds = newArrayList(Identifiers.Server_ServerStatus_State, Identifiers.Server_ServerStatus_CurrentTime);
	return client.readValues(0.0, TimestampsToReturn.Both, nodeIds)
		.thenApply(values -> new Tuple2<ServerState, ZonedDateTime>(ServerState.from((Integer) values.get(0).getValue().getValue()),
			toZonedDateTime((DateTime) values.get(1).getValue().getValue())));
    }

    private static ZonedDateTime toZonedDateTime(DateTime time) {
	return Instant.ofEpochMilli(time.getJavaTime()).atZone(ZoneOffset.systemDefault());
    }

    public CompletableFuture<ServerState> readServerState() {
	List<NodeId> nodeIds = newArrayList(Identifiers.Server_ServerStatus_State);
	return client.readValues(0.0, TimestampsToReturn.Both, nodeIds).thenApply(values -> ServerState.from((Integer) values.get(0).getValue().getValue()));
    }

    @Override
    public void onSessionActive(UaSession session) {
	logger.info("active session id: {}", session.getSessionId());
	if (listener != null) {
	    listener.accept(Boolean.TRUE, null);
	}
    }

    @Override
    public void onSessionInactive(UaSession session) {
	logger.info("inactive session id: {}", session.getSessionId());
	if (listener != null) {
	    listener.accept(Boolean.FALSE, null);
	}
    }

    public CompletableFuture<BrowseResult> getHierarchicalReferences(ExpandedNodeId node) {
	if (!node.isLocal()){
	    CompletableFuture<BrowseResult> f = new CompletableFuture<>();
	    f.completeExceptionally(new Exception("invalid node index: " + node));
	    return f;
	}
	return getHierarchicalReferences(node.local().get());
    }
    
    public CompletableFuture<BrowseResult> getHierarchicalReferences(NodeId node) {
	UInteger nodeClassMask = UInteger.valueOf(NodeClass.Object.getValue() | NodeClass.Variable.getValue());
	UInteger resultMask = UInteger.valueOf(BrowseResultMask.All.getValue());
	BrowseDescription bd = new BrowseDescription(node, BrowseDirection.Forward, Identifiers.HierarchicalReferences, true, nodeClassMask, resultMask);
	return browse(bd);
    }

    public ReferenceDescription getRootNode(String displayName) {
	return new ReferenceDescription(Identifiers.RootFolder, Boolean.TRUE, Identifiers.RootFolder.expanded(), QualifiedName.parse("Root"), LocalizedText.english(displayName),
		NodeClass.Unspecified, ExpandedNodeId.NULL_VALUE);
    }

    public CompletableFuture<BrowseResult> browse(BrowseDescription nodeToBrowse) {
	return client.browse(nodeToBrowse);
    }

    public CompletableFuture<List<DataValue>> read(NodeId node, AttributeId attr) {
	return client.read(0.0, TimestampsToReturn.Both, Lists.newArrayList(node), Lists.newArrayList(attr.uid()));
    }

    public CompletableFuture<List<DataValue>> readValues(List<NodeId> nodeIds) {
	return client.readValues(0.0, TimestampsToReturn.Both, nodeIds);
    }

    public CompletableFuture<StatusCode> write(WriteValue value) {
	return client.write(newArrayList(value)).thenApply(WriteResponse::getResults).thenApply(d -> d[0]);
    }
    
    public CompletableFuture<StatusCode> writeValue(NodeId node, DataValue value) {
	return client.writeValues(newArrayList(node), newArrayList(value)).thenApply(d -> d.get(0));
    }
    
    @PreDestroy
    public void shutdown() {
	if (client != null) {
	    try {
		client.disconnect().get();
	    } catch (InterruptedException | ExecutionException e) {
		logger.error(e.getMessage(), e);
	    }
	}
	Stack.releaseSharedResources(1, TimeUnit.SECONDS);
    }

}
