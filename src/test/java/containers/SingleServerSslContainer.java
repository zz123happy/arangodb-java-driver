package containers;


import com.arangodb.next.connection.HostDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

public enum SingleServerSslContainer {

    INSTANCE;

    private final Logger log = LoggerFactory.getLogger(SingleServerSslContainer.class);

    private final int PORT = 8529;
    private final String DOCKER_IMAGE = "docker.io/arangodb/arangodb:3.5.2";
    private final String PASSWORD = "test";
    private String SSL_CERT_PATH = Paths.get("docker/server.pem").toAbsolutePath().toString();

    public final GenericContainer container =
            new GenericContainer(DOCKER_IMAGE)
                    .withExposedPorts(PORT)
                    .withEnv("ARANGO_ROOT_PASSWORD", PASSWORD)
                    .withFileSystemBind(SSL_CERT_PATH, "/server.pem", BindMode.READ_ONLY)
                    .withCommand("arangod --ssl.keyfile /server.pem --server.endpoint ssl://0.0.0.0:8529")
                    .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("[DB_LOG]"))
                    .waitingFor(Wait.forLogMessage(".*ready for business.*", 1));


    public HostDescription getHostDescription() {
        return HostDescription.of(container.getContainerIpAddress(), container.getFirstMappedPort());
    }

    public CompletableFuture<SingleServerSslContainer> start() {
        return CompletableFuture.runAsync(container::start).thenAccept((v) -> log.info("Ready!")).thenApply((v) -> this);
    }

    public CompletableFuture<Void> stop() {
        return CompletableFuture.runAsync(container::stop).thenAccept((v) -> log.info("Stopped!"));
    }

}
