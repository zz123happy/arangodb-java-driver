package deployments;


import com.arangodb.next.connection.HostDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ClusterDeployment implements ContainerDeployment {

    private final Logger log = LoggerFactory.getLogger(ClusterDeployment.class);
    private final String DOCKER_COMMAND = "arangodb --auth.jwt-secret /jwtSecret ";

    private final AtomicInteger createdAgentsCount = new AtomicInteger();
    private final AtomicInteger createdDbServersCount = new AtomicInteger();
    private final AtomicInteger createdCoordinatorsCount = new AtomicInteger();

    private final Network network;

    private final List<GenericContainer> agents;
    private final List<GenericContainer> dbServers;
    private final List<GenericContainer> coordinators;

    ClusterDeployment(int dbServers, int coordinators) {
        network = Network.newNetwork();

        agents = Arrays.asList(
                createAgent(),
                createAgent(),
                createAgent()
        );

        this.dbServers = IntStream.range(0, dbServers)
                .mapToObj(i -> createDbServer())
                .collect(Collectors.toList());

        this.coordinators = IntStream.range(0, coordinators)
                .mapToObj(i -> createCoordinator())
                .collect(Collectors.toList());
    }

    @Override
    public CompletableFuture<ContainerDeployment> start() {
        return CompletableFuture.completedFuture(null)
                .thenCompose(v -> performActionOnGroup(agents, GenericContainer::start))
                .thenCompose(v -> performActionOnGroup(dbServers, GenericContainer::start))
                .thenCompose(v -> performActionOnGroup(coordinators, GenericContainer::start))
                .thenCompose(v -> {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    CompletableFuture<Void> future = new CompletableFuture<>();
                    try {
                        Container.ExecResult result = coordinators.get(0).execInContainer(
                                "arangosh",
                                "--server.authentication=false",
                                "--javascript.execute-string=require('org/arangodb/users').update('root', 'test')");

                        if (result.getExitCode() != 0) {
                            throw new RuntimeException(result.getStderr() + "\n" + result.getStdout());
                        }

                        future.complete(v);
                    } catch (Exception e) {
                        e.printStackTrace();
                        future.completeExceptionally(e);
                    }
                    return future;
                })
                .thenAccept((v) -> log.info("Cluster is ready!"))
                .thenApply((v) -> this);
    }

    @Override
    public CompletableFuture<ContainerDeployment> stop() {
        return CompletableFuture.allOf(
                performActionOnGroup(agents, GenericContainer::stop),
                performActionOnGroup(dbServers, GenericContainer::stop),
                performActionOnGroup(coordinators, GenericContainer::stop)
        )
                .thenAccept((v) -> log.info("Cluster is ready!"))
                .thenApply((v) -> this);
    }

    @Override
    public List<HostDescription> getHosts() {
        return coordinators.stream()
                .map(it -> HostDescription.of(it.getContainerIpAddress(), it.getFirstMappedPort()))
                .collect(Collectors.toList());
    }

    private GenericContainer createContainer(String name, int port) {
        return new GenericContainer(getImage())
                .withCopyFileToContainer(MountableFile.forClasspathResource("deployments/jwtSecret"), "/jwtSecret")
                .withExposedPorts(port)
                .withNetwork(network)
                .withNetworkAliases(name)
                .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("[" + name + "]"))
                .waitingFor(Wait.forLogMessage(".*up and running.*", 1).withStartupTimeout(Duration.ofSeconds(300)));
    }

    private GenericContainer createAgent() {
        int count = createdAgentsCount.incrementAndGet();
        String joinParameter = count == 1 ? " " : "--starter.join agent1 ";
        return createContainer("agent" + count, 8531)
                .withCommand(DOCKER_COMMAND + "--cluster.start-dbserver false --cluster.start-coordinator false " + joinParameter);
    }

    private GenericContainer createDbServer() {
        int count = createdDbServersCount.incrementAndGet();
        return createContainer("dbserver" + count, 8530)
                .withCommand(DOCKER_COMMAND + "--cluster.start-dbserver true --cluster.start-coordinator false --starter.join agent1");
    }

    private GenericContainer createCoordinator() {
        int count = createdCoordinatorsCount.incrementAndGet();
        return createContainer("coordinator" + count, 8529)
                .withCommand(DOCKER_COMMAND + "--cluster.start-dbserver false --cluster.start-coordinator true --starter.join agent1");
    }

    private CompletableFuture<Void> performActionOnGroup(List<GenericContainer> group, Consumer<GenericContainer> action) {
        return CompletableFuture.allOf(
                group.stream()
                        .map(it -> CompletableFuture.runAsync(() -> action.accept(it)))
                        .toArray(CompletableFuture[]::new)
        );
    }

}
