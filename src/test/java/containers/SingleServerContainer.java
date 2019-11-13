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

public enum SingleServerContainer {

    INSTANCE;

    private final Logger log = LoggerFactory.getLogger(SingleServerContainer.class);

    private final int PORT = 8529;
    private final String DOCKER_IMAGE = "docker.io/arangodb/arangodb:3.5.1";
    private final String PASSWORD = "test";

    private Network network = Network.newNetwork();

    private final GenericContainer container =
            new GenericContainer(DOCKER_IMAGE)
                    .withExposedPorts(PORT)
                    .withEnv("ARANGO_ROOT_PASSWORD", PASSWORD)
                    .withNetwork(network)
                    .withNetworkAliases("db")
                    .withExposedPorts(8529)
                    .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("[DB_LOG]"))
                    .waitingFor(Wait.forHttp("/_api/version")
                            .withBasicCredentials("root", "test")
                            .forStatusCode(200));

    private final ToxiproxyContainer toxiproxy = new ToxiproxyContainer().withNetwork(network);

    private ToxiproxyContainer.ContainerProxy proxy;

    public HostDescription getHostDescription() {
        return HostDescription.of(proxy.getContainerIpAddress(), proxy.getProxyPort());
    }

    public CompletableFuture<SingleServerContainer> start() {
        return CompletableFuture.allOf(
                CompletableFuture.runAsync(container::start).thenAccept((v) -> log.info("READY: db")),
                CompletableFuture.runAsync(toxiproxy::start).thenAccept((v) -> log.info("READY: toxiproxy"))
        ).thenApply((v) -> {
            proxy = toxiproxy.getProxy("db", 8529);
            return this;
        });
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
        setProxyEnabled(true);
    }

    public void disableProxy() {
        setProxyEnabled(false);
    }

    private void setProxyEnabled(boolean value) {
        String request = new ObjectMapper().createObjectNode().put("enabled", value).toString();
        String response = HttpClient.create()
                .post()
                .uri("http://" + toxiproxy.getContainerIpAddress() + ":" + toxiproxy.getMappedPort(8474)
                        + "/proxies/db:8529")
                .send(Mono.just(Unpooled.wrappedBuffer(request.getBytes())))
                .responseContent()
                .asString()
                .blockFirst();

        log.info(response);
    }

}
