package com.example.voicejournal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.voicejournal.data.Category
import com.example.voicejournal.di.Injector
import com.example.voicejournal.ui.theme.VoicejournalTheme

class EditCategoryActivity : ComponentActivity() {

    private val categoryId by lazy { intent.getIntExtra(EXTRA_CATEGORY_ID, -1) }
     private val highestOrderIndex by lazy { intent.getIntExtra(EXTRA_HIGHEST_ORDER_INDEX, 100) }

    private val viewModel: EditCategoryViewModel by viewModels {
        EditCategoryViewModelFactory(Injector.provideJournalRepository(this), categoryId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoicejournalTheme {
                val category by viewModel.category.collectAsState()

                // Handle the case where a category is being edited but not yet loaded
                if (categoryId != -1 && category == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    EditCategoryScreen(
                        initialCategory = category,
                        onSave = { name, aliases, showAll ->
                            val orderIndex = category?.orderIndex ?: (highestOrderIndex + 1)
                            viewModel.saveCategory(name, aliases, showAll, orderIndex)
                            finish()
                        },
                        onNavigateUp = { finish() }
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_CATEGORY_ID = "extra_category_id"
        private const val EXTRA_HIGHEST_ORDER_INDEX = "extra_highest_order_index"

        fun newIntent(context: Context, categoryId: Int? = null, highestOrderIndex: Int = 0): Intent {
            return Intent(context, EditCategoryActivity::class.java).apply {
                putExtra(EXTRA_CATEGORY_ID, categoryId ?: -1)
                putExtra(EXTRA_HIGHEST_ORDER_INDEX, highestOrderIndex)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCategoryScreen(
    initialCategory: Category?,
    onSave: (String, String, Boolean) -> Unit,
    onNavigateUp: () -> Unit
) {
    var name by remember { mutableStateOf(initialCategory?.category ?: "") }
    var aliases by remember { mutableStateOf(initialCategory?.aliases ?: "") }
    var showAll by remember { mutableStateOf(initialCategory?.showAll ?: false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initialCategory == null) "Add Category" else "Edit Category") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (name.isNotBlank()) {
                    onSave(name, aliases, showAll)
                }
            }) {
                Icon(Icons.Default.Done, contentDescription = "Save Category")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Category Name") },
                modifier = Modifier.fillMaxWidth(),
                // A category name should not be changed, as it could orphan relations
                // However, for a new category it must be enabled.
                enabled = initialCategory == null
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = aliases,
                onValueChange = { aliases = it },
                label = { Text("Aliases (comma-separated)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(checked = showAll, onCheckedChange = { showAll = it })
                Text("Show all entries in this category")
            }
        }
    }
}