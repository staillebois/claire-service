package com.st

import dev.langchain4j.data.document.Metadata

class Answer {
    var text: String? = null

    var score: Double = 0.0

    var metadata: Map<String, Any>? = null

    constructor(text: String?, score: Double, metadata: Metadata) {
        this.text = text
        this.score = score
        setMetadata(metadata)
    }

    constructor()

    fun setMetadata(metadata: Metadata) {
        this.metadata = metadata.toMap()
    }
}