package com.venom.stealthcam.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.venom.stealthcam.domain.model.BitrateMode
import com.venom.stealthcam.domain.model.Resolution
import com.venom.stealthcam.domain.model.VideoCodec
import com.venom.stealthcam.isBatteryOptimizationDisabled
import com.venom.stealthcam.isAccessibilityEnabled
import com.venom.stealthcam.requestBatteryOptimizationExemption
import com.venom.stealthcam.ui.theme.AmoledBlack
import com.venom.stealthcam.ui.theme.DarkGrey
import com.venom.stealthcam.ui.theme.LightGrey
import com.venom.stealthcam.ui.theme.StealthRed
import com.venom.stealthcam.ui.theme.TextWhite
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is SettingsEffect.NavigateBack -> onNavigateBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = TextWhite) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onIntent(SettingsIntent.NavigateBack) }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AmoledBlack)
            )
        },
        containerColor = AmoledBlack
    ) { paddingValues ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = StealthRed)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                val settings = state.settings

                // ── Recording ─────────────────────────────────────────────────

                SettingsSectionTitle("Recording")

                // Video Codec
                SettingsDropdown(
                    label = "Video Codec",
                    currentValue = settings.videoCodec.displayName,
                    options = VideoCodec.entries.map { it.displayName },
                    onOptionSelected = { selected ->
                        val codec = VideoCodec.entries.first { it.displayName == selected }
                        viewModel.onIntent(SettingsIntent.UpdateSettings(settings.copy(videoCodec = codec)))
                    }
                )

                // Resolution
                val resolutionOptions = listOf("Auto (Max)", "4K (3840×2160)", "1080p (1920×1080)", "720p (1280×720)", "480p (640×480)")
                val currentResolution = when (settings.preferredResolution) {
                    null                  -> "Auto (Max)"
                    Resolution.UHD_4K    -> "4K (3840×2160)"
                    Resolution.FHD_1080P -> "1080p (1920×1080)"
                    Resolution.HD_720P   -> "720p (1280×720)"
                    Resolution.SD_480P   -> "480p (640×480)"
                    else                 -> "${settings.preferredResolution.width}×${settings.preferredResolution.height}"
                }
                SettingsDropdown(
                    label = "Resolution",
                    currentValue = currentResolution,
                    options = resolutionOptions,
                    onOptionSelected = { selected ->
                        val res = when (selected) {
                            "Auto (Max)"        -> null
                            "4K (3840×2160)"    -> Resolution.UHD_4K
                            "1080p (1920×1080)" -> Resolution.FHD_1080P
                            "720p (1280×720)"   -> Resolution.HD_720P
                            "480p (640×480)"    -> Resolution.SD_480P
                            else                -> null
                        }
                        viewModel.onIntent(SettingsIntent.UpdateSettings(settings.copy(preferredResolution = res)))
                    }
                )

                // Frame Rate
                val frameRateOptions = listOf("Auto (Max)", "60 fps", "30 fps", "24 fps")
                val currentFrameRate = settings.preferredFrameRate?.let { "$it fps" } ?: "Auto (Max)"
                SettingsDropdown(
                    label = "Frame Rate",
                    currentValue = currentFrameRate,
                    options = frameRateOptions,
                    onOptionSelected = { selected ->
                        val fps = when (selected) {
                            "Auto (Max)" -> null
                            "60 fps"     -> 60
                            "30 fps"     -> 30
                            "24 fps"     -> 24
                            else         -> null
                        }
                        viewModel.onIntent(SettingsIntent.UpdateSettings(settings.copy(preferredFrameRate = fps)))
                    }
                )

                // Bitrate
                val bitrateOptions = listOf("Auto", "50 Mbps", "30 Mbps", "16 Mbps", "8 Mbps", "4 Mbps")
                val currentBitrate = when (val bm = settings.bitrateMode) {
                    is BitrateMode.Auto   -> "Auto"
                    is BitrateMode.Manual -> "${bm.bps / 1_000_000} Mbps"
                }
                SettingsDropdown(
                    label = "Bitrate",
                    currentValue = currentBitrate,
                    options = bitrateOptions,
                    onOptionSelected = { selected ->
                        val mode = when (selected) {
                            "Auto"    -> BitrateMode.Auto
                            "50 Mbps" -> BitrateMode.Manual(50_000_000)
                            "30 Mbps" -> BitrateMode.Manual(30_000_000)
                            "16 Mbps" -> BitrateMode.Manual(16_000_000)
                            "8 Mbps"  -> BitrateMode.Manual(8_000_000)
                            "4 Mbps"  -> BitrateMode.Manual(4_000_000)
                            else      -> BitrateMode.Auto
                        }
                        viewModel.onIntent(SettingsIntent.UpdateSettings(settings.copy(bitrateMode = mode)))
                    }
                )

                // Audio Toggle
                SettingsSwitch(
                    label = "Record Audio",
                    description = "Capture microphone audio alongside video",
                    isChecked = settings.isAudioEnabled,
                    onCheckedChange = {
                        viewModel.onIntent(SettingsIntent.UpdateSettings(settings.copy(isAudioEnabled = it)))
                    }
                )

                // ── Trigger ───────────────────────────────────────────────────

                SettingsSectionTitle("Volume Trigger")

                // Long Press Duration
                val durationOptions = listOf("300 ms", "500 ms", "750 ms", "1000 ms")
                val currentDuration = "${settings.longPressDurationMs.toInt()} ms"
                    .takeIf { settings.longPressDurationMs in listOf(300L, 500L, 750L, 1000L) }
                    ?: "${settings.longPressDurationMs} ms"
                SettingsDropdown(
                    label = "Long Press Duration",
                    currentValue = currentDuration,
                    options = durationOptions,
                    onOptionSelected = { selected ->
                        val ms = selected.removeSuffix(" ms").trim().toLongOrNull() ?: 500L
                        viewModel.onIntent(SettingsIntent.UpdateSettings(settings.copy(longPressDurationMs = ms)))
                    }
                )

                // ── Storage ───────────────────────────────────────────────────

                SettingsSectionTitle("Storage")

                SettingsSwitch(
                    label = "Hidden Folder (.StealthCam)",
                    description = "Save in a hidden directory with a .nomedia file",
                    isChecked = settings.storageConfig.isHiddenStorage,
                    onCheckedChange = {
                        val newConfig = settings.storageConfig.copy(isHiddenStorage = it)
                        viewModel.onIntent(SettingsIntent.UpdateSettings(settings.copy(storageConfig = newConfig)))
                    }
                )

                // ── System ────────────────────────────────────────────────────

                SettingsSectionTitle("System")

                // Recording Overlay Indicator
                SettingsSwitch(
                    label = "Recording Indicator",
                    description = "Show a red dot on screen while recording is active",
                    isChecked = settings.showRecordingOverlay,
                    onCheckedChange = {
                        viewModel.onIntent(SettingsIntent.UpdateSettings(settings.copy(showRecordingOverlay = it)))
                    }
                )

                // Foreground Notification
                SettingsSwitch(
                    label = "Foreground Notification",
                    description = "Required by Android 8+. Hides the recording notification (not recommended).",
                    isChecked = settings.isForegroundNotificationEnabled,
                    onCheckedChange = {
                        viewModel.onIntent(SettingsIntent.UpdateSettings(settings.copy(isForegroundNotificationEnabled = it)))
                    }
                )

                // Battery Optimization shortcut
                val isBatteryExempt = isBatteryOptimizationDisabled(context)
                SettingsActionRow(
                    label = "Battery Optimization",
                    description = if (isBatteryExempt)
                        "✅ Exempted — screen-off trigger active"
                    else
                        "⚠️ Not exempted — screen-off trigger may not work",
                    actionLabel = if (isBatteryExempt) "Granted" else "Fix",
                    enabled = !isBatteryExempt,
                    onClick = { requestBatteryOptimizationExemption(context) }
                )

                // Accessibility Service shortcut
                val isAccessibilityOn = isAccessibilityEnabled(context)
                SettingsActionRow(
                    label = "Volume Trigger Service",
                    description = if (isAccessibilityOn)
                        "✅ Enabled — volume buttons are intercepted"
                    else
                        "⚠️ Disabled — volume buttons will not trigger recording",
                    actionLabel = if (isAccessibilityOn) "Enabled" else "Enable",
                    enabled = !isAccessibilityOn,
                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// ── Reusable Setting Components ───────────────────────────────────────────────

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        color = StealthRed,
        modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsSwitch(
    label: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = TextWhite)
            Spacer(modifier = Modifier.height(3.dp))
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = LightGrey)
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextWhite,
                checkedTrackColor = StealthRed,
                uncheckedThumbColor = LightGrey,
                uncheckedTrackColor = DarkGrey,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
fun SettingsActionRow(
    label: String,
    description: String,
    actionLabel: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = TextWhite)
            Spacer(modifier = Modifier.height(3.dp))
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = LightGrey)
        }
        TextButton(
            onClick = onClick,
            enabled = enabled
        ) {
            Text(
                text = actionLabel,
                color = if (enabled) StealthRed else LightGrey,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDropdown(
    label: String,
    currentValue: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = TextWhite,
            modifier = Modifier.weight(1f)
        )

        Box {
            Text(
                text = currentValue,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = StealthRed,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.padding(end = 4.dp)
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(DarkGrey)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                option,
                                color = if (option == currentValue) StealthRed else TextWhite
                            )
                        },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
