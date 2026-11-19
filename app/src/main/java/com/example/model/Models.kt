package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class SubjectColor(val label: String) {
    COBALT("Cobalt"),
    SAGE("Sage"),
    AMBER("Amber"),
    CORAL("Coral"),
    LAVENDER("Lavender"),
    TEAL("Teal");

    companion object {
        fun fromRaw(raw: String?): SubjectColor =
            entries.firstOrNull { it.name == raw } ?: COBALT
    }
}

enum class SubjectIcon(val label: String) {
    SCIENCE("Science"),
    HISTORY("History"),
    MATH("Math"),
    LITERATURE("Reading"),
    LANGUAGE("Language"),
    GENERAL("General");

    companion object {
        fun fromRaw(raw: String?): SubjectIcon =
            entries.firstOrNull { it.name == raw } ?: GENERAL
    }
}

enum class NoteSourceType(val label: String) {
    PRINTED("Printed Page"),
    HANDWRITING("Handwritten"),
    TYPED("Typed Note");

    companion object {
        fun fromRaw(raw: String?): NoteSourceType =
            entries.firstOrNull { it.name == raw } ?: PRINTED
    }
}

enum class HighlightColor(val label: String) {
    YELLOW("Sun"),
    MINT("Mint"),
    CORAL("Rose"),
    SKY("Sky");

    companion object {
        fun fromRaw(raw: String?): HighlightColor =
            entries.firstOrNull { it.name == raw } ?: YELLOW
    }
}

@Keep
@IgnoreExtraProperties
data class Subject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val colorKey: String = SubjectColor.COBALT.name,
    val iconKey: String = SubjectIcon.GENERAL.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedColor(): SubjectColor = SubjectColor.fromRaw(colorKey)
    fun resolvedIcon(): SubjectIcon = SubjectIcon.fromRaw(iconKey)

    fun toCreateMap(ownerUid: String): Map<String, Any> = mapOf(
        "userId" to ownerUid,
        "name" to name.trim().take(50),
        "colorKey" to SubjectColor.fromRaw(colorKey).name,
        "iconKey" to SubjectIcon.fromRaw(iconKey).name,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class StudyNote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subjectId: String = "",
    val title: String = "",
    val extractedText: String = "",
    val sourceType: String = NoteSourceType.TYPED.name,
    val tags: List<String> = emptyList(),
    val photoFileName: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedSourceType(): NoteSourceType = NoteSourceType.fromRaw(sourceType)

    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(ownerUid: String): Map<String, Any> = mapOf(
        "userId" to ownerUid,
        "subjectId" to subjectId,
        "title" to title.trim().take(120),
        "extractedText" to extractedText.take(20000),
        "sourceType" to NoteSourceType.fromRaw(sourceType).name,
        "tags" to tags.map { it.trim().take(32) }.filter { it.isNotEmpty() }.distinct().take(10),
        "photoFileName" to photoFileName?.trim()?.take(128)?.ifEmpty { null },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class KeyDefinition(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subjectId: String = "",
    val noteId: String = "",
    val term: String = "",
    val definition: String = "",
    val highlightColor: String = HighlightColor.YELLOW.name,
    val startOffset: Long = -1L,
    val endOffset: Long = -1L,
    val tags: List<String> = emptyList(),
    val mastered: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedColor(): HighlightColor = HighlightColor.fromRaw(highlightColor)

    fun toCreateMap(ownerUid: String): Map<String, Any> = mapOf(
        "userId" to ownerUid,
        "subjectId" to subjectId,
        "noteId" to noteId,
        "term" to term.trim().take(120),
        "definition" to definition.trim().take(1000),
        "highlightColor" to HighlightColor.fromRaw(highlightColor).name,
        "startOffset" to startOffset.coerceIn(-1L, 20000L),
        "endOffset" to endOffset.coerceIn(-1L, 20000L),
        "tags" to tags.map { it.trim().take(32) }.filter { it.isNotEmpty() }.distinct().take(10),
        "mastered" to mastered,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

data class ExtractedDefinitionSuggestion(
    val term: String,
    val definition: String
)

data class PhotoExtractionResult(
    val title: String,
    val sourceType: NoteSourceType,
    val extractedText: String,
    val suggestedDefinitions: List<ExtractedDefinitionSuggestion>
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
