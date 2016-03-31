package org.comtel2000.opcua.client.service;

import static com.digitalpetri.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    private final AtomicReference<OpcUaClient> client = new AtomicReference<>();

    private final AtomicLong clientHandles = new AtomicLong();

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
	    return buildCompleteExceptionally(UaClient.class, e);
	}

	OpcUaClientConfig config = OpcUaClientConfig.builder().setApplicationName(LocalizedText.english(name)).setApplicationUri("urn:comtel:opcua:client").setEndpoint(endpoint)
		.setIdentityProvider(new AnonymousProvider()).setRequestTimeout(uint(5000)).build();

	client.set(new OpcUaClient(config));
	client.get().addFaultListener(fault -> logger.error("fault on {}", fault.getResponseHeader().getServiceResult()));
	client.get().addSessionActivityListener(this);

	return client.get().connect();
    }

    public String getUrl() {
	return urlRef.get();
    }

    public Optional<OpcUaClient> getClient() {
	return Optional.ofNullable(client.get());
    }

    public CompletableFuture<UaSubscription> subscribe(double interval, ReferenceDescription rd) throws InterruptedException, ExecutionException {
	if (client.get() == null){
	    return buildCompleteExceptionally(UaSubscription.class, new IOException("not connected"));
	}
	NodeId nodeId = rd.getNodeId().local().get();
	Optional<UaSubscription> subItem = client.get().getSubscriptionManager().getSubscriptions().stream()
		.filter(s -> s.getMonitoredItems().stream().anyMatch(m -> m.getReadValueId().getNodeId().equals(nodeId))).findFirst();
	if (subItem.isPresent()) {
	    throw new ExecutionException(new Exception("ReferenceDescription already monitored: " + rd.getBrowseName().getName()));
	}
	ReadValueId readValueId = new ReadValueId(nodeId, AttributeId.Value.uid(), null, QualifiedName.NULL_VALUE);
	UaSubscription subscription = client.get().getSubscriptionManager().createSubscription(interval).get();
	UInteger clientHandle = uint(clientHandles.getAndIncrement());
	MonitoringParameters parameters = new MonitoringParameters(clientHandle,

	interval, // sampling interval

	null, // filter, null means use default

	uint(10), // queue size

	true); // discard oldest

	MonitoredItemCreateRequest request = new MonitoredItemCreateRequest(readValueId, MonitoringMode.Reporting, parameters);
	return subscription.createMonitoredItems(TimestampsToReturn.Both, Collections.singletonList(request)).thenApply(r -> subscription);
    }

    public CompletableFuture<UaSubscription> unsubscribe(UInteger subscriptionId) {
	if (client.get() == null){
	    return buildCompleteExceptionally(UaSubscription.class, new IOException("not connected"));
	}
	logger.debug("remove subscriptionId: {}", subscriptionId);
	return client.get().getSubscriptionManager().deleteSubscription(subscriptionId);
    }

    public void unsubscribeAll() {
	if (client.get() == null){
	    return;
	}
	UnmodifiableIterator<UaSubscription> it = client.get().getSubscriptionManager().getSubscriptions().iterator();
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
	if (client.get() == null){
	    return buildCompleteExceptionally(UaClient.class, new IOException("not connected"));
	}
	return client.get().disconnect();
    }

    public CompletableFuture<Tuple2<ServerState, ZonedDateTime>> readServerStateAndTime() {
	if (client.get() == null){
	    return buildCompleteExceptionally(new CompletableFuture<Tuple2<ServerState, ZonedDateTime>>(), new IOException("not connected"));
	}
	List<NodeId> nodeIds = Lists.newArrayList(Identifiers.Server_ServerStatus_State, Identifiers.Server_ServerStatus_CurrentTime);
	return client.get().readValues(0.0, TimestampsToReturn.Both, nodeIds)
		.thenApply(values -> new Tuple2<ServerState, ZonedDateTime>(ServerState.from((Integer) values.get(0).getValue().getValue()),
			OpcUaConverter.toZonedDateTime((DateTime) values.get(1).getValue().getValue())));
    }

    public CompletableFuture<ServerState> readServerState() {
	if (client.get() == null){
	    return buildCompleteExceptionally(ServerState.class, new IOException("not connected"));
	}
	List<NodeId> nodeIds = Collections.singletonList(Identifiers.Server_ServerStatus_State);
	return client.get().readValues(0.0, TimestampsToReturn.Both, nodeIds).thenApply(values -> ServerState.from((Integer) values.get(0).getValue().getValue()));
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
	if (!node.isLocal()) {
	    CompletableFuture<BrowseResult> f = new CompletableFuture<>();
	    f.completeExceptionally(new Exception("invalid node index: " + node));
	    return f;
	}
	return getHierarchicalReferences(node.local().get());
    }

    public CompletableFuture<BrowseResult> getHierarchicalReferences(NodeId node) {
	UInteger nodeClassMask = uint(NodeClass.Object.getValue() | NodeClass.Variable.getValue());
	UInteger resultMask = uint(BrowseResultMask.All.getValue());
	BrowseDescription bd = new BrowseDescription(node, BrowseDirection.Forward, Identifiers.HierarchicalReferences, true, nodeClassMask, resultMask);
	return browse(bd);
    }

    public ReferenceDescription getRootNode(String displayName) {
	return new ReferenceDescription(Identifiers.RootFolder, Boolean.TRUE, Identifiers.RootFolder.expanded(), QualifiedName.parse("Root"), LocalizedText.english(displayName),
		NodeClass.Unspecified, ExpandedNodeId.NULL_VALUE);
    }

    public CompletableFuture<BrowseResult> browse(BrowseDescription nodeToBrowse) {
	if (client.get() == null){
	    return buildCompleteExceptionally(BrowseResult.class, new IOException("not connected"));
	}
	return client.get().browse(nodeToBrowse);
    }

    public CompletableFuture<List<DataValue>> read(NodeId node, AttributeId attr) {
	if (client.get() == null){
	    return buildCompleteExceptionally(new CompletableFuture<List<DataValue>>(), new IOException("not connected"));
	}
	return client.get().read(0.0, TimestampsToReturn.Both, Collections.singletonList(node), Collections.singletonList(attr.uid()));
    }

    public CompletableFuture<List<DataValue>> readValues(List<NodeId> nodeIds) {
	if (client.get() == null){
	    return buildCompleteExceptionally(new CompletableFuture<List<DataValue>>(), new IOException("not connected"));
	}
	return client.get().readValues(0.0, TimestampsToReturn.Both, nodeIds);
    }

    public CompletableFuture<StatusCode> write(WriteValue value) {
	if (client.get() == null){
	    return buildCompleteExceptionally(StatusCode.class, new IOException("not connected"));
	}
	return client.get().write(Collections.singletonList(value)).thenApply(WriteResponse::getResults).thenApply(d -> d[0]);
    }

    public CompletableFuture<StatusCode> writeValue(NodeId node, DataValue value) {
	if (client.get() == null){
	    return buildCompleteExceptionally(StatusCode.class, new IOException("not connected"));
	}
	return client.get().writeValues(Collections.singletonList(node), Collections.singletonList(value)).thenApply(d -> d.get(0));
    }

    @PreDestroy
    public void shutdown() {
	if (client.get() != null) {
	    try {
		client.get().disconnect().get();
	    } catch (InterruptedException | ExecutionException e) {
		logger.error(e.getMessage(), e);
	    }
	}
	Stack.releaseSharedResources(1, TimeUnit.SECONDS);
    }

    private <T> CompletableFuture<T> buildCompleteExceptionally(Class<T> cl, Throwable th){
	CompletableFuture<T> cf = new CompletableFuture<>();
	cf.completeExceptionally(th);
	return cf;
    }
    private <T> CompletableFuture<T> buildCompleteExceptionally(CompletableFuture<T> cf, Throwable th){
	cf.completeExceptionally(th);
	return cf;
    }
}
