package com.example.dlmsconfigurator.core.data

data class StagedFile(
    val id: String,
    val fileName: String,
    val rawContent: String,
    val isValid: Boolean,
    val validationError: String? = null,
    val parsedContent: JsonFileContent? = null
)
