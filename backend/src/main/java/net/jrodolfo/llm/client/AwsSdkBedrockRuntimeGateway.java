package net.jrodolfo.llm.client;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ValidationException;

import java.util.stream.Collectors;

public class AwsSdkBedrockRuntimeGateway implements BedrockRuntimeGateway {

    private final BedrockRuntimeClient bedrockRuntimeClient;

    public AwsSdkBedrockRuntimeGateway(BedrockRuntimeClient bedrockRuntimeClient) {
        this.bedrockRuntimeClient = bedrockRuntimeClient;
    }

    @Override
    public String converse(String prompt, String modelId) {
        try {
            ConverseRequest request = ConverseRequest.builder()
                    .modelId(modelId)
                    .messages(Message.builder()
                            .role("user")
                            .content(ContentBlock.fromText(prompt))
                            .build())
                    .build();
            ConverseResponse response = bedrockRuntimeClient.converse(request);
            if (response.output() == null || response.output().message() == null || response.output().message().content() == null) {
                throw new ModelProviderException("Bedrock response did not contain message content.");
            }
            String output = response.output().message().content().stream()
                    .map(ContentBlock::text)
                    .filter(text -> text != null && !text.isBlank())
                    .collect(Collectors.joining());
            if (output.isBlank()) {
                throw new ModelProviderException("Bedrock response did not contain text output.");
            }
            return output;
        } catch (ValidationException ex) {
            throw new ModelProviderException("Bedrock request validation failed: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw new ModelProviderException("Failed to call Bedrock converse endpoint.", ex);
        }
    }
}
