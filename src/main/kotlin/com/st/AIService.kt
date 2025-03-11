package com.st

import io.quarkiverse.langchain4j.RegisterAiService
import io.smallrye.mutiny.Multi
import jakarta.enterprise.context.SessionScoped

@SessionScoped
@RegisterAiService(retrievalAugmentor = ClaireRetrievalAugmentor::class,
    chatMemoryProviderSupplier = ClaireMemoryProviderSupplier::class)
interface AIService {
    fun chat(userMessage: String): Multi<String>
}