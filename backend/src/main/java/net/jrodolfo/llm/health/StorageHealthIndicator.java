package net.jrodolfo.llm.health;

import net.jrodolfo.llm.config.AppStorageProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Health indicator for the application's storage directories.
 * <p>
 * Checks if the sessions directory is accessible and if the reports directory
 * exists and is readable.
 */
@Component("storage")
public class StorageHealthIndicator implements HealthIndicator {

    private final Path sessionsDirectory;
    private final Path reportsDirectory;

    /**
     * Constructs a new StorageHealthIndicator with the specified storage properties.
     *
     * @param appStorageProperties the properties defining the storage paths.
     */
    public StorageHealthIndicator(AppStorageProperties appStorageProperties) {
        this.sessionsDirectory = appStorageProperties.resolvedSessionsDirectory();
        this.reportsDirectory = appStorageProperties.resolvedReportsDirectory();
    }

    /**
     * Performs the health check for the storage directories.
     *
     * @return the health status and details of the storage directories.
     */
    @Override
    public Health health() {
        try {
            Files.createDirectories(sessionsDirectory);
        } catch (IOException ex) {
            return Health.down()
                    .withDetail("sessionsDirectory", sessionsDirectory.toString())
                    .withDetail("reportsDirectory", reportsDirectory.toString())
                    .withDetail("error", "Failed to create or access the sessions directory: " + ex.getMessage())
                    .build();
        }

        boolean reportsExists = Files.isDirectory(reportsDirectory);
        boolean reportsReadable = reportsExists && Files.isReadable(reportsDirectory);
        if (!reportsExists || !reportsReadable) {
            return Health.down()
                    .withDetail("sessionsDirectory", sessionsDirectory.toString())
                    .withDetail("sessionsWritable", Files.isWritable(sessionsDirectory))
                    .withDetail("reportsDirectory", reportsDirectory.toString())
                    .withDetail("reportsExists", reportsExists)
                    .withDetail("reportsReadable", reportsReadable)
                    .build();
        }

        return Health.up()
                .withDetail("sessionsDirectory", sessionsDirectory.toString())
                .withDetail("sessionsWritable", Files.isWritable(sessionsDirectory))
                .withDetail("reportsDirectory", reportsDirectory.toString())
                .withDetail("reportsExists", true)
                .withDetail("reportsReadable", true)
                .build();
    }
}
