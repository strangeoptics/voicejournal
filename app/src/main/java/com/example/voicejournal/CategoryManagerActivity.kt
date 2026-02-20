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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.dp
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
                val context = LocalContext.current

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
                            val highestIndex = categories.maxOfOrNull { it.orderIndex } ?: 0
                            val intent = EditCategoryActivity.newIntent(context, null, highestIndex)
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Category")
                        }
                    }
                ) { padding ->
                    CategoryManagerScreen(
                        modifier = Modifier.padding(padding),
                        categories = categories,
                        onCategoryLongClick = { category ->
                            val intent = EditCategoryActivity.newIntent(context, category.id)
                            context.startActivity(intent)
                        },
                        onDeleteCategory = { category ->
                            viewModel.deleteCategory(category)
                        },
                        onMoveCategory = { category, moveUp ->
                            viewModel.moveCategory(category, moveUp)
                        }
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
    onDeleteCategory: (Category) -> Unit,
    onMoveCategory: (Category, Boolean) -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories, key = { it.id }) { category ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = {
                    if (it == SwipeToDismissBoxValue.StartToEnd) {
                        onDeleteCategory(category)
                        true
                    } else {
                        false
                    }
                },
                positionalThreshold = { it * 0.60f }
            )

            // Reset dismiss state when the category is re-composed (e.g. after undo)
            LaunchedEffect(category) {
                if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                    dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                }
            }

            val currentViewConfiguration = LocalViewConfiguration.current
            val customViewConfiguration = remember(currentViewConfiguration) {
                object : ViewConfiguration by currentViewConfiguration {
                    override val touchSlop: Float
                        get() = currentViewConfiguration.touchSlop * 2.5f
                }
            }

            CompositionLocalProvider(LocalViewConfiguration provides customViewConfiguration) {
                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromEndToStart = false,
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
}