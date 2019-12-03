package deployments;


import com.arangodb.next.connection.HostDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SingleServerDeployment implements ProxiedContainerDeployment {

    private static final Logger log = LoggerFactory.getLogger(SingleServerDeployment.class);

    private final int PORT = 8529;

    private final ToxiproxyContainer toxiproxy;
    private final GenericContainer<?> container;

    private ToxiproxyContainer.ContainerProxy proxy;

    public SingleServerDeployment() {
        Network network = Network.newNetwork();
        toxiproxy = new ToxiproxyContainer().withNetwork(network);
        container = new GenericContainer<>(getImage())
                .withEnv("ARANGO_LICENSE_KEY", ContainerUtils.getLicenseKey())
                .withEnv("ARANGO_ROOT_PASSWORD", getPassword())
                .withExposedPorts(PORT)
                .withNetwork(network)
                .withNetworkAliases("db")
                .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("[DB_LOG]"))
                .waitingFor(Wait.forHttp("/_api/version")
                        .withBasicCredentials(getUser(), getPassword())
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
    public List<ProxiedHost> getProxiedHosts() {
        return Collections.singletonList(ProxiedHost.builder()
                .proxiedHost("db")
                .proxiedPort(PORT)
                .toxiproxy(toxiproxy)
                .build());
    }

}
