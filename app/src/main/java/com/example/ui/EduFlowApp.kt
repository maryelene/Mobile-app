package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.auth.AuthScreen
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.EduFlowRepository
import com.example.data.NoteAiRepository
import com.example.model.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_SUBJECTS = "subjects"
const val ROUTE_NOTES = "notes"
const val ROUTE_REVIEWER = "reviewer"
const val ROUTE_NOTE_EDITOR = "note_editor/{noteId}"
const val DRAFT_NOTE_ID = "draft"

private data class NavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val TopLevelDestinations = listOf(
    NavDestination(
        route = ROUTE_SUBJECTS,
        labelRes = R.string.tab_subjects,
        selectedIcon = Icons.Rounded.AutoStories,
        unselectedIcon = Icons.Outlined.AutoStories
    ),
    NavDestination(
        route = ROUTE_NOTES,
        labelRes = R.string.tab_capture,
        selectedIcon = Icons.Rounded.CameraAlt,
        unselectedIcon = Icons.Outlined.CameraAlt
    ),
    NavDestination(
        route = ROUTE_REVIEWER,
        labelRes = R.string.tab_reviewer,
        selectedIcon = Icons.Rounded.Style,
        unselectedIcon = Icons.Outlined.Style
    )
)

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedEduFlowApp(
                currentUserId = user.uid,
                userDisplayName = user.displayName
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedEduFlowApp(
    currentUserId: String,
    userDisplayName: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: EduFlowViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                EduFlowViewModel(
                    repository = EduFlowRepository(db),
                    aiRepository = NoteAiRepository(),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val workspaceState by viewModel.workspaceState.collectAsStateWithLifecycle()
    val extractionStatus by viewModel.extractionStatus.collectAsStateWithLifecycle()
    val pendingDraft by viewModel.pendingDraft.collectAsStateWithLifecycle()
    val selectedSubjectFilter by viewModel.selectedSubjectFilter.collectAsStateWithLifecycle()
    val selectedReviewerSubjectId by viewModel.selectedReviewerSubjectId.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTopLevelRoute = currentRoute in TopLevelDestinations.map { it.route }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                if (isTopLevelRoute) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        ),
                        title = {
                            Column {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge
                                )
                                if (!userDisplayName.isNullOrBlank()) {
                                    Text(
                                        text = "Hi, ${userDisplayName.substringBefore(" ")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    signOut(
                                        credentialManager = credentialManager,
                                        scope = scope
                                    )
                                },
                                modifier = Modifier.testTag("sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                                    contentDescription = stringResource(R.string.sign_out)
                                )
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (isTopLevelRoute && !useRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        TopLevelDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(ROUTE_SUBJECTS) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTopLevelRoute && useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        TopLevelDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(ROUTE_SUBJECTS) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (val state = workspaceState) {
                        is UiState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator()
                                    Text(
                                        text = "Loading your notebooks…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        is UiState.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "Couldn't load your study workspace",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(onClick = {
                                        signOut(credentialManager, scope)
                                    }) {
                                        Text("Switch Account")
                                    }
                                }
                            }
                        }

                        is UiState.Success -> {
                            val workspace = state.data
                            NavHost(
                                navController = navController,
                                startDestination = ROUTE_SUBJECTS,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                composable(ROUTE_SUBJECTS) {
                                    SubjectsScreen(
                                        workspace = workspace,
                                        onCreateSubject = { name, color, icon ->
                                            viewModel.createSubject(name, color, icon)
                                        },
                                        onUpdateSubject = { id, name, color, icon ->
                                            viewModel.updateSubject(id, name, color, icon)
                                        },
                                        onDeleteSubject = { id ->
                                            viewModel.deleteSubject(id)
                                        },
                                        onOpenSubjectNotes = { subjectId ->
                                            viewModel.setNotesSubjectFilter(subjectId)
                                            navController.navigate(ROUTE_NOTES) {
                                                popUpTo(ROUTE_SUBJECTS) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        onOpenSubjectReviewer = { subjectId ->
                                            viewModel.setReviewerSubjectId(subjectId)
                                            navController.navigate(ROUTE_REVIEWER) {
                                                popUpTo(ROUTE_SUBJECTS) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    )
                                }

                                composable(ROUTE_NOTES) {
                                    NotesScreen(
                                        workspace = workspace,
                                        selectedSubjectId = selectedSubjectFilter,
                                        extractionStatus = extractionStatus,
                                        onSelectSubjectFilter = { viewModel.setNotesSubjectFilter(it) },
                                        onPhotoSelected = { uri ->
                                            viewModel.extractFromPhotoUri(
                                                context = context,
                                                uri = uri,
                                                preferredSubjectId = selectedSubjectFilter,
                                                onDraftReady = {
                                                    navController.navigate("note_editor/$DRAFT_NOTE_ID")
                                                }
                                            )
                                        },
                                        onStartTypedNote = {
                                            viewModel.startNewTypedNoteDraft(selectedSubjectFilter)
                                            navController.navigate("note_editor/$DRAFT_NOTE_ID")
                                        },
                                        onOpenNote = { noteId ->
                                            viewModel.clearPendingDraft()
                                            navController.navigate("note_editor/$noteId")
                                        },
                                        onDismissExtractionError = {
                                            viewModel.clearExtractionError()
                                        }
                                    )
                                }

                                composable(ROUTE_REVIEWER) {
                                    ReviewerScreen(
                                        workspace = workspace,
                                        selectedSubjectId = selectedReviewerSubjectId,
                                        onSelectSubject = { viewModel.setReviewerSubjectId(it) },
                                        onToggleMastered = { viewModel.toggleMastered(it) },
                                        onOpenNote = { noteId ->
                                            viewModel.clearPendingDraft()
                                            navController.navigate("note_editor/$noteId")
                                        }
                                    )
                                }

                                composable(
                                    route = ROUTE_NOTE_EDITOR,
                                    arguments = listOf(
                                        navArgument("noteId") { type = NavType.StringType }
                                    )
                                ) { entry ->
                                    val argNoteId = entry.arguments?.getString("noteId")
                                    val existingNote = if (argNoteId != null && argNoteId != DRAFT_NOTE_ID) {
                                        workspace.notes.firstOrNull { it.id == argNoteId }
                                    } else {
                                        null
                                    }

                                    NoteEditorScreen(
                                        existingNote = existingNote,
                                        draft = if (existingNote == null) pendingDraft else null,
                                        workspace = workspace,
                                        onBack = {
                                            viewModel.clearPendingDraft()
                                            navController.popBackStack()
                                        },
                                        onSaveNote = { id, subjId, title, text, srcType, tags, photo ->
                                            viewModel.saveNote(
                                                existingNoteId = id,
                                                subjectId = subjId,
                                                title = title,
                                                extractedText = text,
                                                sourceType = srcType,
                                                tags = tags,
                                                photoFileName = photo
                                            )
                                        },
                                        onCreateSubjectSync = { name, color, icon ->
                                            viewModel.createSubjectSync(name, color, icon)
                                        },
                                        onSuggestDefinitions = { text ->
                                            viewModel.suggestDefinitionsForText(text)
                                        },
                                        onAddDefinition = { subjId, noteId, term, def, color, start, end, tags ->
                                            viewModel.addDefinition(
                                                subjectId = subjId,
                                                noteId = noteId,
                                                term = term,
                                                definition = def,
                                                highlightColor = color,
                                                startOffset = start,
                                                endOffset = end,
                                                tags = tags
                                            )
                                        },
                                        onDeleteDefinition = { defId ->
                                            viewModel.deleteDefinition(defId)
                                        },
                                        onDeleteNote = { noteId, done ->
                                            viewModel.deleteNote(noteId, done)
                                        },
                                        onShowMessage = { msg -> viewModel.showMessage(msg) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
