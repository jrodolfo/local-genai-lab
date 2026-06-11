package net.jrodolfo.llm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Artifact file entry under a report run directory.
 */
@Schema(description = "Artifact file entry under a report run directory.")
public record ArtifactFileResponse(
        @Schema(description = "File name.", example = "summary.json")
        String name,
        @Schema(description = "Absolute backend-visible artifact path used by preview/copy actions.")
        String path,
        @Schema(description = "Artifact path relative to the configured reports directory.")
        String relativePath,
        @Schema(description = "File size in bytes.", example = "128")
        long size,
        @Schema(description = "Whether the file can be previewed through the artifact preview endpoint.")
        boolean previewable
) {
}
