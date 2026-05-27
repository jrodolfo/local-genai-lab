package net.jrodolfo.llm.service;

import net.jrodolfo.llm.config.AppStorageProperties;
import net.jrodolfo.llm.dto.ArtifactFileResponse;
import net.jrodolfo.llm.dto.ArtifactPreviewResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Reader;
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

/**
 * Service for managing chat artifacts, such as reports and logs.
 */
@Service
public class ChatArtifactService {

    private static final int MAX_PREVIEW_CHARACTERS = 20_000;
    private static final int PREVIEW_BUFFER_SIZE = 4_096;
    private static final Set<String> PREVIEWABLE_EXTENSIONS = Set.of(
            ".json", ".txt", ".log", ".md", ".yaml", ".yml", ".stderr", ".out"
    );

    private final Path reportsDirectory;

    /**
     * Constructs a new ChatArtifactService.
     *
     * @param properties application storage properties
     */
    public ChatArtifactService(AppStorageProperties properties) {
        this.reportsDirectory = properties.resolvedReportsDirectory().toAbsolutePath().normalize();
    }

    /**
     * Lists files within a specific run directory.
     *
     * @param runDir the directory containing the run artifacts
     * @return a list of artifact file responses
     */
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

    /**
     * Provides a preview of a specific artifact file.
     *
     * @param path the path to the artifact file
     * @return an artifact preview response
     */
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
            PreviewContent previewContent = readPreviewContent(resolvedPath);
            FileTime lastModified = Files.getLastModifiedTime(resolvedPath);
            String relativePath = toApiRelativePath(resolvedPath);
            return new ArtifactPreviewResponse(
                    relativePath,
                    relativePath,
                    resolvedPath.getFileName().toString(),
                    contentTypeFor(resolvedPath),
                    Files.size(resolvedPath),
                    Instant.ofEpochMilli(lastModified.toMillis()),
                    previewContent.truncated(),
                    previewContent.content()
            );
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to preview artifact: " + path, ex);
        }
    }

    /**
     * Reads the content of a file for preview, respecting a maximum character limit.
     *
     * @param path the path to the file
     * @return the preview content and truncation status
     * @throws IOException if an I/O error occurs
     */
    private PreviewContent readPreviewContent(Path path) throws IOException {
        StringBuilder builder = new StringBuilder(Math.min(MAX_PREVIEW_CHARACTERS, PREVIEW_BUFFER_SIZE));
        char[] buffer = new char[PREVIEW_BUFFER_SIZE];
        boolean truncated = false;

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            while (builder.length() < MAX_PREVIEW_CHARACTERS + 1) {
                int read = reader.read(buffer);
                if (read == -1) {
                    break;
                }
                builder.append(buffer, 0, read);
                if (builder.length() > MAX_PREVIEW_CHARACTERS) {
                    truncated = true;
                    builder.setLength(MAX_PREVIEW_CHARACTERS);
                    break;
                }
            }
        }

        return new PreviewContent(builder.toString(), truncated);
    }

    /**
     * Converts a file path to an ArtifactFileResponse.
     *
     * @param path the path to the file
     * @return an artifact file response
     */
    private ArtifactFileResponse toArtifactFileResponse(Path path) {
        try {
            String relativePath = toApiRelativePath(path);
            return new ArtifactFileResponse(
                    path.getFileName().toString(),
                    relativePath,
                    relativePath,
                    Files.size(path),
                    isPreviewable(path)
            );
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to inspect artifact file: " + path, ex);
        }
    }

    /**
     * Resolves a requested path within the reports directory, ensuring it is safe and relative.
     *
     * @param requestedPath the path requested by the client
     * @return the resolved absolute and normalized path
     */
    private Path resolveWithinReports(String requestedPath) {
        if (requestedPath == null || requestedPath.isBlank()) {
            throw new ArtifactAccessException(HttpStatus.BAD_REQUEST, "Artifact path must not be blank.");
        }

        Path candidate = Path.of(requestedPath);
        if (candidate.isAbsolute()) {
            throw new ArtifactAccessException(HttpStatus.BAD_REQUEST, "Artifact path must be relative to the reports directory.");
        }

        Path resolved = reportsDirectory.resolve(candidate).toAbsolutePath().normalize();

        if (resolved.startsWith(reportsDirectory)) {
            return resolved;
        }
        throw new ArtifactAccessException(HttpStatus.BAD_REQUEST, "Artifact path is outside the allowed reports directory.");
    }

    /**
     * Checks if a file is previewable based on its extension.
     *
     * @param path the path to the file
     * @return true if the file is previewable, false otherwise
     */
    private boolean isPreviewable(Path path) {
        String lowerName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return PREVIEWABLE_EXTENSIONS.stream().anyMatch(lowerName::endsWith);
    }

    /**
     * Converts a file path to a relative path used in the API.
     *
     * @param path the path to the file
     * @return the API-relative path string
     */
    private String toApiRelativePath(Path path) {
        return reportsDirectory.relativize(path).toString().replace('\\', '/');
    }

    /**
     * Determines the content type for a file based on its extension.
     *
     * @param path the path to the file
     * @return the content type string
     */
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

    /**
     * Internal record to hold preview content and its truncation status.
     *
     * @param content the preview content
     * @param truncated true if the content was truncated, false otherwise
     */
    private record PreviewContent(String content, boolean truncated) {
    }
}
