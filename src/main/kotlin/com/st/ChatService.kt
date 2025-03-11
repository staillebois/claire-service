package com.st

import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.input.Prompt
import dev.langchain4j.model.input.PromptTemplate
import dev.langchain4j.model.language.LanguageModel
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.model.ollama.OllamaLanguageModel
import dev.langchain4j.model.output.Response
import dev.langchain4j.rag.DefaultRetrievalAugmentor
import dev.langchain4j.rag.RetrievalAugmentor
import dev.langchain4j.rag.content.injector.ContentInjector
import dev.langchain4j.rag.content.injector.DefaultContentInjector
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever
import dev.langchain4j.rag.query.Query
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer
import dev.langchain4j.rag.query.transformer.QueryTransformer
import dev.langchain4j.service.AiServices
import dev.langchain4j.rag.query.Metadata
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
import java.time.Duration
import java.util.stream.Collectors


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

    private val chatMemory: ChatMemory = MessageWindowChatMemory.withMaxMessages(10)

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

    fun answerWithMemory(question: String): CompleteAnswer {
        val chatModel: ChatLanguageModel = createChatModel()
        val relevant = getEmbeddingMatches(question, store)

        val answers: MutableList<Answer> = ArrayList()
        val info = StringBuilder()

        for (match in relevant) {
            answers.add(Answer(match.embedded().text(), match.score(), match.embedded().metadata()))
            if (!info.isEmpty()) {
                info.append(System.lineSeparator())
            }
            info.append(match.embedded().text())
        }
        val information = info.toString()
        val prompt = getPrompt(question, information)
        val queryTransformer: QueryTransformer = CompressingQueryTransformer(chatModel)
        val query: Query = Query.from(
            prompt.text(), Metadata(
                UserMessage.from(prompt.text()), chatMemory.messages(),
                chatMemory.messages()
            )
        )
        queryTransformer.transform(query)

        val contentRetriever: ContentRetriever = EmbeddingStoreContentRetriever.builder()
            .embeddingStore(store)
            .embeddingModel(embeddingModel)
            .maxResults(3)
            .minScore(0.55)
            .build()

        val contentInjector: ContentInjector = DefaultContentInjector.builder()
            .metadataKeysToInclude(listOf("title", "index", "url"))
            .build()

        val retrievalAugmentor: RetrievalAugmentor = DefaultRetrievalAugmentor.builder()
            .queryTransformer(queryTransformer)
            .contentRetriever(contentRetriever)
            .contentInjector(contentInjector)
            .build()

        val documentChat: DocumentChat = AiServices.builder<DocumentChat>(DocumentChat::class.java)
            .chatLanguageModel(chatModel)
            .retrievalAugmentor(retrievalAugmentor)
            .chatMemory(chatMemory)
            .build()

        return CompleteAnswer(documentChat.answer(question), answers)
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
                .maxResults(3)
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

    fun createChatModel(): ChatLanguageModel {
        LOGGER.info("Creating Chat Model on $ollamaUrl with model $ollamaModel")
        return OllamaChatModel.builder()
            .baseUrl(ollamaUrl)
            .modelName(ollamaModel)
            .timeout(Duration.ofSeconds(ollamaDuration))
            .build()
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