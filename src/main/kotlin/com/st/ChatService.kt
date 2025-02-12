package com.st

import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore
import jakarta.annotation.PostConstruct
import org.apache.http.HttpHost
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.elasticsearch.client.RestClient
import org.jboss.logging.Logger

class ChatService {

    @ConfigProperty(name = "elastic.url")
    var elasticUrl: String? = null

    @ConfigProperty(name = "elastic.socket.timeout")
    var elasticSocketTimeout: Int = 0

    @ConfigProperty(name = "elastic.index.name")
    var elasticIndexName: String? = null

    private var store: ElasticsearchEmbeddingStore? = null

    @PostConstruct
    fun initialize() {
        store = ElasticsearchEmbeddingStore.builder()
            .restClient(buildRestClient())
            .indexName(elasticIndexName)
            .build()
        LOGGER.info("Elasticsearch Indexing Client OK on: $elasticUrl for index $elasticIndexName")
    }

    private fun buildRestClient(): RestClient {
        return RestClient.builder(HttpHost.create(elasticUrl))
            .setRequestConfigCallback{requestConfigBuilder -> requestConfigBuilder.setSocketTimeout(elasticSocketTimeout)}
            .build()
    }

    companion object {
        private val LOGGER: Logger = Logger.getLogger(ChatService::class.java)
    }
}