package com.example.budget.presentation.inbox

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.budget.domain.model.AssetType
import com.example.budget.presentation.common.BudgetSheet
import com.example.budget.presentation.common.SheetTitle
import com.example.budget.scan.Draft
import com.example.budget.scan.DraftKind
import com.example.budget.ui.components.*
import com.example.budget.ui.theme.Budget
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InboxScreen(onBack: () -> Unit, onOpenSettings: () -> Unit, viewModel: InboxViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let { viewModel.scanImages(listOf(it)) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) viewModel.scanImages(uris)
    }
    val onCamera = {
        val dir = File(context.cacheDir, "scans").apply { mkdirs() }
        val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.update_provider", file)
        cameraUri = uri
        camera.launch(uri)
    }
    val onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    val message = state.message ?: state.status.lastMessage
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }

    Box(Modifier.fillMaxSize().background(Budget.colors.paper)) {
        InboxContent(
            state = state,
            onBack = onBack,
            onCamera = onCamera,
            onPick = onPick,
            onOpenSettings = onOpenSettings,
            onEdit = viewModel::edit,
            onConfirm = viewModel::confirm,
            onConfirmAll = viewModel::confirmAll
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp))
    }

    state.editing?.let { d ->
        DraftSheet(
            draft = d,
            state = state,
            onChange = viewModel::updateEditing,
            onConfirm = { viewModel.confirm(d) },
            onDiscard = { viewModel.discard(d) },
            onDismiss = viewModel::closeEditor
        )
    }
}

@Composable
fun InboxContent(
    state: InboxState,
    onBack: () -> Unit,
    onCamera: () -> Unit,
    onPick: () -> Unit,
    onOpenSettings: () -> Unit,
    onEdit: (Draft) -> Unit,
    onConfirm: (Draft) -> Unit,
    onConfirmAll: () -> Unit
) {
    val c = Budget.colors
    Column(Modifier.fillMaxSize().background(c.paper)) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                PageHeader(
                    "To review",
                    modifier = Modifier.statusBarsPadding(),
                    leading = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = c.ink) } }
                )
            }
            item {
                Row(Modifier.padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton("Scan receipt", onClick = onCamera, icon = Icons.Outlined.PhotoCamera, modifier = Modifier.weight(1f))
                    SoftButton("Screenshots", onClick = onPick, icon = Icons.Outlined.PhotoLibrary, modifier = Modifier.weight(1f))
                }
            }
            if (state.status.working > 0) {
                item {
                    Row(
                        Modifier.padding(horizontal = Gutter, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = c.emerald, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Reading ${state.status.working} ${if (state.status.working == 1) "image" else "images"}…",
                            style = MaterialTheme.typography.bodyMedium, color = c.inkSoft
                        )
                    }
                }
            }
            if (state.drafts.isEmpty() && state.status.working == 0) {
                item {
                    EmptyNote(
                        "All caught up",
                        "Share a payment screenshot to Budget, scan a receipt, or turn on reading notifications in Settings. Everything is read on this phone, and shows up here to check before it's added."
                    )
                }
            }
            if (state.drafts.isNotEmpty()) {
                item {
                    Text(
                        "Check what was read, fix anything that's off, then add it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.inkSoft,
                        modifier = Modifier.padding(start = Gutter, end = Gutter, top = 20.dp, bottom = 8.dp)
                    )
                }
            }
            items(state.drafts, key = { it.id }) { d ->
                DraftCard(d, state, onEdit = { onEdit(d) }, onConfirm = { onConfirm(d) })
                Spacer(Modifier.height(10.dp))
            }
        }
        if (state.readyCount > 1) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(c.paper)
                    .navigationBarsPadding()
                    .padding(horizontal = Gutter - 4.dp, vertical = 12.dp)
            ) {
                PrimaryButton("Add all ${state.readyCount}", onClick = onConfirmAll)
            }
        }
    }
}

private val whenFmt = SimpleDateFormat("d MMM, HH:mm", Locale.ENGLISH)

@Composable
private fun DraftCard(d: Draft, state: InboxState, onEdit: () -> Unit, onConfirm: () -> Unit) {
    val c = Budget.colors
    val problem = state.problems[d.id]
    val asset = state.assets.firstOrNull { it.id == d.assetId }?.name
    val asset2 = state.assets.firstOrNull { it.id == d.asset2Id }?.name
    val fund = state.funds.firstOrNull { it.id == d.fundId }?.name
    val details = when (d.kind) {
        DraftKind.EXPENSE -> listOfNotNull(
            d.category,
            if (d.splitItems && d.items.isNotEmpty()) "${d.items.map { it.fundId ?: d.fundId }.distinct().size} pockets" else fund,
            asset
        )
        DraftKind.INCOME -> listOfNotNull("Income", asset?.let { "into $it" }, fund)
        DraftKind.TRANSFER -> listOf("Transfer", "${asset ?: "?"} to ${asset2 ?: "?"}")
    }.distinct().joinToString(", ")

    Group {
        ListRow(
            title = d.merchant,
            subtitle = "${d.origin}, ${whenFmt.format(Date(d.timestamp))}",
            leading = {
                Glyph(
                    when (d.kind) {
                        DraftKind.EXPENSE -> iconForName(d.category)
                        DraftKind.INCOME -> Icons.Outlined.Payments
                        DraftKind.TRANSFER -> Icons.Outlined.SwapHoriz
                    },
                    tint = if (d.kind == DraftKind.INCOME) c.emerald else c.ink,
                    background = if (d.kind == DraftKind.INCOME) c.emeraldTint else c.sunk
                )
            },
            trailing = {
                MoneyText(
                    if (d.kind == DraftKind.EXPENSE) -d.amount else d.amount,
                    style = MaterialTheme.typography.titleSmall,
                    hidden = state.hidden,
                    sign = if (d.kind == DraftKind.INCOME) SignMode.Always else if (d.kind == DraftKind.EXPENSE) SignMode.Auto else SignMode.None,
                    color = if (d.kind == DraftKind.INCOME) c.emerald else c.ink
                )
            },
            bottom = {
                Column {
                    Text(details, style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
                    if (d.items.isNotEmpty()) {
                        Text(
                            "${d.items.size} items: ${d.items.take(3).joinToString(", ") { it.name }}${if (d.items.size > 3) "…" else ""}",
                            style = MaterialTheme.typography.bodySmall, color = c.inkFaint, maxLines = 1
                        )
                    }
                    if (d.confidence < 0.6) {
                        Text("Unclear image. Double-check the amount.", style = MaterialTheme.typography.bodySmall, color = c.amber)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (problem != null) {
                            Text(problem, style = MaterialTheme.typography.labelMedium, color = c.amber, modifier = Modifier.weight(1f))
                            SoftButton("Fix", onClick = onEdit)
                        } else {
                            Text("Edit", style = MaterialTheme.typography.labelMedium, color = c.inkSoft,
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).clickable(onClick = onEdit).padding(vertical = 8.dp))
                            SoftButton("Add", onClick = onConfirm, icon = Icons.Outlined.Check, contentColor = c.onEmerald, background = c.emerald)
                        }
                    }
                }
            },
            onClick = onEdit
        )
    }
}

@Composable
private fun DraftSheet(
    draft: Draft,
    state: InboxState,
    onChange: ((Draft) -> Draft) -> Unit,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = Budget.colors
    BudgetSheet(onDismiss) {
        SheetTitle("Check transaction", draft.origin)
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = Gutter)) {
                Spacer(Modifier.height(4.dp))
                Segmented(
                    options = listOf(DraftKind.EXPENSE, DraftKind.INCOME, DraftKind.TRANSFER),
                    selected = draft.kind,
                    label = { when (it) { DraftKind.EXPENSE -> "Spending"; DraftKind.INCOME -> "Income"; DraftKind.TRANSFER -> "Transfer" } },
                    onSelect = { k ->
                        onChange { d -> d.copy(kind = k, fundId = if (k == DraftKind.INCOME) state.holdingFundId else d.fundId) }
                    }
                )
                Spacer(Modifier.height(14.dp))
                FieldLabel("Amount")
                MoneyField(
                    digits = if (draft.amount >= 1) draft.amount.toLong().toString() else "",
                    onDigits = { v -> onChange { d -> d.copy(amount = v.toDoubleOrNull() ?: 0.0) } }
                )
                Spacer(Modifier.height(14.dp))
                FieldLabel(if (draft.kind == DraftKind.INCOME) "From" else "Name")
                FieldWell(draft.merchant, { v -> onChange { d -> d.copy(merchant = v) } })
                Spacer(Modifier.height(10.dp))
            }

            val accounts = state.assets
            when (draft.kind) {
                DraftKind.EXPENSE -> {
                    ChipRow("Category") {
                        state.categories.forEach { cat ->
                            ChoiceChip(cat.name, draft.category == cat.name, { onChange { d -> d.copy(category = cat.name) } }, icon = iconForName(cat.name))
                        }
                    }
                    if (!(draft.splitItems && draft.items.isNotEmpty())) {
                        ChipRow("From pocket") {
                            state.funds.filter { it.id != state.holdingFundId }.forEach { f ->
                                ChoiceChip(f.name, draft.fundId == f.id, { onChange { d -> d.copy(fundId = f.id) } })
                            }
                        }
                    }
                    ChipRow("Paid with") {
                        accounts.forEach { a -> ChoiceChip(a.name, draft.assetId == a.id, { onChange { d -> d.copy(assetId = a.id) } }) }
                    }
                    if (draft.items.isNotEmpty()) ItemsSection(draft, state, onChange)
                }
                DraftKind.INCOME -> {
                    ChipRow("Into account") {
                        accounts.filter { it.type == AssetType.LIQUID }.forEach { a -> ChoiceChip(a.name, draft.assetId == a.id, { onChange { d -> d.copy(assetId = a.id) } }) }
                    }
                    ChipRow("Into pocket") {
                        state.funds.sortedByDescending { it.id == state.holdingFundId }.forEach { f ->
                            ChoiceChip(f.name, draft.fundId == f.id, { onChange { d -> d.copy(fundId = f.id) } })
                        }
                    }
                }
                DraftKind.TRANSFER -> {
                    ChipRow("From account") {
                        accounts.forEach { a -> ChoiceChip(a.name, draft.assetId == a.id, { onChange { d -> d.copy(assetId = a.id) } }) }
                    }
                    ChipRow("To account") {
                        accounts.filter { it.id != draft.assetId }.forEach { a -> ChoiceChip(a.name, draft.asset2Id == a.id, { onChange { d -> d.copy(asset2Id = a.id) } }) }
                    }
                }
            }
            if (accounts.isEmpty()) {
                Text("Add an account in Settings first.", style = MaterialTheme.typography.bodyMedium, color = c.amber,
                    modifier = Modifier.padding(horizontal = Gutter))
            }
        }
        Column(Modifier.padding(horizontal = Gutter - 4.dp)) {
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Add to Budget", onClick = onConfirm)
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onDiscard)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) { Text("Discard", style = MaterialTheme.typography.labelLarge, color = c.ruby) }
        }
    }
}

@Composable
private fun ItemsSection(draft: Draft, state: InboxState, onChange: ((Draft) -> Draft) -> Unit) {
    val c = Budget.colors
    Column(Modifier.padding(horizontal = Gutter)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onChange { d -> d.copy(splitItems = !d.splitItems) } }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Split by item", style = MaterialTheme.typography.titleSmall, color = c.ink)
                Text("Put each item in its own pocket", style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
            }
            Switch(
                checked = draft.splitItems,
                onCheckedChange = { on -> onChange { d -> d.copy(splitItems = on) } },
                colors = SwitchDefaults.colors(checkedTrackColor = c.emerald, checkedThumbColor = c.onEmerald)
            )
        }
        Spacer(Modifier.height(8.dp))
    }
    draft.items.forEachIndexed { i, item ->
        Column(Modifier.padding(bottom = 6.dp)) {
            Row(Modifier.padding(horizontal = Gutter)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge, color = c.ink, modifier = Modifier.weight(1f))
                MoneyText(item.amount, style = MaterialTheme.typography.titleSmall, hidden = state.hidden)
            }
            if (draft.splitItems) {
                Row(
                    Modifier
                        .padding(top = 6.dp)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Gutter),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.funds.filter { it.id != state.holdingFundId }.forEach { f ->
                        val selected = (item.fundId ?: draft.fundId) == f.id
                        ChoiceChip(f.name, selected, {
                            onChange { d -> d.copy(items = d.items.mapIndexed { j, it -> if (j == i) it.copy(fundId = f.id) else it }) }
                        })
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}
