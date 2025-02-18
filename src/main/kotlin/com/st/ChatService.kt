package com.st

import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.input.Prompt
import dev.langchain4j.model.input.PromptTemplate
import dev.langchain4j.model.language.LanguageModel
import dev.langchain4j.model.ollama.OllamaLanguageModel;
import dev.langchain4j.store.embedding.EmbeddingMatch
import dev.langchain4j.store.embedding.EmbeddingSearchRequest
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.model.output.Response
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import org.apache.http.HttpHost
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.elasticsearch.client.RestClient
import org.jboss.logging.Logger
import java.util.stream.Collectors
import java.time.Duration


@ApplicationScoped
class ChatService(
    private val embeddingModel: EmbeddingModel
) {

    @ConfigProperty(name = "elastic.url")
    var elasticUrl: String = ""

    @ConfigProperty(name = "elastic.socket.timeout")
    var elasticSocketTimeout: Int = 0

    @ConfigProperty(name = "elastic.index.name")
    var elasticIndexName: String = ""

    @ConfigProperty(name = "ollama.url")
    var ollamaUrl: String = ""

    @ConfigProperty(name = "ollama.model")
    var ollamaModel: String = ""

    @ConfigProperty(name = "ollama.duration")
    var ollamaDuration: Long = 0

    private lateinit var store: EmbeddingStore<TextSegment>

    private val promptTemplate: PromptTemplate = PromptTemplate.from(
        """
                Answer the following question :
               
                Question:
                {{question}}
                
                Base your answer on the following information:
                {{information}}
                
                """
    )

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
        val useLanguageModel = true
        val languageModel: LanguageModel = createLanguageModel()
        val relevant = getEmbeddingMatches(question,store)
        for (rel in relevant) {
            answers.add(Answer(rel.embedded().text(), rel.score(), rel.embedded().metadata()))
        }
        if (useLanguageModel) {
            val information = relevant.stream().map { rlv: EmbeddingMatch<TextSegment> -> rlv.embedded().text() }
                .collect(Collectors.joining("\n\n"))
            val prompt: Prompt = getPrompt(question, information)

            val generatedAnswer: Response<String> = languageModel.generate(prompt)
            return CompleteAnswer(generatedAnswer.content(), answers)
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

    private fun getPrompt(question: String, information: String): Prompt {
        val templateParameters: MutableMap<String, Any> = HashMap()
        templateParameters["question"] = question
        templateParameters["information"] = information
        return promptTemplate.apply(templateParameters)
    }

    fun createLanguageModel(): LanguageModel {
        return getLanguageModelBuilder()
            .timeout(Duration.ofSeconds(ollamaDuration))
            .build()
    }

    private fun getLanguageModelBuilder(): OllamaLanguageModel.OllamaLanguageModelBuilder {
        return OllamaLanguageModel.builder()
            .baseUrl(ollamaUrl)
            .modelName(ollamaModel)
    }

    companion object {
        private val LOGGER: Logger = Logger.getLogger(ChatService::class.java)
    }
}