package deployments;


import com.arangodb.next.connection.HostDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SingleServerSslDeployment implements ContainerDeployment {

    private static final Logger log = LoggerFactory.getLogger(SingleServerSslDeployment.class);

    private final GenericContainer<?> container;

    public SingleServerSslDeployment() {
        String SSL_CERT_PATH = Paths.get("docker/server.pem").toAbsolutePath().toString();
        container = new GenericContainer<>(getImage())
                .withEnv("ARANGO_LICENSE_KEY", ContainerUtils.getLicenseKey())
                .withEnv("ARANGO_ROOT_PASSWORD", getPassword())
                .withExposedPorts(8529)
                .withFileSystemBind(SSL_CERT_PATH, "/server.pem", BindMode.READ_ONLY)
                .withCommand("arangod --ssl.keyfile /server.pem --server.endpoint ssl://0.0.0.0:8529")
                .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("[DB_LOG]"))
                .waitingFor(Wait.forLogMessage(".*ready for business.*", 1));
    }

    @Override
    public List<HostDescription> getHosts() {
        return Collections.singletonList(HostDescription.of(container.getContainerIpAddress(), container.getFirstMappedPort()));
    }

    @Override
    public CompletableFuture<ContainerDeployment> asyncStart() {
        return CompletableFuture.runAsync(container::start).thenAccept((v) -> log.info("Ready!")).thenApply((v) -> this);
    }

    @Override
    public CompletableFuture<ContainerDeployment> asyncStop() {
        return CompletableFuture.runAsync(container::stop).thenAccept((v) -> log.info("Stopped!")).thenApply((v) -> this);
    }

}
