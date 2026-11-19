package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.EduFlowRepository
import com.example.data.NoteAiRepository
import com.example.data.PhotoStorageHelper
import com.example.model.ExtractedDefinitionSuggestion
import com.example.model.HighlightColor
import com.example.model.KeyDefinition
import com.example.model.NoteSourceType
import com.example.model.StudyNote
import com.example.model.Subject
import com.example.model.SubjectColor
import com.example.model.SubjectIcon
import com.example.model.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EduFlowWorkspace(
    val subjects: List<Subject> = emptyList(),
    val notes: List<StudyNote> = emptyList(),
    val definitions: List<KeyDefinition> = emptyList()
)

data class NoteDraft(
    val subjectId: String = "",
    val title: String = "",
    val extractedText: String = "",
    val sourceType: NoteSourceType = NoteSourceType.TYPED,
    val tags: List<String> = emptyList(),
    val photoFileName: String? = null,
    val suggestedDefinitions: List<ExtractedDefinitionSuggestion> = emptyList()
)

sealed interface ExtractionStatus {
    data object Idle : ExtractionStatus
    data object Extracting : ExtractionStatus
    data class Error(val message: String) : ExtractionStatus
}

class EduFlowViewModel(
    private val repository: EduFlowRepository,
    private val aiRepository: NoteAiRepository,
    private val currentUserId: String
) : ViewModel() {

    private val subjectsFlow: StateFlow<UiState<List<Subject>>> =
        repository.observeSubjects(currentUserId)
            .map<List<Subject>, UiState<List<Subject>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing subjects", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load subjects"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val notesFlow: StateFlow<UiState<List<StudyNote>>> =
        repository.observeNotes(currentUserId)
            .map<List<StudyNote>, UiState<List<StudyNote>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing notes", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load notes"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val definitionsFlow: StateFlow<UiState<List<KeyDefinition>>> =
        repository.observeDefinitions(currentUserId)
            .map<List<KeyDefinition>, UiState<List<KeyDefinition>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing definitions", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load definitions"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val workspaceState: StateFlow<UiState<EduFlowWorkspace>> =
        combine(subjectsFlow, notesFlow, definitionsFlow) { sState, nState, dState ->
            when {
                sState is UiState.Error -> UiState.Error(sState.message)
                nState is UiState.Error -> UiState.Error(nState.message)
                dState is UiState.Error -> UiState.Error(dState.message)
                sState is UiState.Success && nState is UiState.Success && dState is UiState.Success -> {
                    UiState.Success(
                        EduFlowWorkspace(
                            subjects = sState.data,
                            notes = nState.data,
                            definitions = dState.data
                        )
                    )
                }
                else -> UiState.Loading
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val _extractionStatus = MutableStateFlow<ExtractionStatus>(ExtractionStatus.Idle)
    val extractionStatus: StateFlow<ExtractionStatus> = _extractionStatus.asStateFlow()

    private val _pendingDraft = MutableStateFlow<NoteDraft?>(null)
    val pendingDraft: StateFlow<NoteDraft?> = _pendingDraft.asStateFlow()

    private val _selectedSubjectFilter = MutableStateFlow<String?>(null)
    val selectedSubjectFilter: StateFlow<String?> = _selectedSubjectFilter.asStateFlow()

    private val _selectedReviewerSubjectId = MutableStateFlow<String?>(null)
    val selectedReviewerSubjectId: StateFlow<String?> = _selectedReviewerSubjectId.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    fun setNotesSubjectFilter(subjectId: String?) {
        _selectedSubjectFilter.value = subjectId
    }

    fun setReviewerSubjectId(subjectId: String?) {
        _selectedReviewerSubjectId.value = subjectId
    }

    fun startNewTypedNoteDraft(preferredSubjectId: String?) {
        _pendingDraft.value = NoteDraft(
            subjectId = preferredSubjectId.orEmpty(),
            sourceType = NoteSourceType.TYPED
        )
    }

    fun clearPendingDraft() {
        _pendingDraft.value = null
    }

    fun clearExtractionError() {
        _extractionStatus.value = ExtractionStatus.Idle
    }

    fun extractFromPhotoUri(
        context: Context,
        uri: Uri,
        preferredSubjectId: String?,
        onDraftReady: () -> Unit
    ) {
        viewModelScope.launch {
            _extractionStatus.value = ExtractionStatus.Extracting
            try {
                val (fileName, bitmap) = PhotoStorageHelper.saveCompressedPhoto(context, uri)
                val result = aiRepository.extractTextFromPhoto(bitmap)
                result.fold(
                    onSuccess = { extracted ->
                        _pendingDraft.value = NoteDraft(
                            subjectId = preferredSubjectId.orEmpty(),
                            title = extracted.title,
                            extractedText = extracted.extractedText,
                            sourceType = extracted.sourceType,
                            tags = listOf(
                                if (extracted.sourceType == NoteSourceType.HANDWRITING) "Handwritten"
                                else "Printed Page"
                            ),
                            photoFileName = fileName,
                            suggestedDefinitions = extracted.suggestedDefinitions
                        )
                        _extractionStatus.value = ExtractionStatus.Idle
                        onDraftReady()
                    },
                    onFailure = { err ->
                        val msg = err.localizedMessage ?: "Failed to extract text from photo."
                        _extractionStatus.value = ExtractionStatus.Error(msg)
                        _snackbarEvents.tryEmit(msg)
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process selected photo", e)
                val msg = e.localizedMessage ?: "Could not open selected photo."
                _extractionStatus.value = ExtractionStatus.Error(msg)
                _snackbarEvents.tryEmit(msg)
            }
        }
    }

    suspend fun suggestDefinitionsForText(text: String): Result<List<ExtractedDefinitionSuggestion>> {
        return aiRepository.suggestDefinitionsFromText(text)
    }

    fun createSubject(
        name: String,
        color: SubjectColor,
        icon: SubjectIcon,
        onCreated: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.createSubject(currentUserId, name, color, icon).fold(
                onSuccess = { id ->
                    _snackbarEvents.tryEmit("Subject \"$name\" created")
                    onCreated(id)
                },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.localizedMessage ?: "Could not create subject")
                }
            )
        }
    }

    suspend fun createSubjectSync(
        name: String,
        color: SubjectColor,
        icon: SubjectIcon
    ): Result<String> {
        return repository.createSubject(currentUserId, name, color, icon)
    }

    fun updateSubject(
        subjectId: String,
        name: String,
        color: SubjectColor,
        icon: SubjectIcon
    ) {
        viewModelScope.launch {
            repository.updateSubject(currentUserId, subjectId, name, color, icon).fold(
                onSuccess = { _snackbarEvents.tryEmit("Subject updated") },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.localizedMessage ?: "Could not update subject")
                }
            )
        }
    }

    fun deleteSubject(subjectId: String) {
        viewModelScope.launch {
            repository.deleteSubject(currentUserId, subjectId).fold(
                onSuccess = {
                    if (_selectedSubjectFilter.value == subjectId) _selectedSubjectFilter.value = null
                    if (_selectedReviewerSubjectId.value == subjectId) _selectedReviewerSubjectId.value = null
                    _snackbarEvents.tryEmit("Subject deleted")
                },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.localizedMessage ?: "Could not delete subject")
                }
            )
        }
    }

    suspend fun saveNote(
        existingNoteId: String?,
        subjectId: String,
        title: String,
        extractedText: String,
        sourceType: NoteSourceType,
        tags: List<String>,
        photoFileName: String?
    ): Result<String> {
        return if (existingNoteId.isNullOrBlank()) {
            repository.createNote(
                userId = currentUserId,
                subjectId = subjectId,
                title = title,
                extractedText = extractedText,
                sourceType = sourceType,
                tags = tags,
                photoFileName = photoFileName
            )
        } else {
            repository.updateNote(
                userId = currentUserId,
                noteId = existingNoteId,
                subjectId = subjectId,
                title = title,
                extractedText = extractedText,
                sourceType = sourceType,
                tags = tags,
                photoFileName = photoFileName
            ).map { existingNoteId }
        }
    }

    fun deleteNote(noteId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteNote(currentUserId, noteId).fold(
                onSuccess = {
                    _snackbarEvents.tryEmit("Note deleted")
                    onDeleted()
                },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.localizedMessage ?: "Could not delete note")
                }
            )
        }
    }

    fun addDefinition(
        subjectId: String,
        noteId: String,
        term: String,
        definition: String,
        highlightColor: HighlightColor,
        startOffset: Int,
        endOffset: Int,
        tags: List<String>,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.createDefinition(
                userId = currentUserId,
                subjectId = subjectId,
                noteId = noteId,
                term = term,
                definition = definition,
                highlightColor = highlightColor,
                startOffset = startOffset,
                endOffset = endOffset,
                tags = tags
            ).fold(
                onSuccess = {
                    _snackbarEvents.tryEmit("Highlighted \"$term\"")
                    onDone()
                },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.localizedMessage ?: "Could not save highlight")
                }
            )
        }
    }

    fun updateDefinition(
        definitionId: String,
        subjectId: String,
        term: String,
        definition: String,
        highlightColor: HighlightColor,
        startOffset: Int,
        endOffset: Int,
        tags: List<String>,
        mastered: Boolean
    ) {
        viewModelScope.launch {
            repository.updateDefinition(
                userId = currentUserId,
                definitionId = definitionId,
                subjectId = subjectId,
                term = term,
                definition = definition,
                highlightColor = highlightColor,
                startOffset = startOffset,
                endOffset = endOffset,
                tags = tags,
                mastered = mastered
            ).fold(
                onSuccess = { _snackbarEvents.tryEmit("Definition updated") },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.localizedMessage ?: "Could not update definition")
                }
            )
        }
    }

    fun toggleMastered(definition: KeyDefinition) {
        viewModelScope.launch {
            repository.toggleDefinitionMastered(
                userId = currentUserId,
                definitionId = definition.id,
                mastered = !definition.mastered
            ).onFailure { err ->
                _snackbarEvents.tryEmit(err.localizedMessage ?: "Could not update progress")
            }
        }
    }

    fun deleteDefinition(definitionId: String) {
        viewModelScope.launch {
            repository.deleteDefinition(currentUserId, definitionId).fold(
                onSuccess = { _snackbarEvents.tryEmit("Highlight removed") },
                onFailure = { err ->
                    _snackbarEvents.tryEmit(err.localizedMessage ?: "Could not remove highlight")
                }
            )
        }
    }

    fun showMessage(msg: String) {
        _snackbarEvents.tryEmit(msg)
    }

    private companion object {
        const val TAG = "EduFlowVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
