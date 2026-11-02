package ca.yorku.eecs3311.ai;

/**
 * Interface the application depends on for text generation (Adapter target).
 * Services such as StudyAgent, SyllabusService and QuizService use only this
 * type, so provider-specific details stay in implementations.
 */
public interface LLMClient {
    /**
     * Send a prompt to the model and return the generated text.
     *
     * @throws LLMException if the request fails or the response is unusable
     */
    String generate(String prompt) throws LLMException;
}