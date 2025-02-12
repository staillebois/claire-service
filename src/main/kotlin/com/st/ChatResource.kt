package com.st

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.jboss.logging.Logger

@Path("/hello")
class ChatResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    fun hello() = "Hello from Quarkus REST"

    companion object {
        private val LOGGER: Logger = Logger.getLogger(ChatResource::class.java)
    }
}