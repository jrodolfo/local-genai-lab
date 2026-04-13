package net.jrodolfo.llm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Read-only preview of a text or JSON artifact under the reports directory.")
public record ArtifactPreviewResponse(
        @Schema(description = "Artifact path relative to the configured reports directory.")
        String path,
        @Schema(description = "Artifact path relative to the configured reports directory.")
        String relativePath,
        @Schema(description = "Artifact file name.")
        String fileName,
        @Schema(description = "Detected response content type.", example = "application/json")
        String contentType,
        @Schema(description = "File size in bytes.")
        long size,
        @Schema(description = "Last modified timestamp for the file.")
        Instant lastModified,
        @Schema(description = "Whether the preview content was truncated to the server-side preview limit.")
        boolean truncated,
        @Schema(description = "Preview content.")
        String content
) {
}
