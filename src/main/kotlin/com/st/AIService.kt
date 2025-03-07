package com.st

import io.quarkiverse.langchain4j.RegisterAiService
import io.smallrye.mutiny.Multi
import jakarta.enterprise.context.SessionScoped

@SessionScoped
@RegisterAiService(streamingChatLanguageModelSupplier = StreamingChatModelProvider::class,
    retrievalAugmentor = ClaireRetrievalAugmentor::class)
interface AIService {
    fun chat(userMessage: String): Multi<String>
}