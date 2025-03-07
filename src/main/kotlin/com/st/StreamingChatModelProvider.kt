package com.st

import dev.langchain4j.model.chat.StreamingChatLanguageModel
import io.quarkiverse.langchain4j.ollama.OllamaStreamingChatLanguageModel
import jakarta.inject.Singleton
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Duration
import java.util.function.Supplier

@Singleton
class StreamingChatModelProvider : Supplier<StreamingChatLanguageModel>{

    @ConfigProperty(name = "ollama.url")
    var ollamaUrl: String = ""

    @ConfigProperty(name = "ollama.model")
    var ollamaModel: String = ""

    @ConfigProperty(name = "ollama.duration")
    var ollamaDuration: Long = 0

    override fun get(): StreamingChatLanguageModel {
        return OllamaStreamingChatLanguageModel.builder()
            .baseUrl(ollamaUrl)
            .model(ollamaModel)
            .timeout(Duration.ofSeconds(ollamaDuration))
            .build()
    }
}
