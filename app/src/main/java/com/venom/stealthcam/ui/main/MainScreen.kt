package com.venom.stealthcam.ui.main

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.venom.stealthcam.ui.theme.AmoledBlack
import com.venom.stealthcam.ui.theme.DarkGrey
import com.venom.stealthcam.ui.theme.StealthRed
import com.venom.stealthcam.ui.theme.TextWhite
import kotlinx.coroutines.flow.collectLatest

@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is MainEffect.ShowToast -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                is MainEffect.NavigateToSettings -> onNavigateToSettings()
            }
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.onIntent(MainIntent.DismissError)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        // Settings Icon (top right)
        IconButton(
            onClick = onNavigateToSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = TextWhite
            )
        }

        // Timer Text (above the record button)
        if (state.isRecording) {
            Text(
                text = formatDuration(state.recordingDurationMs),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp
                ),
                color = TextWhite,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-100).dp)
            )
        } else if (state.isPreparing) {
            Text(
                text = "Preparing...",
                style = MaterialTheme.typography.bodyLarge,
                color = TextWhite,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-100).dp)
            )
        }

        // Record Button (centered)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(80.dp)
                .clip(CircleShape)
                .background(if (state.isRecording) AmoledBlack else DarkGrey)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { viewModel.onIntent(MainIntent.ToggleRecording) }
                ),
            contentAlignment = Alignment.Center
        ) {
            // Inner animated button shape
            val size by animateDpAsState(
                targetValue = if (state.isRecording) 32.dp else 64.dp,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label = "size"
            )
            
            val cornerRadius by animateDpAsState(
                targetValue = if (state.isRecording) 8.dp else 32.dp,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label = "cornerRadius"
            )
            
            val color by animateColorAsState(
                targetValue = if (state.isPreparing) Color.Gray else StealthRed,
                label = "color"
            )

            Box(
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(color)
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
