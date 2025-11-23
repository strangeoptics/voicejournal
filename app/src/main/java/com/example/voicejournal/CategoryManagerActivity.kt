package com.example.voicejournal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.voicejournal.data.Category
import com.example.voicejournal.ui.theme.VoicejournalTheme

class CategoryManagerActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(applicationContext, getSharedPreferences(MainViewModel.PREFS_NAME, MODE_PRIVATE))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoicejournalTheme {
                val categories by viewModel.categoriesFlow.collectAsState()
                var showEditDialog by remember { mutableStateOf(false) }
                var categoryToEdit by remember { mutableStateOf<Category?>(null) }


                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Manage Categories") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(onClick = {
                            categoryToEdit = null
                            showEditDialog = true
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Category")
                        }
                    }
                ) { padding ->
                    CategoryManagerScreen(
                        modifier = Modifier.padding(padding),
                        categories = categories,
                        onCategoryLongClick = { category ->
                            categoryToEdit = category
                            showEditDialog = true
                        },
                        onDeleteCategory = { category ->
                            viewModel.deleteCategory(category)
                        },
                        onMoveCategory = { category, moveUp ->
                            viewModel.moveCategory(category, moveUp)
                        }
                    )
                }

                if (showEditDialog) {
                    EditCategoryDialog(
                        onDismiss = {
                            showEditDialog = false
                            categoryToEdit = null
                        },
                        onSave = { category, aliases, showAll ->
                            viewModel.addOrUpdateCategory(category, aliases, showAll)
                            showEditDialog = false
                            categoryToEdit = null
                        },
                        initialCategory = categoryToEdit
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagerScreen(
    modifier: Modifier = Modifier,
    categories: List<Category>,
    onCategoryLongClick: (Category) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onMoveCategory: (Category, Boolean) -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories, key = { it.category }) { category ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = {
                    if (it == SwipeToDismissBoxValue.StartToEnd) {
                        onDeleteCategory(category.category)
                        true
                    } else {
                        false
                    }
                }
            )
            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {
                    val color = when (dismissState.dismissDirection) {
                        SwipeToDismissBoxValue.StartToEnd -> Color.Red
                        else -> Color.Transparent
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(color)
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.White
                        )
                    }
                }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { /* No action on simple click */ },
                            onLongClick = { onCategoryLongClick(category) }
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = category.category,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = category.aliases,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (category.showAll) {
                            Text("All", modifier = Modifier.padding(start = 8.dp))
                        }
                        Column {
                            IconButton(onClick = { onMoveCategory(category, true) }, enabled = categories.first() != category) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up")
                            }
                            IconButton(onClick = { onMoveCategory(category, false) }, enabled = categories.last() != category) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditCategoryDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit,
    initialCategory: Category?
) {
    var category by remember { mutableStateOf(initialCategory?.category ?: "") }
    var aliases by remember { mutableStateOf(initialCategory?.aliases ?: "") }
    var showAll by remember { mutableStateOf(initialCategory?.showAll ?: false) }

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(if (initialCategory == null) "Add New Category" else "Edit Category")
                TextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    enabled = initialCategory == null
                )
                TextField(
                    value = aliases,
                    onValueChange = { aliases = it },
                    label = { Text("Aliases (comma-separated)") }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = showAll,
                        onCheckedChange = { showAll = it }
                    )
                    Text("Show all")
                }
                Button(onClick = {
                    if (category.isNotBlank() && aliases.isNotBlank()) {
                        onSave(category, aliases, showAll)
                    }
                }) {
                    Text("Save")
                }
            }
        }
    }
}