package deployments;


import com.arangodb.next.connection.HostDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ActiveFailoverDeployment extends ContainerDeployment {

    private final Logger log = LoggerFactory.getLogger(ActiveFailoverDeployment.class);

    private volatile Network network;

    private final Map<String, GenericContainer<?>> servers;

    ActiveFailoverDeployment(int servers) {

        this.servers = IntStream.range(0, servers)
                .mapToObj(i -> "server" + i)
                .map(name -> new AbstractMap.SimpleEntry<String, GenericContainer<?>>(name, createServer(name)))
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));

    }

    @Override
    public CompletableFuture<ContainerDeployment> asyncStart() {
        return CompletableFuture
                .runAsync(() -> {
                    network = Network.newNetwork();
                    servers.values().forEach(agent -> agent.withNetwork(network));
                })
                .thenCompose(__ -> CompletableFuture.allOf(
                        performActionOnGroup(servers.values(), GenericContainer::start)
                ))
                .thenCompose(__ -> {
                    CompletableFuture<Void> future = new CompletableFuture<>();
                    try {
                        for (GenericContainer<?> server : servers.values()) {
                            server.execInContainer(
                                    "arangosh",
                                    "--server.authentication=false",
                                    "--javascript.execute-string=require('org/arangodb/users').update('" + getUser() + "', '" + getPassword() + "')");
                        }
                        future.complete(null);
                    } catch (Exception e) {
                        e.printStackTrace();
                        future.completeExceptionally(e);
                    }
                    return future;
                })
                .thenCompose(__ -> CompletableFuture.runAsync(() -> ContainerUtils.waitForAuthenticationUpdate(this)))
                .thenAccept(__ -> log.info("Cluster is ready!"))
                .thenApply(__ -> this);
    }

    @Override
    public CompletableFuture<ContainerDeployment> asyncStop() {
        return CompletableFuture.allOf(
                performActionOnGroup(servers.values(), GenericContainer::stop)
        )
                .thenAcceptAsync(__ -> network.close())
                .thenAccept((v) -> log.info("Cluster has been shutdown!"))
                .thenApply((v) -> this);
    }

    private String getContainerIP(final GenericContainer<?> container) {
        return container.getContainerInfo()
                .getNetworkSettings()
                .getNetworks()
                .get(((Network.NetworkImpl) network).getName())
                .getIpAddress();
    }

    @Override
    public List<HostDescription> getHosts() {
        return servers.values().stream()
                .map(it -> HostDescription.of(getContainerIP(it), 8529))
                .collect(Collectors.toList());
    }

    private GenericContainer<?> createContainer(String name) {
        return new GenericContainer<>(getImage())
                .withEnv("ARANGO_LICENSE_KEY", ContainerUtils.getLicenseKey())
                .withCopyFileToContainer(MountableFile.forClasspathResource("deployments/jwtSecret"), "/jwtSecret")
                .withExposedPorts(8529)
                .withNetworkAliases(name)
                .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("[" + name + "]"))
                .waitingFor(Wait.forLogMessage(".*resilientsingle up and running.*", 1).withStartupTimeout(Duration.ofSeconds(60)));
    }

    private GenericContainer<?> createServer(String name) {
        String DOCKER_COMMAND = "arangodb --starter.address=$(hostname -i) --auth.jwt-secret /jwtSecret --starter.mode=activefailover --starter.join server1,server2,server3";
        return createContainer(name)
                .withCommand("sh", "-c", DOCKER_COMMAND);
    }

    private CompletableFuture<Void> performActionOnGroup(Collection<GenericContainer<?>> group, Consumer<GenericContainer<?>> action) {
        return CompletableFuture.allOf(
                group.stream()
                        .map(it -> CompletableFuture.runAsync(() -> action.accept(it)))
                        .toArray(CompletableFuture[]::new)
        );
    }

}
