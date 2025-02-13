package com.st

import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingMatch
import dev.langchain4j.store.embedding.EmbeddingSearchRequest
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import org.apache.http.HttpHost
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.elasticsearch.client.RestClient
import org.jboss.logging.Logger


@ApplicationScoped
class ChatService(
    private val embeddingModel: EmbeddingModel
) {

    @ConfigProperty(name = "elastic.url")
    var elasticUrl: String? = null

    @ConfigProperty(name = "elastic.socket.timeout")
    var elasticSocketTimeout: Int = 0

    @ConfigProperty(name = "elastic.index.name")
    var elasticIndexName: String? = null

    private lateinit var store: EmbeddingStore<TextSegment>

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

    fun answer(question: String): CompleteAnswer {
        val answers: MutableList<Answer> = ArrayList()
        val relevant = getEmbeddingMatches(question,store)
        for (rel in relevant) {
            answers.add(Answer(rel.embedded().text(), rel.score(), rel.embedded().metadata()))
        }
        return CompleteAnswer("", answers)
    }

    private fun getEmbeddingMatches(
        question: String,
        store: EmbeddingStore<TextSegment>
    ): List<EmbeddingMatch<TextSegment>> {
        LOGGER.info("Start generating embedding for question: $question")
        val queryEmbedded: Embedding = embeddingModel.embed(question).content()
        LOGGER.info("End of embedding's generation ")
        val docs = store.search(
            EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedded)
                .maxResults(10)
                .build()
        )
        val relevant = docs.matches()
        LOGGER.info("End of finding relevant documents")

        relevant.sortWith { o1: EmbeddingMatch<TextSegment?>, o2: EmbeddingMatch<TextSegment?> ->
            o2.score().compareTo(o1.score())
        }
        return relevant
    }

    companion object {
        private val LOGGER: Logger = Logger.getLogger(ChatService::class.java)
    }
}