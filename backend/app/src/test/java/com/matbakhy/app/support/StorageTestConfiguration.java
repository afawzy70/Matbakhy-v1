package com.matbakhy.app.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/** Local S3 emulator (same digest-pinned image as infra/compose.yaml) with the private and public buckets. */
@TestConfiguration(proxyBeanMethods = false)
public class StorageTestConfiguration {

    public static final String PRIVATE_BUCKET = "matbakhy-private";
    public static final String PUBLIC_BUCKET = "matbakhy-public";

    @Bean
    GenericContainer<?> objectStorageContainer() {
        String image = System.getProperty("matbakhy.test.s3mock-image");
        if (image == null || image.isBlank()) {
            throw new IllegalStateException("matbakhy.test.s3mock-image is not set; run tests through Maven");
        }
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse(image))
                .withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", PRIVATE_BUCKET + "," + PUBLIC_BUCKET)
                .withExposedPorts(9090)
                .waitingFor(Wait.forHttp("/").forPort(9090).forStatusCode(200));
        container.start();
        return container;
    }

    @Bean
    DynamicPropertyRegistrar storageProperties(GenericContainer<?> objectStorageContainer) {
        return registry -> registry.add("matbakhy.storage.endpoint",
                () -> "http://" + objectStorageContainer.getHost() + ":" + objectStorageContainer.getMappedPort(9090));
    }
}
