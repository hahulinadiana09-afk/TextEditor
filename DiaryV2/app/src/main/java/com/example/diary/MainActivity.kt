
package com.example.diary

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.Room
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = DiaryDatabase.get(applicationContext)
        val dao = database.diaryDao()

        setContent {
            DiaryApp(dao = dao)
        }
    }
}

private val LightBackground = Color(0xFFFFF9F7)
private val LightText = Color(0xFF493B3B)
private val LightAccent = Color(0xFFB87985)
private val DarkBackground = Color(0xFF242124)
private val DarkText = Color(0xFFF0E7E8)
private val DarkAccent = Color(0xFFD69AA6)

@Composable
fun DiaryApp(dao: DiaryDao) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var darkTheme by remember {
        mutableStateOf(
            context.getSharedPreferences("diary_settings", Context.MODE_PRIVATE)
                .getBoolean("dark_theme", false)
        )
    }

    val documents by dao.observeAll().collectAsState(initial = emptyList())

    var selectedId by remember { mutableStateOf<Long?>(null) }
    var initialized by remember { mutableStateOf(false) }
    var showManager by remember { mutableStateOf(false) }

    val selected = documents.firstOrNull { it.id == selectedId }

    val background = if (darkTheme) DarkBackground else LightBackground
    val textColor = if (darkTheme) DarkText else LightText
    val accent = if (darkTheme) DarkAccent else LightAccent

    val visibleDocuments = documents.filter {
        it.isNamed || it.content.isNotBlank()
    }

    LaunchedEffect(Unit) {
        dao.deleteEmptyUnnamedDrafts()
        val newId = dao.insert(DiaryDocument())
        selectedId = newId
        initialized = true
    }

    MaterialTheme(
        colorScheme = if (darkTheme) {
            darkColorScheme(
                primary = accent,
                background = background,
                surface = background,
                onBackground = textColor,
                onSurface = textColor
            )
        } else {
            lightColorScheme(
                primary = accent,
                background = background,
                surface = background,
                onBackground = textColor,
                onSurface = textColor
            )
        }
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = background
        ) {
            if (!initialized || (selected == null && !showManager)) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = accent)
                }
            } else if (showManager) {
                DocumentManager(
                    documents = visibleDocuments,
                    darkTheme = darkTheme,
                    background = background,
                    textColor = textColor,
                    accent = accent,
                    onThemeToggle = {
                        darkTheme = !darkTheme
                        context.getSharedPreferences(
                            "diary_settings",
                            Context.MODE_PRIVATE
                        ).edit().putBoolean("dark_theme", darkTheme).apply()
                    },
                    onOpen = {
                        selectedId = it
                        showManager = false
                    },
                    onNew = {
                        scope.launch {
                            val existingDraft = documents.firstOrNull {
                                !it.isNamed && it.content.isBlank()
                            }

                            selectedId = existingDraft?.id
                                ?: dao.insert(DiaryDocument())

                            showManager = false
                        }
                    },
                    onDelete = {
                        scope.launch {
                            dao.delete(it)
                            if (selectedId == it) {
                                selectedId = null
                            }
                        }
                    },
                    onRename = { id, title ->
                        scope.launch {
                            val document = dao.getById(id)
                            if (document != null) {
                                dao.update(
                                    id = id,
                                    title = title,
                                    content = document.content,
                                    modifiedAt = System.currentTimeMillis(),
                                    isNamed = true
                                )
                            }
                        }
                    }
                )
            } else {
                selected?.let { document ->
                    DiaryEditor(
                        document = document,
                        darkTheme = darkTheme,
                        background = background,
                        textColor = textColor,
                        accent = accent,
                        onThemeToggle = {
                            darkTheme = !darkTheme
                            context.getSharedPreferences(
                                "diary_settings",
                                Context.MODE_PRIVATE
                            ).edit().putBoolean("dark_theme", darkTheme).apply()
                        },
                        onDocuments = {
                            scope.launch {
                                dao.update(
                                    id = document.id,
                                    title = document.title,
                                    content = document.content,
                                    modifiedAt = System.currentTimeMillis(),
                                    isNamed = document.isNamed
                                )
                                showManager = true
                            }
                        },
                        onContentChange = { newText ->
                            scope.launch {
                                dao.update(
                                    id = document.id,
                                    title = document.title,
                                    content = newText,
                                    modifiedAt = System.currentTimeMillis(),
                                    isNamed = document.isNamed
                                )
                            }
                        },
                        onSave = { title ->
                            scope.launch {
                                dao.update(
                                    id = document.id,
                                    title = title,
                                    content = document.content,
                                    modifiedAt = System.currentTimeMillis(),
                                    isNamed = true
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DocumentManager(
    documents: List<DiaryDocument>,
    darkTheme: Boolean,
    background: Color,
    textColor: Color,
    accent: Color,
    onThemeToggle: () -> Unit,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onDelete: (Long) -> Unit,
    onRename: (Long, String) -> Unit
) {
    var renameId by remember { mutableStateOf<Long?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteId by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Documents",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            TextButton(onClick = onThemeToggle) {
                Text(if (darkTheme) "☀ Light" else "☾ Dark", color = accent)
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onNew,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = accent)
        ) {
            Text("+ New document", color = Color.White)
        }

        Spacer(Modifier.height(16.dp))

        if (documents.isEmpty()) {
            Text(
                "No saved documents yet.",
                color = textColor
            )
        }

        documents.forEach { document ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                colors = CardDefaults.cardColors(containerColor = background)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        document.title.ifBlank { "Untitled draft" },
                        color = textColor,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        document.content.take(100).ifBlank { "Empty document" },
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )

                    Row {
                        TextButton(onClick = { onOpen(document.id) }) {
                            Text("Open", color = accent)
                        }
                        TextButton(onClick = {
                            renameId = document.id
                            renameText = document.title
                        }) {
                            Text("Rename", color = accent)
                        }
                        TextButton(onClick = {
                            deleteId = document.id
                        }) {
                            Text("Delete", color = accent)
                        }
                    }
                }
            }
        }
    }

    if (renameId != null) {
        AlertDialog(
            onDismissRequest = { renameId = null },
            title = { Text("Rename document") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    renameId?.let { onRename(it, renameText) }
                    renameId = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameId = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (deleteId != null) {
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Delete document?") },
            text = { Text("This document will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteId?.let(onDelete)
                    deleteId = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteId = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DiaryEditor(
    document: DiaryDocument,
    darkTheme: Boolean,
    background: Color,
    textColor: Color,
    accent: Color,
    onThemeToggle: () -> Unit,
    onDocuments: () -> Unit,
    onContentChange: (String) -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember(document.id) { mutableStateOf(document.content) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var titleText by remember(document.id) { mutableStateOf(document.title) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(text, document.id) {
        delay(650)
        onContentChange(text)
    }

    val wordCount = text.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .size

    val characterCount = text.codePointCount(0, text.length)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDocuments) {
                Text("Documents", color = accent)
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "My Diary",
                    color = textColor,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            TextButton(onClick = { onThemeToggle() }) {
                Text(if (darkTheme) "☀" else "☾", color = accent)
            }

            TextButton(onClick = { showSaveDialog = true }) {
                Text("Save", color = accent)
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                val lineSpacing = 30.sp.toPx()
                var y = 22.sp.toPx()

                while (y < size.height) {
                    drawLine(
                        color = accent.copy(alpha = 0.22f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    y += lineSpacing
                }
            }

            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                textStyle = TextStyle(
                    color = textColor,
                    fontSize = 18.sp,
                    lineHeight = 30.sp,
                    fontFamily = FontFamily.Serif
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(accent)
            )
        }

        Text(
            "Words: $wordCount · Characters: $characterCount",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            color = textColor.copy(alpha = 0.7f),
            fontSize = 13.sp
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save document") },
            text = {
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Document name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (titleText.isNotBlank()) {
                        onSave(titleText.trim())
                    }
                    showSaveDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
