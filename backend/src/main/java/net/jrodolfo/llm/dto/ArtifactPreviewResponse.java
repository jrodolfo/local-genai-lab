package net.jrodolfo.llm.dto;

import java.time.Instant;

public record ArtifactPreviewResponse(
        String path,
        String relativePath,
        String fileName,
        String contentType,
        long size,
        Instant lastModified,
        boolean truncated,
        String content
) {
}
