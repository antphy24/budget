package com.example.budget.update

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import com.example.budget.presentation.common.BudgetSheet
import com.example.budget.ui.components.Gutter
import com.example.budget.ui.components.PrimaryButton
import com.example.budget.ui.components.SoftButton
import com.example.budget.ui.theme.Budget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class UpdateViewModel @Inject constructor(val manager: UpdateManager) : ViewModel()

/**
 * Checks GitHub quietly a few seconds after opening and whenever the app comes back
 * to the front (at most every 6 hours), then offers the update in a sheet.
 */
@Composable
fun UpdateHost(viewModel: UpdateViewModel = hiltViewModel()) {
    val manager = viewModel.manager
    val state by manager.state.collectAsState()
    val dismissed by manager.dismissed.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(Unit) {
        delay(3000)
        manager.checkInBackground()
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                val s = manager.state.value
                // Coming back from "allow installs" settings: try installing again
                if (s is UpdateState.Ready && s.needsPermission) manager.install(s.info, s.file)
                else manager.checkInBackground()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val info = when (val s = state) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        is UpdateState.Ready -> s.info
        is UpdateState.Failed -> s.info
        else -> null
    } ?: return
    if (dismissed) return

    val c = Budget.colors
    BudgetSheet(onDismiss = { if (!info.mandatory && state !is UpdateState.Downloading) manager.dismiss() }) {
        Column(Modifier.padding(horizontal = Gutter)) {
            Spacer(Modifier.height(8.dp))
            Text("Version ${info.versionName} is ready", style = MaterialTheme.typography.headlineSmall, color = c.ink)
            Spacer(Modifier.height(4.dp))
            val size = if (info.apkSize > 0) ", ${"%.1f".format(info.apkSize / 1_048_576.0)} MB" else ""
            Text(
                "You have ${manager.currentVersion.name}$size. Your data stays on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.inkSoft
            )
            if (info.mandatory) {
                Spacer(Modifier.height(8.dp))
                Text("This update is required to keep using the app.", style = MaterialTheme.typography.bodyMedium, color = c.ruby)
            }
            if (info.notes.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text("What's new", style = MaterialTheme.typography.titleSmall, color = c.ink)
                Spacer(Modifier.height(6.dp))
                Text(info.notes, style = MaterialTheme.typography.bodyLarge, color = c.ink)
            }
            Spacer(Modifier.height(20.dp))

            when (val s = state) {
                is UpdateState.Downloading -> {
                    val p = s.progress
                    if (p == null) LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.emerald, trackColor = c.sunk)
                    else LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth(), color = c.emerald, trackColor = c.sunk)
                    Spacer(Modifier.height(8.dp))
                    Text(if (p == null) "Downloading…" else "Downloading… ${(p * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
                }
                is UpdateState.Ready -> {
                    Text(
                        if (s.needsPermission) "Allow Budget to install apps on the page that opened, then come back here."
                        else "Tap Update on Android's screen to finish.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.inkSoft
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Install", onClick = { manager.install(s.info, s.file) })
                }
                is UpdateState.Failed -> {
                    Text(s.message, style = MaterialTheme.typography.bodyMedium, color = c.ruby)
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Try again", onClick = { manager.download(info) })
                }
                else -> PrimaryButton("Update now", onClick = { manager.download(info) })
            }
            if (!info.mandatory && state !is UpdateState.Downloading) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth()) {
                    SoftButton("Later", onClick = manager::dismiss, modifier = Modifier.fillMaxWidth(), background = c.paper, contentColor = c.inkSoft)
                }
            }
        }
    }
}
