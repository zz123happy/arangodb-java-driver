package containers;


import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ClusterContainer {

    private final Logger log = LoggerFactory.getLogger(ClusterContainer.class);
    private final String DOCKER_IMAGE = ContainerUtils.getImage();

    private final AtomicInteger createdAgents = new AtomicInteger();
    private final AtomicInteger createdDbServers = new AtomicInteger();
    private final AtomicInteger createdCoordinators = new AtomicInteger();

    private final Network network = Network.newNetwork();

    private final List<GenericContainer> agents;
    private final List<GenericContainer> dbServers;
    private final List<GenericContainer> coordinators;

    public ClusterContainer() {
        agents = Arrays.asList(
                createAgent(),
                createAgent(),
                createAgent()
        );
        dbServers = Arrays.asList(
                createDbServer(),
                createDbServer()
        );
        coordinators = Arrays.asList(
                createCoordinator(),
                createCoordinator()
        );
    }

    private GenericContainer createContainer(String name, int port) {
        return new GenericContainer(DOCKER_IMAGE)
                .withExposedPorts(port)
                .withNetwork(network)
                .withNetworkAliases(name)
                .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("[" + name + "]"))
                .waitingFor(Wait.forHttp("/_api/version").forStatusCode(200).withStartupTimeout(Duration.ofSeconds(300)));
    }

    private GenericContainer createAgent() {
        int count = createdAgents.incrementAndGet();
        String joinParameter = count == 1 ? "" : "--starter.join agent1";
        return createContainer("agent" + count, 8531)
                .withCommand("arangodb --cluster.start-dbserver false --cluster.start-coordinator false " + joinParameter);
    }

    private GenericContainer createDbServer() {
        int count = createdDbServers.incrementAndGet();
        return createContainer("dbserver" + count, 8530)
                .withCommand("arangodb --cluster.start-dbserver true --cluster.start-coordinator false --starter.join agent1");
    }

    private GenericContainer createCoordinator() {
        int count = createdCoordinators.incrementAndGet();
        return createContainer("coordinator" + count, 8529)
                .withCommand("arangodb --cluster.start-dbserver false --cluster.start-coordinator true --starter.join agent1");
    }

    private CompletableFuture<Void> performActionOnGroup(List<GenericContainer> group, Consumer<GenericContainer> action) {
        return CompletableFuture.allOf(
                group.stream()
                        .map(it -> CompletableFuture.runAsync(() -> action.accept(it)))
                        .toArray(CompletableFuture[]::new)
        );
    }

    public CompletableFuture<ClusterContainer> start() {
        return CompletableFuture.completedFuture(null)
                .thenCompose(v -> performActionOnGroup(agents, GenericContainer::start))
                .thenCompose(v -> performActionOnGroup(dbServers, GenericContainer::start))
                .thenCompose(v -> performActionOnGroup(coordinators, GenericContainer::start))
                .thenAccept((v) -> log.info("Cluster is ready!"))
                .thenApply((v) -> this);
    }

    public CompletableFuture<ClusterContainer> close() {
        return CompletableFuture.allOf(
                performActionOnGroup(agents, GenericContainer::stop),
                performActionOnGroup(dbServers, GenericContainer::stop),
                performActionOnGroup(coordinators, GenericContainer::stop)
        )
                .thenAccept((v) -> log.info("Cluster is ready!"))
                .thenApply((v) -> this);
    }

    public List<GenericContainer> getCoordinators() {
        return coordinators;
    }

    @Test
    void startCluster() {
        ClusterContainer cluster = new ClusterContainer().start().join();
        cluster.getCoordinators().forEach(it -> System.out.println(it.getContainerIpAddress() + ":" + it.getFirstMappedPort()));
        cluster.close().join();
    }

}
