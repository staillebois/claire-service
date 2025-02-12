package com.st

class CompleteAnswer {
    var generatedAnswer: String? = null

    private var relevantDocuments: List<Answer>? = null

    constructor()

    constructor(generatedAnswer: String?, relevantDocuments: List<Answer>?) {
        this.generatedAnswer = generatedAnswer
        this.relevantDocuments = relevantDocuments
    }

    fun getRelevantDocuments(): List<Answer>? {
        return relevantDocuments
    }

    fun setRelevantDocuments(relevantDocuments: List<Answer>?) {
        this.relevantDocuments = relevantDocuments
    }
}