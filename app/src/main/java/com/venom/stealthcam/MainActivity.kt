package com.venom.stealthcam

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.venom.stealthcam.core.trigger.VolumeKeyAccessibilityService
import com.venom.stealthcam.ui.main.MainScreen
import com.venom.stealthcam.ui.settings.SettingsScreen
import com.venom.stealthcam.ui.theme.StealthCamTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.net.toUri

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            StealthCamTheme {
                val permissions = mutableListOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }

                val permissionState = rememberMultiplePermissionsState(permissions = permissions)

                LaunchedEffect(Unit) {
                    if (!permissionState.allPermissionsGranted) {
                        permissionState.launchMultiplePermissionRequest()
                    }
                }

                if (permissionState.allPermissionsGranted) {
                    // Show onboarding prompts for special permissions, then main content
                    OnboardingGate {
                        StealthCamAppNavHost()
                    }
                } else {
                    PermissionDeniedScreen()
                }
            }
        }
    }
}

/**
 * Sequential onboarding for special permissions that cannot use the normal
 * Compose permissions API.
 *
 * Step machine: each step is evaluated lazily so already-granted permissions are skipped.
 *   0 → Accessibility service (volume trigger)
 *   1 → SYSTEM_ALERT_WINDOW (red-dot overlay)
 *   2 → Battery optimization exemption (screen-off trigger)
 *   3+ → Done — show main content
 *
 * New installs will see all three dialogs. Users who already granted one or more
 * will skip those steps automatically.
 */
@Composable
fun OnboardingGate(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Compute which step to start on: skip already-granted permissions
    val initialStep = remember {
        when {
            !isAccessibilityEnabled(context)     -> 0
            !Settings.canDrawOverlays(context)   -> 1
            !isBatteryOptimizationDisabled(context) -> 2
            else                                 -> 3
        }
    }

    var step by remember { mutableStateOf(initialStep) }

    fun advance() {
        // Advance through steps, skipping already-granted ones
        var next = step + 1
        while (next < 3) {
            val alreadyGranted = when (next) {
                1 -> Settings.canDrawOverlays(context)
                2 -> isBatteryOptimizationDisabled(context)
                else -> false
            }
            if (alreadyGranted) next++ else break
        }
        step = next
    }

    when (step) {
        0 -> AlertDialog(
            onDismissRequest = { advance() },
            title = { Text("Enable Volume Trigger") },
            text = {
                Text(
                    "StealthCam uses an Accessibility Service to detect volume button " +
                    "long-presses and start/stop recording — even when the screen is off.\n\n" +
                    "Please enable \"StealthCam\" in the next screen."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    advance()
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { advance() }) { Text("Skip") }
            }
        )

        1 -> AlertDialog(
            onDismissRequest = { advance() },
            title = { Text("Recording Indicator") },
            text = {
                Text(
                    "Allow StealthCam to show a small red dot on screen while recording.\n\n" +
                    "You can also toggle this off in Settings at any time."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    advance()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                "package:${context.packageName}".toUri()
                            )
                        )
                    }
                }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = { advance() }) { Text("Skip") }
            }
        )

        2 -> AlertDialog(
            onDismissRequest = { advance() },
            title = { Text("⚠️ Disable Battery Optimization") },
            text = {
                Text(
                    "REQUIRED for screen-off recording trigger.\n\n" +
                    "Without this, Android freezes StealthCam when the screen turns off " +
                    "and volume buttons will not trigger recording.\n\n" +
                    "Tap \"Disable\" then select \"Don't optimize\" in the next screen."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    advance()
                    requestBatteryOptimizationExemption(context)
                }) { Text("Disable") }
            },
            dismissButton = {
                TextButton(onClick = { advance() }) { Text("Skip") }
            }
        )

        // step >= 3 → all done, show content
        else -> { /* no dialog */ }
    }

    content()
}

/**
 * Returns true if [VolumeKeyAccessibilityService] is currently enabled in system settings.
 */
fun isAccessibilityEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(
        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
    )
    return enabledServices.any { info ->
        info.resolveInfo.serviceInfo.packageName == context.packageName &&
        info.resolveInfo.serviceInfo.name == VolumeKeyAccessibilityService::class.java.name
    }
}

/**
 * Returns true if the app is already exempt from battery optimization.
 * On API < M, always returns true (battery optimization doesn't exist).
 */
fun isBatteryOptimizationDisabled(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val pm = context.getSystemService(android.os.PowerManager::class.java)
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

/**
 * Opens the system dialog that lets the user exempt this app from battery optimization.
 * Uses ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS which goes directly to the per-app dialog.
 */
fun requestBatteryOptimizationExemption(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                android.net.Uri.parse("package:${context.packageName}")
            )
        )
    }
}

@Composable
fun StealthCamAppNavHost() {
    val navController = rememberNavController()
    
    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun PermissionDeniedScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Camera and Audio permissions are required.",
            color = Color.White
        )
    }
}