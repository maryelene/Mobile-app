package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.PhotoStorageHelper
import com.example.model.ExtractedDefinitionSuggestion
import com.example.model.HighlightColor
import com.example.model.KeyDefinition
import com.example.model.NoteSourceType
import com.example.model.StudyNote
import com.example.model.SubjectColor
import com.example.model.SubjectIcon
import com.example.ui.theme.StudyReadingStyle
import kotlinx.coroutines.launch

private data class WordSpan(
    val start: Int,
    val end: Int,
    val word: String
)

private enum class EditorTab(val label: String) {
    EDIT("Edit Text"),
    HIGHLIGHT("Highlight Definitions")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteEditorScreen(
    existingNote: StudyNote?,
    draft: NoteDraft?,
    workspace: EduFlowWorkspace,
    onBack: () -> Unit,
    onSaveNote: suspend (
        existingNoteId: String?,
        subjectId: String,
        title: String,
        extractedText: String,
        sourceType: NoteSourceType,
        tags: List<String>,
        photoFileName: String?
    ) -> Result<String>,
    onCreateSubjectSync: suspend (String, SubjectColor, SubjectIcon) -> Result<String>,
    onSuggestDefinitions: suspend (String) -> Result<List<ExtractedDefinitionSuggestion>>,
    onAddDefinition: (
        subjectId: String,
        noteId: String,
        term: String,
        definition: String,
        highlightColor: HighlightColor,
        startOffset: Int,
        endOffset: Int,
        tags: List<String>
    ) -> Unit,
    onDeleteDefinition: (String) -> Unit,
    onDeleteNote: (String, () -> Unit) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentNoteId by remember(existingNote?.id) { mutableStateOf(existingNote?.id) }
    var subjectId by remember(existingNote?.id, draft) {
        mutableStateOf(
            existingNote?.subjectId
                ?: draft?.subjectId?.takeIf { it.isNotBlank() }
                ?: workspace.subjects.firstOrNull()?.id.orEmpty()
        )
    }
    var title by remember(existingNote?.id, draft) {
        mutableStateOf(existingNote?.title ?: draft?.title.orEmpty())
    }
    var extractedText by remember(existingNote?.id, draft) {
        mutableStateOf(existingNote?.extractedText ?: draft?.extractedText.orEmpty())
    }
    var sourceType by remember(existingNote?.id, draft) {
        mutableStateOf(existingNote?.resolvedSourceType() ?: draft?.sourceType ?: NoteSourceType.TYPED)
    }
    var tags by remember(existingNote?.id, draft) {
        mutableStateOf(existingNote?.tags ?: draft?.tags.orEmpty())
    }
    val photoFileName = existingNote?.photoFileName ?: draft?.photoFileName
    var suggestions by remember(existingNote?.id, draft) {
        mutableStateOf(draft?.suggestedDefinitions.orEmpty())
    }

    LaunchedEffect(workspace.subjects) {
        if (subjectId.isBlank() && workspace.subjects.isNotEmpty()) {
            subjectId = workspace.subjects.first().id
        }
    }

    var activeTab by remember(existingNote?.id, draft) {
        mutableStateOf(
            if ((existingNote?.extractedText ?: draft?.extractedText).isNullOrBlank()) EditorTab.EDIT
            else EditorTab.HIGHLIGHT
        )
    }
    var activeHighlightColor by remember { mutableStateOf(HighlightColor.YELLOW) }
    var expandPhoto by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isFindingTerms by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var showCreateSubjectDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Interactive tap-word selection state
    var selectionStartWordIdx by remember { mutableIntStateOf(-1) }
    var pendingHighlightDialog by remember { mutableStateOf<Triple<String, String, Pair<Int, Int>>?>(null) }

    val noteDefinitions = remember(workspace.definitions, currentNoteId) {
        if (currentNoteId == null) emptyList()
        else workspace.definitions.filter { it.noteId == currentNoteId }
    }

    val photoFile = remember(photoFileName) {
        PhotoStorageHelper.getPhotoFile(context, photoFileName)
    }

    suspend fun ensureNoteSaved(): String? {
        if (subjectId.isBlank()) {
            onShowMessage("Select or create a subject first.")
            return null
        }
        val cleanTitle = title.trim().ifEmpty { "Study Note" }
        val result = onSaveNote(
            currentNoteId,
            subjectId,
            cleanTitle,
            extractedText,
            sourceType,
            tags,
            photoFileName
        )
        return result.fold(
            onSuccess = { savedId ->
                currentNoteId = savedId
                title = cleanTitle
                savedId
            },
            onFailure = { err ->
                onShowMessage(err.localizedMessage ?: "Failed to save note.")
                null
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to notes"
                        )
                    }
                },
                title = {
                    Text(
                        text = if (currentNoteId == null) "New Study Note" else "Edit & Highlight",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    if (currentNoteId != null) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Delete note"
                            )
                        }
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                val savedId = ensureNoteSaved()
                                isSaving = false
                                if (savedId != null) {
                                    onShowMessage("Note saved")
                                }
                            }
                        },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("save_note_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Save")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 4.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Subject Assignment
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Subject",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        workspace.subjects.forEach { subj ->
                            FilterChip(
                                selected = subj.id == subjectId,
                                onClick = { subjectId = subj.id },
                                label = { Text(subj.name) }
                            )
                        }
                        AssistChip(
                            onClick = { showCreateSubjectDialog = true },
                            label = { Text("+ Subject") },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }

            // 2. Tagged Photo Card (if present)
            if (photoFile != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandPhoto = !expandPhoto }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    AsyncImage(
                                        model = photoFile,
                                        contentDescription = "Captured page thumbnail",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                    Column {
                                        Text(
                                            text = "Source Page Photo",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = if (expandPhoto) "Tap to collapse" else "Tap to inspect photo",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (expandPhoto) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = if (expandPhoto) "Collapse photo" else "Expand photo"
                                )
                            }

                            if (expandPhoto) {
                                Spacer(Modifier.height(10.dp))
                                AsyncImage(
                                    model = photoFile,
                                    contentDescription = "Full captured page photo",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(260.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                )
                            }
                        }
                    }
                }
            }

            // 3. Tags Row (tags apply to note & attached photo)
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tags.forEach { tag ->
                        TagPill(
                            tag = tag,
                            onRemove = { tags = tags - tag }
                        )
                    }
                    if (tags.size < 10) {
                        AssistChip(
                            onClick = { showAddTagDialog = true },
                            label = { Text("+ Tag") },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Tag,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }

            // 4. Mode Switch: Edit Text vs Highlight Definitions
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    EditorTab.entries.forEachIndexed { idx, tab ->
                        SegmentedButton(
                            selected = activeTab == tab,
                            onClick = {
                                selectionStartWordIdx = -1
                                activeTab = tab
                            },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = idx,
                                count = EditorTab.entries.size
                            ),
                            icon = {
                                Icon(
                                    imageVector = if (tab == EditorTab.EDIT) Icons.Rounded.Edit
                                    else Icons.Rounded.BorderColor,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        ) {
                            Text(tab.label)
                        }
                    }
                }
            }

            if (activeTab == EditorTab.EDIT) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = title,
                                onValueChange = { if (it.length <= 120) title = it },
                                label = { Text("Note Title") },
                                placeholder = { Text("e.g., Chapter 4: Cell Respiration") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("note_title_input")
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                NoteSourceType.entries.forEach { src ->
                                    FilterChip(
                                        selected = sourceType == src,
                                        onClick = { sourceType = src },
                                        label = { Text(src.label) }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = extractedText,
                                onValueChange = { if (it.length <= 20000) extractedText = it },
                                label = { Text("Editable Note Text") },
                                placeholder = {
                                    Text("Extracted text from your printed page or handwriting appears here. You can edit anything freely.")
                                },
                                textStyle = StudyReadingStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                                minLines = 8,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("note_text_input")
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            isFindingTerms = true
                                            onSuggestDefinitions(extractedText).fold(
                                                onSuccess = { list ->
                                                    suggestions = list
                                                    activeTab = EditorTab.HIGHLIGHT
                                                    onShowMessage("Found ${list.size} key terms to highlight")
                                                },
                                                onFailure = { err ->
                                                    onShowMessage(err.localizedMessage ?: "Could not detect definitions")
                                                }
                                            )
                                            isFindingTerms = false
                                        }
                                    },
                                    enabled = !isFindingTerms && extractedText.isNotBlank(),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (isFindingTerms) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text("Find Key Terms")
                                }

                                Button(
                                    onClick = { activeTab = EditorTab.HIGHLIGHT },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.BorderColor,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Highlight Mode")
                                }
                            }
                        }
                    }
                }
            } else {
                // HIGHLIGHT DEFINITIONS MODE
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Highlighter Pen",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    HighlightColor.entries.forEach { color ->
                                        val swatch = color.toMarkerColor()
                                        val selected = activeHighlightColor == color
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(swatch)
                                                .border(
                                                    width = if (selected) 2.5.dp else 1.dp,
                                                    color = if (selected) MaterialTheme.colorScheme.onSurface
                                                    else MaterialTheme.colorScheme.outline,
                                                    shape = CircleShape
                                                )
                                                .clickable { activeHighlightColor = color }
                                                .testTag("highlighter_${color.name.lowercase()}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (selected) {
                                                Icon(
                                                    Icons.Rounded.Check,
                                                    contentDescription = "${color.label} highlighter",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (selectionStartWordIdx == -1)
                                            "Tap a start word, then an end word to highlight any phrase."
                                        else
                                            "Start word selected — now tap the end word!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (selectionStartWordIdx != -1) {
                                        TextButton(onClick = { selectionStartWordIdx = -1 }) {
                                            Text("Clear")
                                        }
                                    }
                                }
                            }

                            if (extractedText.isBlank()) {
                                Text(
                                    text = "No text in this note yet. Switch to Edit Text to type or paste study notes.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                InteractiveHighlightReader(
                                    text = extractedText,
                                    definitions = noteDefinitions,
                                    selectionStartWordIdx = selectionStartWordIdx,
                                    activeColor = activeHighlightColor,
                                    onWordTapped = { wordIdx, words ->
                                        if (selectionStartWordIdx == -1) {
                                            selectionStartWordIdx = wordIdx
                                        } else {
                                            val startIdx = minOf(selectionStartWordIdx, wordIdx)
                                            val endIdx = maxOf(selectionStartWordIdx, wordIdx)
                                            val startOffset = words[startIdx].start
                                            val endOffset = words[endIdx].end
                                            val selectedPhrase = extractedText.substring(startOffset, endOffset).trim()
                                            selectionStartWordIdx = -1
                                            if (selectedPhrase.isNotEmpty()) {
                                                pendingHighlightDialog = Triple(
                                                    selectedPhrase.take(60),
                                                    selectedPhrase,
                                                    startOffset to endOffset
                                                )
                                            }
                                        }
                                    }
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    pendingHighlightDialog = Triple("", "", -1 to -1)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("manual_highlight_button")
                            ) {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Add Definition Manually")
                            }
                        }
                    }
                }

                // Suggested Key Terms strip (from photo scan or AI Find Key Terms)
                if (suggestions.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Suggested Terms (Tap to Highlight)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            suggestions.forEach { item ->
                                val alreadySaved = noteDefinitions.any {
                                    it.term.equals(item.term, ignoreCase = true)
                                }
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (alreadySaved) MaterialTheme.colorScheme.secondary
                                        else MaterialTheme.colorScheme.outline
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !alreadySaved) {
                                            val idx = extractedText.indexOf(item.term, ignoreCase = true)
                                            val range = if (idx >= 0) idx to (idx + item.term.length) else -1 to -1
                                            pendingHighlightDialog = Triple(item.term, item.definition, range)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.term,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = item.definition,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = if (alreadySaved) "Highlighted" else "+ Highlight",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (alreadySaved) MaterialTheme.colorScheme.secondary
                                            else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Saved Highlights in this Note
            if (noteDefinitions.isNotEmpty()) {
                item {
                    Text(
                        text = "Highlighted Definitions (${noteDefinitions.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(noteDefinitions, key = { it.id }) { def ->
                    val marker = def.resolvedColor().toMarkerColor()
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(6.dp)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(marker)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = marker
                                ) {
                                    Text(
                                        text = def.term,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = def.definition,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            IconButton(onClick = { onDeleteDefinition(def.id) }) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "Remove highlight ${def.term}"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingHighlightDialog?.let { (initialTerm, initialDef, range) ->
        HighlightDefinitionDialog(
            initialTerm = initialTerm,
            initialDefinition = initialDef,
            initialColor = activeHighlightColor,
            onDismiss = { pendingHighlightDialog = null },
            onSave = { term, definition, color ->
                pendingHighlightDialog = null
                activeHighlightColor = color
                scope.launch {
                    val noteId = ensureNoteSaved() ?: return@launch
                    onAddDefinition(
                        subjectId,
                        noteId,
                        term,
                        definition,
                        color,
                        range.first,
                        range.second,
                        tags
                    )
                }
            }
        )
    }

    if (showAddTagDialog) {
        var newTag by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddTagDialog = false },
            title = { Text("Add Tag to Note & Photo") },
            text = {
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { if (it.length <= 32) newTag = it },
                    label = { Text("Tag name") },
                    placeholder = { Text("e.g., Exam 1, Chapter 3, Lab") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val clean = newTag.trim()
                    if (clean.isNotEmpty() && !tags.contains(clean)) {
                        tags = (tags + clean).take(10)
                    }
                    showAddTagDialog = false
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTagDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCreateSubjectDialog) {
        SubjectDialog(
            onDismiss = { showCreateSubjectDialog = false },
            onSave = { name, color, icon ->
                showCreateSubjectDialog = false
                scope.launch {
                    onCreateSubjectSync(name, color, icon).fold(
                        onSuccess = { newSubId ->
                            subjectId = newSubId
                            onShowMessage("Subject \"$name\" created")
                        },
                        onFailure = { err ->
                            onShowMessage(err.localizedMessage ?: "Failed to create subject")
                        }
                    )
                }
            }
        )
    }

    if (showDeleteConfirm && currentNoteId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this note?") },
            text = { Text("This note and its highlighted definitions will be removed.") },
            confirmButton = {
                Button(onClick = {
                    showDeleteConfirm = false
                    onDeleteNote(currentNoteId!!, onBack)
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Suppress("DEPRECATION")
@Composable
private fun InteractiveHighlightReader(
    text: String,
    definitions: List<KeyDefinition>,
    selectionStartWordIdx: Int,
    activeColor: HighlightColor,
    onWordTapped: (wordIdx: Int, words: List<WordSpan>) -> Unit
) {
    val words = remember(text) {
        Regex("\\S+").findAll(text).map { match ->
            WordSpan(
                start = match.range.first,
                end = match.range.last + 1,
                word = match.value
            )
        }.toList()
    }

    val markerColors = mapOf(
        HighlightColor.YELLOW to HighlightColor.YELLOW.toMarkerColor(),
        HighlightColor.MINT to HighlightColor.MINT.toMarkerColor(),
        HighlightColor.CORAL to HighlightColor.CORAL.toMarkerColor(),
        HighlightColor.SKY to HighlightColor.SKY.toMarkerColor()
    )
    val activeSelectionBg = activeColor.toMarkerColor().copy(alpha = 0.7f)
    val onSurface = MaterialTheme.colorScheme.onSurface

    val annotated: AnnotatedString = remember(text, definitions, selectionStartWordIdx, words, markerColors) {
        buildAnnotatedString {
            append(text)
            addStyle(SpanStyle(color = onSurface), 0, text.length)

            definitions.forEach { def ->
                val start = def.startOffset.toInt()
                val end = def.endOffset.toInt()
                val bg = markerColors[def.resolvedColor()] ?: activeSelectionBg
                if (start in 0 until text.length && end in (start + 1)..text.length) {
                    addStyle(
                        SpanStyle(background = bg, fontWeight = FontWeight.SemiBold),
                        start,
                        end
                    )
                } else if (def.term.isNotBlank()) {
                    val idx = text.indexOf(def.term, ignoreCase = true)
                    if (idx >= 0) {
                        addStyle(
                            SpanStyle(background = bg, fontWeight = FontWeight.SemiBold),
                            idx,
                            (idx + def.term.length).coerceAtMost(text.length)
                        )
                    }
                }
            }

            if (selectionStartWordIdx in words.indices) {
                val w = words[selectionStartWordIdx]
                addStyle(
                    SpanStyle(background = activeSelectionBg, fontWeight = FontWeight.Bold),
                    w.start,
                    w.end
                )
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
    ) {
        ClickableText(
            text = annotated,
            style = StudyReadingStyle,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            onClick = { offset ->
                val tappedWordIdx = words.indexOfFirst { offset in it.start..it.end }
                if (tappedWordIdx >= 0) {
                    onWordTapped(tappedWordIdx, words)
                }
            }
        )
    }
}

@Composable
private fun HighlightDefinitionDialog(
    initialTerm: String,
    initialDefinition: String,
    initialColor: HighlightColor,
    onDismiss: () -> Unit,
    onSave: (term: String, definition: String, color: HighlightColor) -> Unit
) {
    var term by remember { mutableStateOf(initialTerm) }
    var definition by remember { mutableStateOf(initialDefinition) }
    var color by remember { mutableStateOf(initialColor) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Highlight Key Definition") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = term,
                    onValueChange = {
                        if (it.length <= 120) term = it
                        error = null
                    },
                    label = { Text("Key Term") },
                    placeholder = { Text("e.g., Photosynthesis") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("highlight_term_input")
                )

                OutlinedTextField(
                    value = definition,
                    onValueChange = {
                        if (it.length <= 1000) definition = it
                        error = null
                    },
                    label = { Text("Definition / Explanation") },
                    placeholder = { Text("Clear summary for your reviewer") },
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("highlight_definition_input")
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Color:", style = MaterialTheme.typography.labelLarge)
                    HighlightColor.entries.forEach { option ->
                        val swatch = option.toMarkerColor()
                        val selected = color == option
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { color = option },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = option.label,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                if (error != null) {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val t = term.trim()
                    val d = definition.trim()
                    when {
                        t.isEmpty() -> error = "Enter a key term"
                        d.isEmpty() -> error = "Enter the definition"
                        else -> onSave(t, d, color)
                    }
                },
                modifier = Modifier.testTag("save_highlight_button")
            ) {
                Text("Highlight & Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
