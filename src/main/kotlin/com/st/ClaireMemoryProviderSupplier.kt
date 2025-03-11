package com.st

import dev.langchain4j.memory.chat.ChatMemoryProvider
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import jakarta.enterprise.context.ApplicationScoped
import java.util.function.Supplier

@ApplicationScoped
class ClaireMemoryProviderSupplier : Supplier<ChatMemoryProvider>{
    override fun get(): ChatMemoryProvider {
        return ChatMemoryProvider { MessageWindowChatMemory.withMaxMessages(10) }
    }
}
