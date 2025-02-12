package com.st

import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.jboss.logging.Logger

@Path("/chat")
class ChatResource(
    private val chatService: ChatService
) {

    @POST
    fun chat(question: String): CompleteAnswer {
        val answer = chatService.answer(question)
        return answer
    }

    companion object {
        private val LOGGER: Logger = Logger.getLogger(ChatResource::class.java)
    }
}