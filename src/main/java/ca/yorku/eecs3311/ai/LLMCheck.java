package ca.yorku.eecs3311.ai;

import ca.yorku.eecs3311.config.AppConfig;
import java.io.IOException;

/**
 * Manual check that the local API key, model and network work. Sends one tiny request
 * and prints the reply. Run it from VS Code (Run above main) or with Maven's exec plugin.
 * It never prints the key.
 */
public final class LLMCheck {
    private LLMCheck() {
    }

    public static void main(String[] args) throws IOException {
        AppConfig config = AppConfig.load();
        if (!config.hasApiKey()) {
            System.out.println("No API key found. Copy .env.example to .env and set LLM_API_KEY.");
            return;
        }
        System.out.println("Model: " + config.getModel() + " at " + config.getBaseUrl());
        try {
            String reply = DeepSeekClient.fromConfig(config)
                    .generate("Reply with exactly the single word: ready");
            System.out.println("Reply: " + reply.strip());
            System.out.println("The key and model work.");
        } catch (LLMException e) {
            System.out.println("The request failed: " + e.getMessage());
        }
    }
}