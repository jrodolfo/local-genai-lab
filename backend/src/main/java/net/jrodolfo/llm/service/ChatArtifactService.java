package net.jrodolfo.llm.service;

import net.jrodolfo.llm.config.AppStorageProperties;
import net.jrodolfo.llm.dto.ArtifactFileResponse;
import net.jrodolfo.llm.dto.ArtifactPreviewResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class ChatArtifactService {

    private static final int MAX_PREVIEW_CHARACTERS = 20_000;
    private static final Set<String> PREVIEWABLE_EXTENSIONS = Set.of(
            ".json", ".txt", ".log", ".md", ".yaml", ".yml", ".stderr", ".out"
    );

    private final Path reportsDirectory;

    public ChatArtifactService(AppStorageProperties properties) {
        this.reportsDirectory = Path.of(properties.reportsDirectory()).toAbsolutePath().normalize();
    }

    public List<ArtifactFileResponse> listFiles(String runDir) {
        Path resolvedRunDir = resolveWithinReports(runDir);
        if (!Files.exists(resolvedRunDir)) {
            throw new ArtifactAccessException(HttpStatus.NOT_FOUND, "Artifact directory does not exist: " + runDir);
        }
        if (!Files.isDirectory(resolvedRunDir)) {
            throw new ArtifactAccessException(HttpStatus.BAD_REQUEST, "Artifact path is not a directory: " + runDir);
        }

        try (Stream<Path> paths = Files.walk(resolvedRunDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> reportsDirectory.relativize(path).toString()))
                    .map(this::toArtifactFileResponse)
                    .toList();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to list artifact files for: " + runDir, ex);
        }
    }

    public ArtifactPreviewResponse previewFile(String path) {
        Path resolvedPath = resolveWithinReports(path);
        if (!Files.exists(resolvedPath)) {
            throw new ArtifactAccessException(HttpStatus.NOT_FOUND, "Artifact file does not exist: " + path);
        }
        if (!Files.isRegularFile(resolvedPath)) {
            throw new ArtifactAccessException(HttpStatus.BAD_REQUEST, "Artifact path is not a file: " + path);
        }
        if (!isPreviewable(resolvedPath)) {
            throw new ArtifactAccessException(HttpStatus.BAD_REQUEST, "Artifact file type is not previewable: " + resolvedPath.getFileName());
        }

        try {
            String content = Files.readString(resolvedPath, StandardCharsets.UTF_8);
            boolean truncated = content.length() > MAX_PREVIEW_CHARACTERS;
            if (truncated) {
                content = content.substring(0, MAX_PREVIEW_CHARACTERS);
            }
            FileTime lastModified = Files.getLastModifiedTime(resolvedPath);
            return new ArtifactPreviewResponse(
                    resolvedPath.toString(),
                    reportsDirectory.relativize(resolvedPath).toString(),
                    resolvedPath.getFileName().toString(),
                    contentTypeFor(resolvedPath),
                    Files.size(resolvedPath),
                    Instant.ofEpochMilli(lastModified.toMillis()),
                    truncated,
                    content
            );
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to preview artifact: " + path, ex);
        }
    }

    private ArtifactFileResponse toArtifactFileResponse(Path path) {
        try {
            return new ArtifactFileResponse(
                    path.getFileName().toString(),
                    path.toString(),
                    reportsDirectory.relativize(path).toString(),
                    Files.size(path),
                    isPreviewable(path)
            );
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to inspect artifact file: " + path, ex);
        }
    }

    private Path resolveWithinReports(String requestedPath) {
        Path candidate = Path.of(requestedPath);
        Path resolved = candidate.isAbsolute()
                ? candidate.toAbsolutePath().normalize()
                : reportsDirectory.resolve(candidate).toAbsolutePath().normalize();

        if (resolved.startsWith(reportsDirectory)) {
            return resolved;
        }
        throw new ArtifactAccessException(HttpStatus.BAD_REQUEST, "Artifact path is outside the allowed reports directory.");
    }

    private boolean isPreviewable(Path path) {
        String lowerName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return PREVIEWABLE_EXTENSIONS.stream().anyMatch(lowerName::endsWith);
    }

    private String contentTypeFor(Path path) {
        String lowerName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".json")) {
            return "application/json";
        }
        if (lowerName.endsWith(".md")) {
            return "text/markdown";
        }
        return "text/plain";
    }
}
