package com.example.budget

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.example.budget.presentation.navigation.BudgetNavGraph
import com.example.budget.ui.components.PrimaryButton
import com.example.budget.ui.theme.Budget
import com.example.budget.ui.theme.BudgetTheme
import com.example.budget.scan.ScanRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var scan: ScanRepository

    /** Set when screenshots were shared to the app; opens the review inbox after unlocking */
    private val openInbox = mutableStateOf(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            )
            Intent.ACTION_SEND_MULTIPLE ->
                (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)) ?: emptyList()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) {
            scan.scanImages(uris, "SHARE")
            openInbox.value = true
            intent.action = null // don't re-scan after rotation
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            BudgetTheme {
                var unlocked by rememberSaveable { mutableStateOf(false) }
                var prompting by remember { mutableStateOf(true) }

                if (unlocked) {
                    BudgetNavGraph(openInbox = openInbox.value, onInboxOpened = { openInbox.value = false })
                } else {
                    LockScreen(onUnlock = { prompting = true })
                }

                if (prompting && !unlocked) {
                    LaunchedEffect(Unit) {
                        authenticate(
                            onSuccess = { unlocked = true; prompting = false },
                            onFail = { prompting = false }
                        )
                    }
                }
            }
        }
    }

    private fun authenticate(onSuccess: () -> Unit, onFail: () -> Unit) {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail()
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Budget")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val c = Budget.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.hero)
            .systemBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp)
    ) {
        Spacer(Modifier.weight(1f))
        AppMark(Modifier.size(64.dp))
        Spacer(Modifier.height(28.dp))
        Text("Budget", style = MaterialTheme.typography.headlineLarge, color = c.onHero)
        Spacer(Modifier.height(6.dp))
        Text("Locked to keep your money private.", style = MaterialTheme.typography.bodyLarge, color = c.onHeroSoft)
        Spacer(Modifier.weight(1.4f))
        PrimaryButton("Unlock", onClick = onUnlock, color = c.onHero, contentColor = c.hero)
    }
}

/** The launcher mark drawn in Compose: three pace rails and the "today" tick. */
@Composable
fun AppMark(modifier: Modifier = Modifier) {
    val mint = Color(0xFF8FE0C2)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val bar = h * 0.14f
        val gap = h * 0.13f
        val top = (h - 3 * bar - 2 * gap) / 2
        val fills = listOf(0.82f, 0.56f, 0.32f)
        fills.forEachIndexed { i, f ->
            val y = top + i * (bar + gap)
            drawRoundRect(Color.White.copy(alpha = 0.2f), Offset(0f, y), Size(w, bar), CornerRadius(bar / 2))
            drawRoundRect(Color.White, Offset(0f, y), Size(w * f, bar), CornerRadius(bar / 2))
        }
        val x = w * 0.62f
        drawRect(mint, Offset(x, top - gap * 0.6f), Size(w * 0.06f, 3 * bar + 2 * gap + gap * 1.2f))
    }
}
