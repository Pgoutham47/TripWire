// The model process's interface. The model runs in its own process (":llm") so a native crash
// in the LLM runtime can never take down protection (PRD 14.5).
package com.tripwire.app.ai;

interface ITacticModel {
    /** Raw model answer for one message, or null when no backend can run the model. */
    String generate(String modelPath, String systemInstruction, String userPrompt, String jsonSchema);
    /** "ready:<backend>", "idle", "loading", or "unavailable". */
    String status();
    void release();
}
