package com.example.data

import android.content.Context
import com.example.R
import com.example.model.HighlightColor
import com.example.model.KeyDefinition
import com.example.model.NoteSourceType
import com.example.model.StudyNote
import com.example.model.Subject
import com.example.model.SubjectColor
import com.example.model.SubjectIcon
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class EduFlowRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireAuthorizedUser(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Please sign in with Google to sync your notes.")
        check(uid == expectedUserId) { "Signed-in account changed; refusing cross-user operation." }
        return uid
    }

    private fun subjectsCol(userId: String) =
        db.collection("users").document(userId).collection("subjects")

    private fun notesCol(userId: String) =
        db.collection("users").document(userId).collection("notes")

    private fun definitionsCol(userId: String) =
        db.collection("users").document(userId).collection("definitions")

    fun observeSubjects(userId: String): Flow<List<Subject>> = flow {
        if (userId.isBlank()) throw IllegalStateException("Missing user ID.")
        val path = "users/$userId/subjects"
        subjectsCol(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    Subject::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.name.lowercase() }
            }
            .catch { error ->
                logStreamErrorIfActive(error, userId, path)
                throw error
            }
            .collect { emit(it) }
    }

    fun observeNotes(userId: String): Flow<List<StudyNote>> = flow {
        if (userId.isBlank()) throw IllegalStateException("Missing user ID.")
        val path = "users/$userId/notes"
        notesCol(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    StudyNote::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                logStreamErrorIfActive(error, userId, path)
                throw error
            }
            .collect { emit(it) }
    }

    fun observeDefinitions(userId: String): Flow<List<KeyDefinition>> = flow {
        if (userId.isBlank()) throw IllegalStateException("Missing user ID.")
        val path = "users/$userId/definitions"
        definitionsCol(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    KeyDefinition::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                logStreamErrorIfActive(error, userId, path)
                throw error
            }
            .collect { emit(it) }
    }

    private fun logStreamErrorIfActive(error: Throwable, userId: String, path: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
        }
    }

    suspend fun createSubject(
        userId: String,
        name: String,
        color: SubjectColor,
        icon: SubjectIcon
    ): Result<String> {
        var docPath = "users/$userId/subjects"
        return try {
            val uid = requireAuthorizedUser(userId)
            val trimmed = name.trim()
            require(trimmed.length in 1..50) { "Subject name must be 1 to 50 characters." }
            val docRef = subjectsCol(uid).document()
            docPath = docRef.path
            val payload = Subject(
                userId = uid,
                name = trimmed,
                colorKey = color.name,
                iconKey = icon.name
            ).toCreateMap(uid)
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateSubject(
        userId: String,
        subjectId: String,
        name: String,
        color: SubjectColor,
        icon: SubjectIcon
    ): Result<Unit> {
        val docPath = "users/$userId/subjects/$subjectId"
        return try {
            val uid = requireAuthorizedUser(userId)
            require(subjectId.isNotBlank()) { "Subject ID cannot be blank." }
            val trimmed = name.trim()
            require(trimmed.length in 1..50) { "Subject name must be 1 to 50 characters." }
            subjectsCol(uid).document(subjectId).update(
                mapOf(
                    "name" to trimmed,
                    "colorKey" to color.name,
                    "iconKey" to icon.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSubject(userId: String, subjectId: String): Result<Unit> {
        val docPath = "users/$userId/subjects/$subjectId"
        return try {
            val uid = requireAuthorizedUser(userId)
            require(subjectId.isNotBlank()) { "Subject ID cannot be blank." }
            val notesSnap = notesCol(uid).whereEqualTo("subjectId", subjectId).get().await()
            val defsSnap = definitionsCol(uid).whereEqualTo("subjectId", subjectId).get().await()
            val batch = db.batch()
            defsSnap.documents.forEach { batch.delete(it.reference) }
            notesSnap.documents.forEach { batch.delete(it.reference) }
            batch.delete(subjectsCol(uid).document(subjectId))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createNote(
        userId: String,
        subjectId: String,
        title: String,
        extractedText: String,
        sourceType: NoteSourceType,
        tags: List<String>,
        photoFileName: String?
    ): Result<String> {
        var docPath = "users/$userId/notes"
        return try {
            val uid = requireAuthorizedUser(userId)
            require(subjectId.isNotBlank()) { "Please select a subject for this note." }
            val cleanTitle = title.trim().ifEmpty { "Untitled Note" }.take(120)
            val docRef = notesCol(uid).document()
            docPath = docRef.path
            val payload = StudyNote(
                userId = uid,
                subjectId = subjectId,
                title = cleanTitle,
                extractedText = extractedText.take(20000),
                sourceType = sourceType.name,
                tags = sanitizeTags(tags),
                photoFileName = photoFileName
            ).toCreateMap(uid)
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateNote(
        userId: String,
        noteId: String,
        subjectId: String,
        title: String,
        extractedText: String,
        sourceType: NoteSourceType,
        tags: List<String>,
        photoFileName: String?
    ): Result<Unit> {
        val docPath = "users/$userId/notes/$noteId"
        return try {
            val uid = requireAuthorizedUser(userId)
            require(noteId.isNotBlank()) { "Note ID cannot be blank." }
            require(subjectId.isNotBlank()) { "Subject ID cannot be blank." }
            val cleanTitle = title.trim().ifEmpty { "Untitled Note" }.take(120)
            val updates = mutableMapOf<String, Any>(
                "subjectId" to subjectId,
                "title" to cleanTitle,
                "extractedText" to extractedText.take(20000),
                "sourceType" to sourceType.name,
                "tags" to sanitizeTags(tags),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!photoFileName.isNullOrBlank()) {
                updates["photoFileName"] = photoFileName.trim().take(128)
            }
            notesCol(uid).document(noteId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteNote(userId: String, noteId: String): Result<Unit> {
        val docPath = "users/$userId/notes/$noteId"
        return try {
            val uid = requireAuthorizedUser(userId)
            require(noteId.isNotBlank()) { "Note ID cannot be blank." }
            val defsSnap = definitionsCol(uid).whereEqualTo("noteId", noteId).get().await()
            val batch = db.batch()
            defsSnap.documents.forEach { batch.delete(it.reference) }
            batch.delete(notesCol(uid).document(noteId))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createDefinition(
        userId: String,
        subjectId: String,
        noteId: String,
        term: String,
        definition: String,
        highlightColor: HighlightColor,
        startOffset: Int,
        endOffset: Int,
        tags: List<String>
    ): Result<String> {
        var docPath = "users/$userId/definitions"
        return try {
            val uid = requireAuthorizedUser(userId)
            require(subjectId.isNotBlank() && noteId.isNotBlank()) { "Subject and Note are required." }
            val cleanTerm = term.trim()
            val cleanDef = definition.trim()
            require(cleanTerm.length in 1..120) { "Term must be 1 to 120 characters." }
            require(cleanDef.length in 1..1000) { "Definition must be 1 to 1000 characters." }
            val docRef = definitionsCol(uid).document()
            docPath = docRef.path
            val payload = KeyDefinition(
                userId = uid,
                subjectId = subjectId,
                noteId = noteId,
                term = cleanTerm,
                definition = cleanDef,
                highlightColor = highlightColor.name,
                startOffset = startOffset.toLong().coerceIn(-1L, 20000L),
                endOffset = endOffset.toLong().coerceIn(-1L, 20000L),
                tags = sanitizeTags(tags),
                mastered = false
            ).toCreateMap(uid)
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateDefinition(
        userId: String,
        definitionId: String,
        subjectId: String,
        term: String,
        definition: String,
        highlightColor: HighlightColor,
        startOffset: Int,
        endOffset: Int,
        tags: List<String>,
        mastered: Boolean
    ): Result<Unit> {
        val docPath = "users/$userId/definitions/$definitionId"
        return try {
            val uid = requireAuthorizedUser(userId)
            require(definitionId.isNotBlank()) { "Definition ID cannot be blank." }
            val cleanTerm = term.trim()
            val cleanDef = definition.trim()
            require(cleanTerm.length in 1..120) { "Term must be 1 to 120 characters." }
            require(cleanDef.length in 1..1000) { "Definition must be 1 to 1000 characters." }
            definitionsCol(uid).document(definitionId).update(
                mapOf(
                    "subjectId" to subjectId,
                    "term" to cleanTerm,
                    "definition" to cleanDef,
                    "highlightColor" to highlightColor.name,
                    "startOffset" to startOffset.toLong().coerceIn(-1L, 20000L),
                    "endOffset" to endOffset.toLong().coerceIn(-1L, 20000L),
                    "tags" to sanitizeTags(tags),
                    "mastered" to mastered,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleDefinitionMastered(
        userId: String,
        definitionId: String,
        mastered: Boolean
    ): Result<Unit> {
        val docPath = "users/$userId/definitions/$definitionId"
        return try {
            val uid = requireAuthorizedUser(userId)
            require(definitionId.isNotBlank()) { "Definition ID cannot be blank." }
            definitionsCol(uid).document(definitionId).update(
                mapOf(
                    "mastered" to mastered,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteDefinition(userId: String, definitionId: String): Result<Unit> {
        val docPath = "users/$userId/definitions/$definitionId"
        return try {
            val uid = requireAuthorizedUser(userId)
            require(definitionId.isNotBlank()) { "Definition ID cannot be blank." }
            definitionsCol(uid).document(definitionId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private fun sanitizeTags(tags: List<String>): List<String> =
        tags.map { it.trim().take(32) }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(10)
}
