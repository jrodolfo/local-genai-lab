package net.jrodolfo.llm.provider;

import net.jrodolfo.llm.config.AppModelProperties;
import net.jrodolfo.llm.service.InvalidProviderException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves configured chat model providers by stable provider id.
 *
 * <p>The registry validates the configured default provider at startup and is
 * the single place where blank provider requests fall back to that default.
 */
public class ChatModelProviderRegistry {

    private final String defaultProvider;
    private final Map<String, ChatModelProvider> providers;

    /**
     * Constructs a new ChatModelProviderRegistry.
     *
     * @param appModelProperties the application configuration properties for model providers
     * @param providers          a map of available providers, keyed by their identifier
     */
    public ChatModelProviderRegistry(AppModelProperties appModelProperties, Map<String, ChatModelProvider> providers) {
        this.providers = Map.copyOf(providers);
        this.defaultProvider = normalizeProvider(appModelProperties.provider());
        if (!this.providers.containsKey(this.defaultProvider)) {
            throw new IllegalStateException(
                    "Configured default provider '%s' is not available. Supported providers are: %s"
                            .formatted(this.defaultProvider, this.providers.keySet())
            );
        }
    }

    /**
     * Retrieves a provider by id, falling back to the configured default for blank input.
     *
     * @param provider requested provider id
     * @return resolved provider implementation
     * @throws InvalidProviderException if the provider is not registered
     */
    public ChatModelProvider get(String provider) {
        String resolvedProvider = provider == null || provider.isBlank() ? defaultProvider : normalizeProvider(provider);
        ChatModelProvider chatModelProvider = providers.get(resolvedProvider);
        if (chatModelProvider == null) {
            throw new InvalidProviderException(
                    "Unsupported model provider: %s. Supported providers are: %s"
                            .formatted(resolvedProvider, supportedProviders())
            );
        }
        return chatModelProvider;
    }

    /**
     * Resolves the provider name, using the default if none is provided.
     *
     * @param provider the requested provider name
     * @return the normalized provider name
     */
    public String resolveProviderName(String provider) {
        return provider == null || provider.isBlank() ? defaultProvider : normalizeProvider(provider);
    }

    /**
     * Gets the identifier of the default provider.
     *
     * @return the default provider identifier
     */
    public String defaultProvider() {
        return defaultProvider;
    }

    /**
     * Returns a list of all supported provider identifiers.
     *
     * @return a sorted list of supported provider identifiers
     */
    public List<String> supportedProviders() {
        return providers.keySet().stream().sorted().toList();
    }

    /**
     * Normalizes the provider name by trimming and converting to lowercase.
     *
     * @param provider the raw provider name
     * @return the normalized provider name
     */
    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return "ollama";
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }
}
