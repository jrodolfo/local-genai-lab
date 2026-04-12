package net.jrodolfo.llm.dto;

public record ArtifactFileResponse(
        String name,
        String path,
        String relativePath,
        long size,
        boolean previewable
) {
}
