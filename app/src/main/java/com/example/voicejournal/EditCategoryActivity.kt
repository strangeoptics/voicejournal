package com.example.voicejournal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width // <- Added this import
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.* 
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
                        onSave = { name, aliases, showAll, color ->
                            val orderIndex = category?.orderIndex ?: (highestOrderIndex + 1)
                            viewModel.saveCategory(name, aliases, showAll, orderIndex, color)
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
    onSave: (String, String, Boolean, String) -> Unit,
    onNavigateUp: () -> Unit
) {
    var name by remember { mutableStateOf(initialCategory?.category ?: "") }
    var aliases by remember { mutableStateOf(initialCategory?.aliases ?: "") }
    var showAll by remember { mutableStateOf(initialCategory?.showAll ?: false) }
    var color by remember { mutableStateOf(initialCategory?.color ?: "#FFFFFF") }
    var showColorPickerDialog by remember { mutableStateOf(false) }

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
                    onSave(name, aliases, showAll, color)
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
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = color,
                    onValueChange = { color = it },
                    label = { Text("Color (Hex)") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Color preview box
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            try {
                                Color(android.graphics.Color.parseColor(color))
                            } catch (e: IllegalArgumentException) {
                                Color.Gray // Fallback color for invalid hex
                            }
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { showColorPickerDialog = true }) {
                    Icon(Icons.Default.Palette, contentDescription = "Pick Color")
                }
            }
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

    if (showColorPickerDialog) {
        ColorPickerDialog(
            onColorSelected = { selectedColor ->
                color = "#%06X".format(0xFFFFFF and selectedColor.toArgb())
                showColorPickerDialog = false
            },
            onDismiss = { showColorPickerDialog = false }
        )
    }
}

@Composable
fun ColorPickerDialog(
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val commonColors = listOf(
        Color.Red, Color.Red.copy(red = 0.9f, green = 0.4f, blue = 0.5f), Color(0xFF9C27B0), Color(0xFF673AB7), // Red, Pink, Purple, DeepPurple
        Color(0xFF3F51B5), Color.Blue, Color(0xFF2196F3), Color(0xFF00BCD4), // Indigo, Blue, LightBlue, Cyan
        Color(0xFF009688), Color.Green, Color(0xFF8BC34A), Color(0xFFCDDC39), // Teal, Green, LightGreen, Lime
        Color.Yellow, Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFFFF5722), // Yellow, Amber, Orange, DeepOrange
        Color(0xFF795548), Color.Gray, Color(0xFF607D8B), Color.Black, Color.White // Brown, Gray, BlueGrey, Black, White
    )

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Wähle eine Farbe", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(commonColors) { colorItem ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(colorItem)
                                .clickable {
                                    onColorSelected(colorItem)
                                }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Abbrechen")
                }
            }
        }
    }
}
