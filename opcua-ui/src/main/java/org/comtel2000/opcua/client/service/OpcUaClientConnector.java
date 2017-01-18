/*******************************************************************************
 * Copyright (c) 2016 comtel2000
 *
 * Licensed under the Apache License, version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *******************************************************************************/
package org.comtel2000.opcua.client.service;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import javax.annotation.PreDestroy;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.api.UaClient;
import org.eclipse.milo.opcua.sdk.client.api.UaSession;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.identity.AnonymousProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.CompositeProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.IdentityProvider;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.client.UaTcpStackClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.Stack;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseResultMask;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.ServerState;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.DeleteMonitoredItemsResponse;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoredItemCreateRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoringParameters;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteResponse;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;
import org.jooq.lambda.tuple.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.UnmodifiableIterator;

public class OpcUaClientConnector implements SessionActivityListener {

  protected final static Logger logger = LoggerFactory.getLogger(OpcUaClientConnector.class);

  private final double DEFAULT_PUBLISH_INTERVAL = 500.0;

  private final AtomicReference<EndpointDescription> endpointDescription = new AtomicReference<>();

  private final AtomicReference<IdentityProvider> identityProvider = new AtomicReference<>();

  private final AtomicReference<OpcUaClient> client = new AtomicReference<>();

  private final AtomicLong clientHandles = new AtomicLong();

  private final AtomicReference<BiConsumer<Boolean, Throwable>> listener = new AtomicReference<>();

  private final String name;

  private final Executor pool;

  public OpcUaClientConnector() {
    this("OPC-UA Client");
  }

  public OpcUaClientConnector(String name) {
    this.name = name;
    this.pool = Executors.newCachedThreadPool(r -> {
      Thread th = new Thread(r);
      th.setName("client-connector-" + th.getId());
      th.setDaemon(true);
      return th;
    });
  }

  private CompletableFuture<OpcUaClient> newClient(OpcUaClientConfig config) {
    return CompletableFuture.supplyAsync(() -> {
      OpcUaClient c = new OpcUaClient(config);
      c.addFaultListener(fault -> logger.error("fault on {}", fault.getResponseHeader().getServiceResult()));
      c.addSessionActivityListener(this);
      client.set(c);
      return c;
    }, pool);
  }

  public CompletableFuture<OpcUaClient> getClient() {
    return CompletableFuture.supplyAsync(() -> {
      OpcUaClient c = client.get();
      if (c == null) {
        throw new CompletionException(new IOException("not connected"));
      }
      return c;
    }, pool);
  }

  public void onConnectionChanged(BiConsumer<Boolean, Throwable> c) {
    this.listener.set(c);
  }

  public CompletableFuture<EndpointDescription[]> getEndpoints(String url) {
    return CompletableFuture.supplyAsync(() -> {
      logger.debug("search for endpoints of url: {}", url);
      return null;
    }, pool).thenCompose(t -> UaTcpStackClient.getEndpoints(url));
  }

  public CompletableFuture<UaClient> connect(String url, EndpointDescription endpoint) {
    clientHandles.set(0);
    endpointDescription.set(endpoint);

    logger.debug("use endpoint: {} [{}]", endpointDescription.get().getEndpointUrl(), endpointDescription.get().getSecurityMode());

    if (!url.equals(endpointDescription.get().getEndpointUrl())) {
      logger.warn("fix search (returned) endpoint url missmatch: {} ({})", url, endpointDescription.get().getEndpointUrl());
      endpointDescription.set(changeEndpointUrl(endpointDescription.get(), url));
    }
    List<IdentityProvider> idProv = new ArrayList<>();
    getIdentityProvider().ifPresent(idProv::add);
    idProv.add(new AnonymousProvider());

    OpcUaClientConfig config = OpcUaClientConfig.builder().setApplicationName(LocalizedText.english(name)).setApplicationUri("urn:comtel:opcua:client")
        .setEndpoint(endpointDescription.get()).setIdentityProvider(new CompositeProvider(idProv)).setRequestTimeout(uint(5000))
        .build();

    return newClient(config).thenCompose(c -> c.connect());
  }

  public Optional<EndpointDescription> findLowestEndpoint(EndpointDescription[] endpoints) {
    return Arrays.stream(endpoints).sorted((e1, e2) -> e1.getSecurityLevel().intValue() - e2.getSecurityLevel().intValue()).findFirst();
  }

  public void setIdentityProvider(IdentityProvider provider) {
    identityProvider.set(provider);
  }

  public Optional<IdentityProvider> getIdentityProvider() {
    return Optional.ofNullable(identityProvider.get());
  }

  public Optional<EndpointDescription> getEndpointDescription() {
    return Optional.ofNullable(endpointDescription.get());
  }

  public CompletableFuture<UaSubscription> modify(UaSubscription subscription, double publishingInterval, int lifetimeCount, int maxKeepAliveCount,
      int maxNotifications, byte prio) throws InterruptedException, ExecutionException {
    return getClient().thenCompose(c -> c.getSubscriptionManager().modifySubscription(subscription.getSubscriptionId(), publishingInterval, uint(lifetimeCount),
        uint(maxKeepAliveCount), uint(maxNotifications), UByte.valueOf(prio)));
  }

  public CompletableFuture<Tuple2<UaSubscription, UaMonitoredItem>> subscribe(NodeId node) {
    return subscribe(Collections.singletonList(node)).thenApply(t -> new Tuple2<>(t.v1, t.v2.get(0)));
  }

  public CompletableFuture<Tuple2<UaSubscription, UaMonitoredItem>> subscribe(NodeId node, double publishInterval) {
    return subscribe(Collections.singletonList(node), publishInterval).thenApply(t -> new Tuple2<>(t.v1, t.v2.get(0)));
  }

  public CompletableFuture<Tuple2<UaSubscription, List<UaMonitoredItem>>> subscribe(List<NodeId> nodes) {
    return subscribe(nodes, DEFAULT_PUBLISH_INTERVAL);
  }

  public CompletableFuture<Tuple2<UaSubscription, List<UaMonitoredItem>>> subscribe(List<NodeId> nodes, double publishInterval) {

    return getClient().thenApply(c -> {
      UaSubscription subscription =
          c.getSubscriptionManager().getSubscriptions().stream().filter(s -> s.getRevisedPublishingInterval() == publishInterval).findFirst().orElseGet(() -> {
            try {
              return c.getSubscriptionManager().createSubscription(publishInterval).get();
            } catch (InterruptedException | ExecutionException e) {
              logger.error(e.getMessage(), e);
              disconnect();
            }
            return null;
          });

      List<MonitoredItemCreateRequest> list = nodes.stream()
          .map(node -> new ReadValueId(node, AttributeId.Value.uid(), null, QualifiedName.NULL_VALUE)).map(v -> new MonitoredItemCreateRequest(v,
              MonitoringMode.Reporting, new MonitoringParameters(uint(clientHandles.getAndIncrement()), publishInterval, null, uint(1), true)))
          .collect(Collectors.toList());
      List<UaMonitoredItem> items;
      try {
        items = subscription.createMonitoredItems(TimestampsToReturn.Both, list).get();
      } catch (InterruptedException | ExecutionException e) {
        throw new CompletionException(e);
      }
      return new Tuple2<>(subscription, items);
    });
  }

  public CompletableFuture<UaSubscription> unsubscribe(UaSubscription subscription) {

    return getClient().thenCompose(c -> {
      logger.debug("remove add MonitoredItem from subscriptionId: {}", subscription.getSubscriptionId());
      // c.deleteMonitoredItems(subscription.getSubscriptionId(),
      // subscription.getMonitoredItems().stream().map(UaMonitoredItem::getMonitoredItemId).collect(Collectors.toList()));
      return c.getSubscriptionManager().deleteSubscription(subscription.getSubscriptionId());

    });
  }

  public CompletableFuture<DeleteMonitoredItemsResponse> unsubscribe(UInteger subscriptionId, UaMonitoredItem item) {
    logger.debug("remove MonitoredItemId: {}", item.getMonitoredItemId());
    return getClient().thenCompose(c -> c.deleteMonitoredItems(subscriptionId, Collections.singletonList(item.getMonitoredItemId())));
  }

  public CompletableFuture<Void> unsubscribeAll() {
    OpcUaClient c = client.get();
    if (c == null) {
      return buildCompleteExceptionally(Void.class, new IOException("not connected"));
    }

    UnmodifiableIterator<UaSubscription> it = c.getSubscriptionManager().getSubscriptions().iterator();
    List<CompletableFuture<UaSubscription>> futures = new ArrayList<>();
    while (it.hasNext()) {
      futures.add(unsubscribe(it.next()));
    }

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

  }

  public CompletableFuture<UaClient> disconnect() {
    return getClient().thenCompose(c -> c.disconnect());
  }

  public CompletableFuture<Tuple2<ServerState, ZonedDateTime>> readServerStateAndTime() {
    List<NodeId> nodeIds = Lists.newArrayList(Identifiers.Server_ServerStatus_State, Identifiers.Server_ServerStatus_CurrentTime);
    return getClient().thenCompose(c -> c.readValues(0.0, TimestampsToReturn.Both, nodeIds))
        .thenApply(values -> new Tuple2<ServerState, ZonedDateTime>(ServerState.from((Integer) values.get(0).getValue().getValue()),
            OpcUaConverter.toZonedDateTime((DateTime) values.get(1).getValue().getValue())));
  }

  public CompletableFuture<ServerState> readServerState() {
    List<NodeId> nodeIds = Collections.singletonList(Identifiers.Server_ServerStatus_State);
    return getClient().thenCompose(c -> c.readValues(0.0, TimestampsToReturn.Both, nodeIds))
        .thenApply(values -> ServerState.from((Integer) values.get(0).getValue().getValue()));
  }

  @Override
  public void onSessionActive(UaSession session) {
    logger.info("active session id: {}", session.getSessionId());
    BiConsumer<Boolean, Throwable> consumer = listener.get();
    if (consumer != null) {
      consumer.accept(Boolean.TRUE, null);
    }
  }

  @Override
  public void onSessionInactive(UaSession session) {
    logger.info("inactive session id: {}", session.getSessionId());
    BiConsumer<Boolean, Throwable> consumer = listener.get();
    if (consumer != null) {
      consumer.accept(Boolean.FALSE, null);
    }
  }

  public CompletableFuture<BrowseResult> getHierarchicalReferences(ExpandedNodeId node) {
    if (!node.isLocal()) {
      return buildCompleteExceptionally(BrowseResult.class, new Exception("invalid node index: " + node));
    }
    return getHierarchicalReferences(node.local().get());
  }

  public CompletableFuture<BrowseResult> getHierarchicalReferences(NodeId node) {
    UInteger nodeClassMask = uint(NodeClass.Object.getValue() | NodeClass.Variable.getValue() | NodeClass.Method.getValue() | NodeClass.DataType.getValue());
    return getHierarchicalReferences(node, nodeClassMask);
  }

  public CompletableFuture<BrowseResult> getHierarchicalReferences(NodeId node, UInteger nodeClassMask) {
    UInteger resultMask = uint(BrowseResultMask.All.getValue());
    BrowseDescription bd = new BrowseDescription(node, BrowseDirection.Forward, Identifiers.References, true, nodeClassMask, resultMask);
    return browse(bd);
  }
  
  public ReferenceDescription getRootNode(String displayName) {
    return new ReferenceDescription(Identifiers.RootFolder, Boolean.TRUE, Identifiers.RootFolder.expanded(), QualifiedName.parse("Root"),
        LocalizedText.english(displayName), NodeClass.Unspecified, ExpandedNodeId.NULL_VALUE);
  }

  public CompletableFuture<BrowseResult> browse(BrowseDescription nodeToBrowse) {
    return getClient().thenCompose(c -> c.browse(nodeToBrowse));
  }

  public CompletableFuture<List<DataValue>> read(NodeId node, AttributeId attr) {
    return getClient().thenCompose(c -> c.read(0.0, TimestampsToReturn.Both, Collections.singletonList(node), Collections.singletonList(attr.uid())));
  }

  public CompletableFuture<List<DataValue>> read(NodeId node, List<UInteger> attr) {
    List<NodeId> nodes = attr.stream().map(a -> node).collect(Collectors.toList());
    return getClient().thenCompose(c -> c.read(0.0, TimestampsToReturn.Both, nodes, attr));
  }

  public CompletableFuture<List<DataValue>> readValues(List<NodeId> nodeIds) {
    return getClient().thenCompose(c -> c.readValues(0.0, TimestampsToReturn.Both, nodeIds));
  }

  public CompletableFuture<StatusCode> write(WriteValue value) {
    return getClient().thenCompose(c -> c.write(Collections.singletonList(value)).thenApply(WriteResponse::getResults).thenApply(d -> d[0]));
  }

  public CompletableFuture<StatusCode> writeValue(NodeId node, DataValue value) {
    return getClient().thenCompose(c -> c.writeValues(Collections.singletonList(node), Collections.singletonList(value)).thenApply(d -> d.get(0)));
  }

  @PreDestroy
  public void shutdown() {
    if (client.get() != null) {
      try {
        client.get().disconnect().get(500, TimeUnit.MILLISECONDS);
      } catch (TimeoutException | InterruptedException | ExecutionException e) {
        logger.error(e.getMessage(), e);
      }
    }
    Stack.releaseSharedResources(500, TimeUnit.MILLISECONDS);
  }

  private EndpointDescription changeEndpointUrl(EndpointDescription e, String url) {
    return new EndpointDescription(url, e.getServer(), e.getServerCertificate(), e.getSecurityMode(), e.getSecurityPolicyUri(), e.getUserIdentityTokens(),
        e.getTransportProfileUri(), e.getSecurityLevel());
  }

  private <T> CompletableFuture<T> buildCompleteExceptionally(Class<T> cl, Throwable th) {
    CompletableFuture<T> cf = new CompletableFuture<>();
    cf.completeExceptionally(th);
    return cf;
  }

}
