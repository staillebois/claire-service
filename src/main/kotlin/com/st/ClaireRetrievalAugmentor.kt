package com.st

import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.rag.DefaultRetrievalAugmentor
import dev.langchain4j.rag.RetrievalAugmentor
import dev.langchain4j.rag.content.injector.ContentInjector
import dev.langchain4j.rag.content.injector.DefaultContentInjector
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore
import jakarta.enterprise.inject.Default
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.apache.http.HttpHost
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.elasticsearch.client.RestClient
import java.util.function.Supplier
import org.jboss.logging.Logger

@Singleton
class ClaireRetrievalAugmentor : Supplier<RetrievalAugmentor> {

    @Inject
    @field: Default
    lateinit var embeddingModel: EmbeddingModel;

    @ConfigProperty(name = "elastic.url")
    var elasticUrl: String = ""

    @ConfigProperty(name = "elastic.socket.timeout")
    var elasticSocketTimeout: Int = 0

    @ConfigProperty(name = "elastic.index.name")
    var elasticIndexName: String = ""

    override fun get(): RetrievalAugmentor {

        val store = ElasticsearchEmbeddingStore.builder()
            .restClient(buildRestClient())
            .indexName(elasticIndexName)
            .build()
        LOGGER.info("Elasticsearch Indexing Client OK on: $elasticUrl for index $elasticIndexName")

        val contentRetriever: ContentRetriever = EmbeddingStoreContentRetriever.builder()
            .embeddingStore(store)
            .embeddingModel(embeddingModel)
            .maxResults(3)
            .minScore(0.55)
            .build()

        val contentInjector: ContentInjector = DefaultContentInjector.builder()
            .metadataKeysToInclude(listOf("title", "index", "url"))
            .build()

        return  DefaultRetrievalAugmentor.builder()
//            .queryTransformer(queryTransformer)
            .contentRetriever(contentRetriever)
            .contentInjector(contentInjector)
            .build()
    }

    private fun buildRestClient(): RestClient {
        return RestClient.builder(HttpHost.create(elasticUrl))
            .setRequestConfigCallback{requestConfigBuilder -> requestConfigBuilder.setSocketTimeout(elasticSocketTimeout)}
            .build()
    }

    companion object {
        private val LOGGER: Logger = Logger.getLogger(ClaireRetrievalAugmentor::class.java)
    }
}
