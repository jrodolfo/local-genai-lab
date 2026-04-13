package net.jrodolfo.llm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Artifact file entry under a report run directory.")
public record ArtifactFileResponse(
        @Schema(description = "File name.", example = "summary.json")
        String name,
        @Schema(description = "Artifact path relative to the configured reports directory.")
        String path,
        @Schema(description = "Artifact path relative to the configured reports directory.")
        String relativePath,
        @Schema(description = "File size in bytes.", example = "128")
        long size,
        @Schema(description = "Whether the file can be previewed through the artifact preview endpoint.")
        boolean previewable
) {
}
