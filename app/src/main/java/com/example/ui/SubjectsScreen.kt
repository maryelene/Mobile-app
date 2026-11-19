package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.Subject
import com.example.model.SubjectColor
import com.example.model.SubjectIcon

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubjectsScreen(
    workspace: EduFlowWorkspace,
    onCreateSubject: (String, SubjectColor, SubjectIcon) -> Unit,
    onUpdateSubject: (String, String, SubjectColor, SubjectIcon) -> Unit,
    onDeleteSubject: (String) -> Unit,
    onOpenSubjectNotes: (String) -> Unit,
    onOpenSubjectReviewer: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingSubject by remember { mutableStateOf<Subject?>(null) }
    var deletingSubject by remember { mutableStateOf<Subject?>(null) }

    val quickStarters = remember {
        listOf(
            Triple("Biology", SubjectColor.SAGE, SubjectIcon.SCIENCE),
            Triple("World History", SubjectColor.AMBER, SubjectIcon.HISTORY),
            Triple("Algebra", SubjectColor.COBALT, SubjectIcon.MATH),
            Triple("Literature", SubjectColor.CORAL, SubjectIcon.LITERATURE)
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("New Subject") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_subject_fab")
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 270.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 88.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Your Study Subjects",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Organize class notes, tagged photos, and highlighted definitions into tidy reviewers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (workspace.subjects.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.widthIn(max = 520.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AsyncImage(
                                    model = R.drawable.img_empty_notes,
                                    contentDescription = "Notebook waiting for your first subject",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(170.dp)
                                        .clip(RoundedCornerShape(18.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Text(
                                    text = "Start your first subject notebook",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = "Create a subject or pick a starter below to group your scanned pages and definitions.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    quickStarters.forEach { (name, color, icon) ->
                                        AssistChip(
                                            onClick = { onCreateSubject(name, color, icon) },
                                            label = { Text("+ $name") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = icon.toImageVector(),
                                                    contentDescription = null,
                                                    tint = color.toAccentColor(),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        )
                                    }
                                }
                                Button(
                                    onClick = { showCreateDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Create Custom Subject")
                                }
                            }
                        }
                    }
                }
            } else {
                items(workspace.subjects, key = { it.id }) { subject ->
                    val noteCount = workspace.notes.count { it.subjectId == subject.id }
                    val photoCount = workspace.notes.count {
                        it.subjectId == subject.id && !it.photoFileName.isNullOrBlank()
                    }
                    val defCount = workspace.definitions.count { it.subjectId == subject.id }
                    val accent = subject.resolvedColor().toAccentColor()

                    var menuExpanded by remember { mutableStateOf(false) }

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSubjectReviewer(subject.id) }
                            .testTag("subject_card_${subject.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = accent.copy(alpha = 0.16f)
                                    ) {
                                        Box(
                                            modifier = Modifier.size(44.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = subject.resolvedIcon().toImageVector(),
                                                contentDescription = subject.name,
                                                tint = accent
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = subject.name,
                                            style = MaterialTheme.typography.titleLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "$noteCount notes • $defCount definitions",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Box {
                                    IconButton(
                                        onClick = { menuExpanded = true },
                                        modifier = Modifier.testTag("subject_menu_${subject.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MoreVert,
                                            contentDescription = "Subject options"
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Edit subject") },
                                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                            onClick = {
                                                menuExpanded = false
                                                editingSubject = subject
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete subject") },
                                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                            onClick = {
                                                menuExpanded = false
                                                deletingSubject = subject
                                            }
                                        )
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatPill(
                                    icon = Icons.Rounded.Style,
                                    label = "$defCount terms"
                                )
                                StatPill(
                                    icon = Icons.Rounded.PhotoCamera,
                                    label = "$photoCount photos"
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = { onOpenSubjectNotes(subject.id) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Notes ($noteCount)")
                                }
                                Button(
                                    onClick = { onOpenSubjectReviewer(subject.id) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Review", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        SubjectDialog(
            onDismiss = { showCreateDialog = false },
            onSave = { name, color, icon ->
                showCreateDialog = false
                onCreateSubject(name, color, icon)
            }
        )
    }

    editingSubject?.let { subject ->
        SubjectDialog(
            initialSubject = subject,
            onDismiss = { editingSubject = null },
            onSave = { name, color, icon ->
                editingSubject = null
                onUpdateSubject(subject.id, name, color, icon)
            }
        )
    }

    deletingSubject?.let { subject ->
        AlertDialog(
            onDismissRequest = { deletingSubject = null },
            title = { Text("Delete \"${subject.name}\"?") },
            text = {
                Text("This removes the subject along with its saved notes and highlighted definitions.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        deletingSubject = null
                        onDeleteSubject(subject.id)
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSubject = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
