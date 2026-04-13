package net.jrodolfo.llm.client;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrock.model.InferenceProfileStatus;
import software.amazon.awssdk.services.bedrock.model.InferenceProfileSummary;
import software.amazon.awssdk.services.bedrock.model.InferenceProfileType;
import software.amazon.awssdk.services.bedrock.model.ListInferenceProfilesRequest;
import software.amazon.awssdk.services.bedrock.model.ListInferenceProfilesResponse;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class AwsSdkBedrockCatalogClient implements BedrockCatalogClient {

    private final BedrockClient bedrockClient;

    public AwsSdkBedrockCatalogClient(BedrockClient bedrockClient) {
        this.bedrockClient = bedrockClient;
    }

    @Override
    public List<String> listInferenceProfiles() {
        try {
            LinkedHashSet<String> profiles = new LinkedHashSet<>();
            String nextToken = null;
            do {
                ListInferenceProfilesResponse response = bedrockClient.listInferenceProfiles(
                        ListInferenceProfilesRequest.builder()
                                .maxResults(1000)
                                .typeEquals(InferenceProfileType.SYSTEM_DEFINED)
                                .nextToken(nextToken)
                                .build()
                );
                for (InferenceProfileSummary summary : response.inferenceProfileSummaries()) {
                    if (summary.status() != null && summary.status() != InferenceProfileStatus.ACTIVE) {
                        continue;
                    }
                    String inferenceProfileId = summary.inferenceProfileId();
                    if (inferenceProfileId != null && !inferenceProfileId.isBlank()) {
                        profiles.add(inferenceProfileId.trim());
                    }
                }
                nextToken = response.nextToken();
            } while (nextToken != null && !nextToken.isBlank());
            return new ArrayList<>(profiles);
        } catch (SdkException ex) {
            throw new ModelDiscoveryException("Bedrock model discovery failed: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw new ModelDiscoveryException("Bedrock model discovery failed.", ex);
        }
    }
}
