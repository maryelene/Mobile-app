package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.PhotoStorageHelper
import com.example.model.KeyDefinition
import com.example.model.StudyNote
import com.example.ui.theme.StudyReadingStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewerScreen(
    workspace: EduFlowWorkspace,
    selectedSubjectId: String?,
    onSelectSubject: (String?) -> Unit,
    onToggleMastered: (KeyDefinition) -> Unit,
    onOpenNote: (String) -> Unit
) {
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var quizMode by remember { mutableStateOf(false) }
    val revealedMap = remember { mutableStateMapOf<String, Boolean>() }

    val subjectById = remember(workspace.subjects) {
        workspace.subjects.associateBy { it.id }
    }
    val noteById = remember(workspace.notes) {
        workspace.notes.associateBy { it.id }
    }

    val activeSubject = selectedSubjectId?.let { subjectById[it] }

    val subjectNotes = remember(workspace.notes, selectedSubjectId, selectedTag, searchQuery) {
        workspace.notes.filter { note ->
            val matchSub = selectedSubjectId == null || note.subjectId == selectedSubjectId
            val matchTag = selectedTag == null || note.tags.any { it.equals(selectedTag, ignoreCase = true) }
            val matchSearch = searchQuery.isBlank() ||
                note.title.contains(searchQuery, ignoreCase = true) ||
                note.extractedText.contains(searchQuery, ignoreCase = true)
            matchSub && matchTag && matchSearch
        }
    }

    val taggedPhotos = remember(subjectNotes) {
        subjectNotes.filter { !it.photoFileName.isNullOrBlank() }
    }

    val subjectDefinitions = remember(workspace.definitions, selectedSubjectId, selectedTag, searchQuery) {
        workspace.definitions.filter { def ->
            val matchSub = selectedSubjectId == null || def.subjectId == selectedSubjectId
            val matchTag = selectedTag == null || def.tags.any { it.equals(selectedTag, ignoreCase = true) }
            val matchSearch = searchQuery.isBlank() ||
                def.term.contains(searchQuery, ignoreCase = true) ||
                def.definition.contains(searchQuery, ignoreCase = true)
            matchSub && matchTag && matchSearch
        }
    }

    val availableTags = remember(workspace.notes, workspace.definitions, selectedSubjectId) {
        val noteTags = workspace.notes
            .filter { selectedSubjectId == null || it.subjectId == selectedSubjectId }
            .flatMap { it.tags }
        val defTags = workspace.definitions
            .filter { selectedSubjectId == null || it.subjectId == selectedSubjectId }
            .flatMap { it.tags }
        (noteTags + defTags).distinct().sorted()
    }

    val masteredCount = subjectDefinitions.count { it.mastered }
    val masteryRatio = if (subjectDefinitions.isEmpty()) 0f
    else masteredCount.toFloat() / subjectDefinitions.size.toFloat()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = activeSubject?.name?.let { "$it Reviewer" } ?: "Subject Reviewer",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Your tagged photos and highlighted definitions combined into a clean study sheet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (workspace.subjects.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedSubjectId == null,
                            onClick = {
                                onSelectSubject(null)
                                selectedTag = null
                            },
                            label = { Text("All Subjects") }
                        )
                        workspace.subjects.forEach { subj ->
                            FilterChip(
                                selected = selectedSubjectId == subj.id,
                                onClick = {
                                    onSelectSubject(subj.id)
                                    selectedTag = null
                                },
                                label = { Text(subj.name) }
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search terms, definitions, or notes…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reviewer_search_input")
                )
            }

            if (availableTags.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableTags.forEach { tag ->
                            FilterChip(
                                selected = selectedTag == tag,
                                onClick = {
                                    selectedTag = if (selectedTag == tag) null else tag
                                },
                                label = { Text("#$tag") }
                            )
                        }
                    }
                }
            }

            // Summary + Quiz Toggle Card
            item {
                val accent = activeSubject?.resolvedColor()?.toAccentColor()
                    ?: MaterialTheme.colorScheme.primary
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${subjectDefinitions.size} Definitions • ${taggedPhotos.size} Tagged Photos",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "$masteredCount of ${subjectDefinitions.size} reviewed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.quiz_mode),
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Switch(
                                    checked = quizMode,
                                    onCheckedChange = {
                                        quizMode = it
                                        revealedMap.clear()
                                    },
                                    modifier = Modifier.testTag("quiz_mode_switch")
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = { masteryRatio },
                            color = accent,
                            trackColor = accent.copy(alpha = 0.16f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(999.dp))
                        )
                    }
                }
            }

            // Tagged Photos Strip
            if (taggedPhotos.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Rounded.PhotoCamera,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Tagged Study Photos (${taggedPhotos.size})",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(taggedPhotos, key = { it.id }) { note ->
                                TaggedPhotoCard(
                                    note = note,
                                    onClick = { onOpenNote(note.id) }
                                )
                            }
                        }
                    }
                }
            }

            // Key Definitions Section
            if (subjectDefinitions.isEmpty() && taggedPhotos.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.widthIn(max = 520.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AsyncImage(
                                    model = R.drawable.img_empty_notes,
                                    contentDescription = "Empty reviewer",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .clip(RoundedCornerShape(16.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Text(
                                    text = "No reviewer items yet",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = "Capture a study page or open a note to highlight key definitions. They will appear here automatically.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else if (subjectDefinitions.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Key Definitions (${subjectDefinitions.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                items(subjectDefinitions, key = { it.id }) { def ->
                    val sourceNote = noteById[def.noteId]
                    val subjectName = subjectById[def.subjectId]?.name ?: "Subject"
                    val isRevealed = !quizMode || (revealedMap[def.id] == true)
                    ReviewerDefinitionCard(
                        definition = def,
                        subjectName = subjectName,
                        sourceNoteTitle = sourceNote?.title ?: "Note",
                        isRevealed = isRevealed,
                        quizMode = quizMode,
                        onRevealClick = { revealedMap[def.id] = !(revealedMap[def.id] ?: false) },
                        onToggleMastered = { onToggleMastered(def) },
                        onOpenSourceNote = {
                            if (sourceNote != null) onOpenNote(sourceNote.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TaggedPhotoCard(
    note: StudyNote,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val photoFile = remember(note.photoFileName) {
        PhotoStorageHelper.getPhotoFile(context, note.photoFileName)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .width(190.dp)
            .clickable(onClick = onClick)
    ) {
        Column {
            if (photoFile != null) {
                AsyncImage(
                    model = photoFile,
                    contentDescription = note.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                )
            }
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (note.tags.isNotEmpty()) {
                    Text(
                        text = note.tags.joinToString(" ") { "#$it" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewerDefinitionCard(
    definition: KeyDefinition,
    subjectName: String,
    sourceNoteTitle: String,
    isRevealed: Boolean,
    quizMode: Boolean,
    onRevealClick: () -> Unit,
    onToggleMastered: () -> Unit,
    onOpenSourceNote: () -> Unit
) {
    val markerColor = definition.resolvedColor().toMarkerColor()

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (definition.mastered) BorderStroke(
            1.5.dp,
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
        ) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = quizMode, onClick = onRevealClick)
            .testTag("reviewer_def_card_${definition.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = markerColor
                ) {
                    Text(
                        text = definition.term,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                IconButton(
                    onClick = onToggleMastered,
                    modifier = Modifier.testTag("toggle_mastered_${definition.id}")
                ) {
                    Icon(
                        imageVector = if (definition.mastered) Icons.Rounded.CheckCircle
                        else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = if (definition.mastered) "Marked as reviewed" else "Mark as reviewed",
                        tint = if (definition.mastered) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = isRevealed) {
                Text(
                    text = definition.definition,
                    style = StudyReadingStyle,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (!isRevealed) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Tap card to reveal definition",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable(onClick = onOpenSourceNote)
                ) {
                    Text(
                        text = "$subjectName • $sourceNoteTitle",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                definition.tags.take(3).forEach { tag ->
                    TagPill(tag = tag)
                }
            }
        }
    }
}
