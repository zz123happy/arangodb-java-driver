package deployments;


import com.arangodb.next.connection.HostDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SingleServerDeployment implements ProxiedContainerDeployment {

    private static final Logger log = LoggerFactory.getLogger(SingleServerDeployment.class);

    private final int PORT = 8529;

    private final Network network;
    private final ToxiproxyContainer toxiproxy;
    private final GenericContainer<?> container;

    private ToxiproxyContainer.ContainerProxy proxy;

    public SingleServerDeployment() {
        network = Network.newNetwork();
        toxiproxy = new ToxiproxyContainer().withNetwork(network);
        container = new GenericContainer<>(getImage())
                .withExposedPorts(PORT)
                .withEnv("ARANGO_ROOT_PASSWORD", "test")
                .withNetwork(network)
                .withNetworkAliases("db")
                .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("[DB_LOG]"))
                .waitingFor(Wait.forHttp("/_api/version")
                        .withBasicCredentials("root", "test")
                        .forStatusCode(200));

    }

    /**
     * call after {@link #start()}
     *
     * @return HostDescription of the proxy
     */
    @Override
    public List<HostDescription> getHosts() {
        return Collections.singletonList(HostDescription.of(proxy.getContainerIpAddress(), proxy.getProxyPort()));
    }

    @Override
    public CompletableFuture<ProxiedContainerDeployment> asyncStart() {
        return CompletableFuture.allOf(
                CompletableFuture.runAsync(container::start).thenAccept((v) -> log.info("READY: db")),
                CompletableFuture.runAsync(toxiproxy::start).thenAccept((v) -> log.info("READY: toxiproxy"))
        )
                .thenCompose(v -> CompletableFuture.runAsync(() -> proxy = toxiproxy.getProxy("db", PORT)))
                .thenApply(v -> this);
    }


    @Override
    public CompletableFuture<ContainerDeployment> asyncStop() {
        return CompletableFuture.allOf(
                CompletableFuture.runAsync(container::stop).thenAccept((v) -> log.info("STOPPED: db")),
                CompletableFuture.runAsync(toxiproxy::stop).thenAccept((v) -> log.info("STOPPED: toxiproxy"))
        ).thenApply(v -> this);
    }

    @Override
    public ToxiproxyContainer.ContainerProxy getProxy() {
        return proxy;
    }

    @Override
    public void enableProxy() {
        log.debug("enableProxy()");
        setProxyEnabled(true);
        log.debug("... enableProxy() done");
    }

    @Override
    public void disableProxy() {
        log.debug("disableProxy()");
        setProxyEnabled(false);
        log.debug("... disableProxy() done");
    }

    /**
     * Bringing a service down is not technically a toxic in the implementation of Toxiproxy. This is done by POSTing
     * to /proxies/{proxy} and setting the enabled field to false.
     *
     * @param value value to set
     * @see <a href="https://github.com/Shopify/toxiproxy#down">
     */
    private void setProxyEnabled(boolean value) {
        String request = new ObjectMapper().createObjectNode().put("enabled", value).toString();
        String response = HttpClient.create()
                .post()
                .uri("http://" + toxiproxy.getContainerIpAddress() + ":" + toxiproxy.getMappedPort(8474)
                        + "/proxies/db:" + PORT)
                .send(Mono.just(Unpooled.wrappedBuffer(request.getBytes())))
                .responseContent()
                .asString()
                .blockFirst();

        log.debug(response);
    }

}
