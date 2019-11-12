package containers;


import com.arangodb.next.connection.HostDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

public enum SingleServerWithChunkSizeContainer {

    INSTANCE;

    private final Logger log = LoggerFactory.getLogger(SingleServerWithChunkSizeContainer.class);

    private final int PORT = 8529;
    private final String DOCKER_IMAGE = "docker.io/arangodb/arangodb:3.5.1";
    private final String PASSWORD = "test";
    private final int CHUNK_SIZE = 8;

    private final GenericContainer container =
            new GenericContainer(DOCKER_IMAGE)
                    .withExposedPorts(PORT)
                    .withEnv("ARANGO_ROOT_PASSWORD", PASSWORD)
                    .withCommand("arangod --log.level communication=trace --log.level requests=trace --log.foreground-tty --vst.maxsize " + CHUNK_SIZE)
                    .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("[DB_LOG]"))
                    .waitingFor(Wait.forHttp("/_api/version")
                            .withBasicCredentials("root", "test")
                            .forStatusCode(200));

    {
        container.start();
    }

    public HostDescription getHostDescription() {
        return HostDescription.of(container.getContainerIpAddress(), container.getFirstMappedPort());
    }

    public void stop() {
        container.stop();
    }

}
