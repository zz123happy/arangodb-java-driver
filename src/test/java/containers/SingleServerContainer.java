package containers;


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

import java.util.concurrent.CompletableFuture;

public class SingleServerContainer {

    private static final Logger log = LoggerFactory.getLogger(SingleServerContainer.class);

    private final int PORT = 8529;
    private final String DOCKER_IMAGE = ContainerUtils.getImage();
    private final String PASSWORD = "test";

    private final Network network = Network.newNetwork();

    private final GenericContainer container =
            new GenericContainer(DOCKER_IMAGE)
                    .withExposedPorts(PORT)
                    .withEnv("ARANGO_ROOT_PASSWORD", PASSWORD)
                    .withNetwork(network)
                    .withNetworkAliases("db")
                    .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("[DB_LOG]"))
                    .waitingFor(Wait.forHttp("/_api/version")
                            .withBasicCredentials("root", "test")
                            .forStatusCode(200));

    private final ToxiproxyContainer toxiproxy = new ToxiproxyContainer().withNetwork(network);

    private ToxiproxyContainer.ContainerProxy proxy;

    public CompletableFuture<SingleServerContainer> start() {
        return CompletableFuture.allOf(
                CompletableFuture.runAsync(container::start).thenAccept((v) -> log.info("READY: db")),
                CompletableFuture.runAsync(toxiproxy::start).thenAccept((v) -> log.info("READY: toxiproxy"))
        ).thenApply((v) -> {
            proxy = toxiproxy.getProxy("db", PORT);
            return this;
        });
    }

    /**
     * call after {@link #start()}
     *
     * @return HostDescription of the proxy
     */
    public HostDescription getHostDescription() {
        return HostDescription.of(proxy.getContainerIpAddress(), proxy.getProxyPort());
    }

    public CompletableFuture<Void> stop() {
        return CompletableFuture.allOf(
                CompletableFuture.runAsync(container::stop).thenAccept((v) -> log.info("STOPPED: db")),
                CompletableFuture.runAsync(toxiproxy::stop).thenAccept((v) -> log.info("STOPPED: toxiproxy"))
        );
    }

    public GenericContainer getContainer() {
        return container;
    }

    public ToxiproxyContainer.ContainerProxy getProxy() {
        return proxy;
    }

    public void enableProxy() {
        log.debug("enableProxy()");
        setProxyEnabled(true);
        log.debug("... enableProxy() done");
    }

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
