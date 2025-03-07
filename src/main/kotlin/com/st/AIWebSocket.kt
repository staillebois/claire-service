package com.st

import io.quarkus.websockets.next.OnOpen
import io.quarkus.websockets.next.OnTextMessage
import io.quarkus.websockets.next.WebSocket
import io.smallrye.mutiny.Multi

@WebSocket(path = "/stream")
class AIWebSocket(private val aiService: AIService) {

    @OnOpen
    fun onOpen(): String {
        return "How can I help you today?"
    }

    @OnTextMessage
    fun onTextMessage(message: String): Multi<String> {
        return aiService.chat(message)
    }
}