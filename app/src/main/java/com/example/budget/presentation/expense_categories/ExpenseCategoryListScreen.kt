package com.example.budget.presentation.expense_categories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.budget.domain.model.ExpenseCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseCategoryListScreen(
    viewModel: ExpenseCategoryViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<ExpenseCategory?>(null) }
    
    var categoryNameInput by remember { mutableStateOf("") }

    if (showAddDialog || editingCategory != null) {
        AlertDialog(
            onDismissRequest = { 
                showAddDialog = false
                editingCategory = null
            },
            title = { Text(if (editingCategory != null) "Edit Category" else "Add Category") },
            text = {
                OutlinedTextField(
                    value = categoryNameInput,
                    onValueChange = { categoryNameInput = it },
                    label = { Text("Category Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editingCategory != null) {
                        viewModel.updateCategory(editingCategory!!, categoryNameInput)
                    } else {
                        viewModel.addCategory(categoryNameInput)
                    }
                    showAddDialog = false
                    editingCategory = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showAddDialog = false
                    editingCategory = null
                }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expense Categories") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                categoryNameInput = ""
                showAddDialog = true 
            }) {
                Icon(Icons.Default.Add, "Add Category")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(categories, key = { it.id }) { category ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(category.name, style = MaterialTheme.typography.bodyLarge)
                        Row {
                            IconButton(onClick = { 
                                categoryNameInput = category.name
                                editingCategory = category
                            }) {
                                Icon(Icons.Default.Edit, "Edit")
                            }
                            IconButton(onClick = { viewModel.deleteCategory(category) }) {
                                Icon(Icons.Default.Delete, "Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}
