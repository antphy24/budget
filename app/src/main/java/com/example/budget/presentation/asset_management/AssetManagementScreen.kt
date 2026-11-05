package com.example.budget.presentation.asset_management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.budget.R
import com.example.budget.domain.model.AssetType
import com.example.budget.utils.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetManagementScreen(
    viewModel: AssetManagementViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Assets") },
                actions = {
                    IconButton(onClick = { viewModel.toggleHideBalance() }) {
                        Icon(
                            painter = painterResource(id = if (state.isBalanceHidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                            contentDescription = "Toggle Balance Visibility"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.onEvent(AssetManagementEvent.AddNewClick) }) {
                Icon(Icons.Default.Add, "Add Asset")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(state.assets) { asset ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(asset.name, style = MaterialTheme.typography.titleMedium)
                            Text(asset.type.name, style = MaterialTheme.typography.bodySmall)
                            Text(CurrencyFormatter.formatToIDR(asset.balance, state.isBalanceHidden), style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.togglePin(asset.id, !asset.isPinned) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = if (asset.isPinned) R.drawable.ic_push_pin else R.drawable.ic_push_pin_outline),
                                    contentDescription = "Pin",
                                    tint = if (asset.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.onEvent(AssetManagementEvent.EditClick(asset)) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Edit, "Edit")
                            }
                            IconButton(onClick = { viewModel.onEvent(AssetManagementEvent.Delete(asset)) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Delete, "Delete")
                            }
                        }
                    }
                }
            }
        }

        if (state.showDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.onEvent(AssetManagementEvent.DismissDialog) },
                title = { Text(if (state.editingAssetId == null) "New Asset" else "Edit Asset") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = state.nameInput,
                            onValueChange = { viewModel.onEvent(AssetManagementEvent.NameChanged(it)) },
                            label = { Text("Asset Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            FilterChip(
                                selected = state.typeInput == AssetType.LIQUID,
                                onClick = { viewModel.onEvent(AssetManagementEvent.TypeChanged(AssetType.LIQUID)) },
                                label = { Text("LIQUID") }
                            )
                            FilterChip(
                                selected = state.typeInput == AssetType.NON_LIQUID,
                                onClick = { viewModel.onEvent(AssetManagementEvent.TypeChanged(AssetType.NON_LIQUID)) },
                                label = { Text("NON-LIQUID") }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.balanceInput,
                            onValueChange = { viewModel.onEvent(AssetManagementEvent.BalanceChanged(it)) },
                            label = { Text("Initial Balance") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = state.editingAssetId == null // Only allow setting initial balance for new assets. Editing balance should be done via transactions or manual reconciliation.
                        )
                        if (state.editingAssetId != null) {
                            Text("Note: To change balance of an existing asset, please use a transaction.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.onEvent(AssetManagementEvent.Save) }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.onEvent(AssetManagementEvent.DismissDialog) }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
