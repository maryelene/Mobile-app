package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.HighlightColor
import com.example.model.NoteSourceType
import com.example.model.Subject
import com.example.model.SubjectColor
import com.example.model.SubjectIcon
import com.example.ui.theme.HighlightCoral
import com.example.ui.theme.HighlightCoralDark
import com.example.ui.theme.HighlightMint
import com.example.ui.theme.HighlightMintDark
import com.example.ui.theme.HighlightSky
import com.example.ui.theme.HighlightSkyDark
import com.example.ui.theme.HighlightYellow
import com.example.ui.theme.HighlightYellowDark
import com.example.ui.theme.SubjectAmber
import com.example.ui.theme.SubjectCobalt
import com.example.ui.theme.SubjectCoral
import com.example.ui.theme.SubjectLavender
import com.example.ui.theme.SubjectSage
import com.example.ui.theme.SubjectTeal

fun SubjectColor.toAccentColor(): Color = when (this) {
    SubjectColor.COBALT -> SubjectCobalt
    SubjectColor.SAGE -> SubjectSage
    SubjectColor.AMBER -> SubjectAmber
    SubjectColor.CORAL -> SubjectCoral
    SubjectColor.LAVENDER -> SubjectLavender
    SubjectColor.TEAL -> SubjectTeal
}

fun SubjectIcon.toImageVector(): ImageVector = when (this) {
    SubjectIcon.SCIENCE -> Icons.Rounded.Science
    SubjectIcon.HISTORY -> Icons.Rounded.Public
    SubjectIcon.MATH -> Icons.Rounded.Calculate
    SubjectIcon.LITERATURE -> Icons.Rounded.AutoStories
    SubjectIcon.LANGUAGE -> Icons.Rounded.Translate
    SubjectIcon.GENERAL -> Icons.Rounded.Folder
}

fun NoteSourceType.toIcon(): ImageVector = when (this) {
    NoteSourceType.PRINTED -> Icons.Rounded.HistoryEdu
    NoteSourceType.HANDWRITING -> Icons.Rounded.Draw
    NoteSourceType.TYPED -> Icons.Rounded.Keyboard
}

@Composable
fun HighlightColor.toMarkerColor(): Color {
    val isDark = isSystemInDarkTheme()
    return when (this) {
        HighlightColor.YELLOW -> if (isDark) HighlightYellowDark else HighlightYellow
        HighlightColor.MINT -> if (isDark) HighlightMintDark else HighlightMint
        HighlightColor.CORAL -> if (isDark) HighlightCoralDark else HighlightCoral
        HighlightColor.SKY -> if (isDark) HighlightSkyDark else HighlightSky
    }
}

@Composable
fun SourceTypeBadge(sourceType: NoteSourceType, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = sourceType.toIcon(),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = sourceType.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TagPill(
    tag: String,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    ) {
        Row(
            modifier = Modifier.padding(
                start = 10.dp,
                end = if (onRemove != null) 4.dp else 10.dp,
                top = 4.dp,
                bottom = 4.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "#$tag",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Remove tag $tag",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubjectDialog(
    initialSubject: Subject? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, color: SubjectColor, icon: SubjectIcon) -> Unit
) {
    var name by remember { mutableStateOf(initialSubject?.name ?: "") }
    var selectedColor by remember {
        mutableStateOf(initialSubject?.resolvedColor() ?: SubjectColor.COBALT)
    }
    var selectedIcon by remember {
        mutableStateOf(initialSubject?.resolvedIcon() ?: SubjectIcon.SCIENCE)
    }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialSubject == null) "New Subject" else "Edit Subject",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        if (it.length <= 50) name = it
                        error = null
                    },
                    label = { Text("Subject name") },
                    placeholder = { Text("e.g., Biology, World History") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { msg -> { Text(msg) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("subject_name_input")
                )

                Text(
                    text = "Notebook Color",
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SubjectColor.entries.forEach { colorOption ->
                        val accent = colorOption.toAccentColor()
                        val selected = selectedColor == colorOption
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(accent)
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = colorOption }
                                .testTag("subject_color_${colorOption.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "${colorOption.label} selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Icon",
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SubjectIcon.entries.forEach { iconOption ->
                        val selected = selectedIcon == iconOption
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) selectedColor.toAccentColor().copy(alpha = 0.16f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (selected) BorderStroke(1.5.dp, selectedColor.toAccentColor()) else null,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedIcon = iconOption }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = iconOption.toImageVector(),
                                    contentDescription = iconOption.label,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (selected) selectedColor.toAccentColor()
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = iconOption.label,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        error = "Enter a subject name"
                    } else {
                        onSave(trimmed, selectedColor, selectedIcon)
                    }
                },
                modifier = Modifier.testTag("save_subject_button")
            ) {
                Text(if (initialSubject == null) "Create" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
