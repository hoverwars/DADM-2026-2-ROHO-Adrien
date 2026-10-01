package com.example.demo_oral.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.demo_oral.R
import com.example.demo_oral.viewmodel.ChatMessage
import com.example.demo_oral.viewmodel.ChatUiState
import com.example.demo_oral.viewmodel.ChatViewModel
import com.example.demo_oral.viewmodel.ModelState
import com.example.demo_oral.viewmodel.Role

@Composable
fun ChatScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionDenied = !granted }

    // A tool (send an SMS, add a calendar event...) needs Android permissions: ask for them now
    val toolPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> viewModel.onPermissionsResult(results.values.all { it }) }
    LaunchedEffect(state.pendingPermissions) {
        if (state.pendingPermissions.isNotEmpty()) {
            toolPermissionLauncher.launch(state.pendingPermissions.toTypedArray())
        }
    }

    fun onMicPressed() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        // The very first press only asks for the permission, the user then presses again
        if (granted) viewModel.startRecording() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.button_back))
            }
            Text(
                text = stringResource(R.string.title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            SettingsMenu(
                speechDisabled = !state.speechEnabled,
                onSpeechDisabledChange = { viewModel.setSpeechEnabled(!it) },
                keyboardHidden = state.keyboardHidden,
                onKeyboardHiddenChange = viewModel::setKeyboardHidden,
            )
            IconButton(
                onClick = viewModel::resetConversation,
                enabled = state.messages.isNotEmpty() && !state.isRecording,
            ) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.button_reset))
            }
        }

        Conversation(
            state = state,
            onConfirm = { viewModel.onConfirmationButton(true) },
            onCancel = viewModel::onCancelButton,
            onGrant = viewModel::requestPermissions,
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        StatusLine(state = state, permissionDenied = permissionDenied)

        // DEBUG: typed input, remove this call and DebugTextInput.kt
        if (!state.keyboardHidden) {
            DebugTextInput(
                enabled = state.modelsReady && !state.assistantBusy && !state.isRecording && !state.isFinishing,
                onSend = viewModel::debugSendText,
            )
        }

        MicButton(
            isRecording = state.isRecording,
            enabled = state.modelsReady && !state.assistantBusy,
            onPress = ::onMicPressed,
            onRelease = viewModel::stopRecording,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
    }
}

@Composable
private fun SettingsMenu(
    speechDisabled: Boolean,
    onSpeechDisabledChange: (Boolean) -> Unit,
    keyboardHidden: Boolean,
    onKeyboardHiddenChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.button_settings))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.setting_disable_tts)) },
                trailingIcon = { Checkbox(checked = speechDisabled, onCheckedChange = null) },
                onClick = { onSpeechDisabledChange(!speechDisabled) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.setting_hide_keyboard)) },
                trailingIcon = { Checkbox(checked = keyboardHidden, onCheckedChange = null) },
                onClick = { onKeyboardHiddenChange(!keyboardHidden) },
            )
        }
    }
}

@Composable
private fun Conversation(
    state: ChatUiState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Newest first, with a reversed layout: the list stays glued to the latest message
    val bubbles = buildList {
        addAll(state.messages)
        if (state.hasDraft) add(ChatMessage(DRAFT_ID, Role.USER, ""))
    }.asReversed()

    val listState = rememberLazyListState()
    LaunchedEffect(bubbles.size) { listState.animateScrollToItem(0) }

    if (!state.modelsReady) {
        LoadingPanel(state, modifier)
        return
    }
    if (bubbles.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.chat_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        reverseLayout = true,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(bubbles, key = { it.id }) { message ->
            when {
                message.id == DRAFT_ID -> DraftBubble(state.transcript, state.partial, state.isRecording)
                message.role == Role.USER -> Bubble(message.text, fromUser = true)
                message.action != null -> ToolActionCard(
                    action = message.action,
                    text = message.text,
                    onConfirm = onConfirm,
                    onCancel = onCancel,
                    onGrant = onGrant,
                    onOpenSettings = onOpenSettings,
                )
                else -> Bubble(
                    text = message.text,
                    fromUser = false,
                    // Nothing to show yet: the LLM is still reading the conversation
                    thinking = message.text.isEmpty(),
                )
            }
        }
    }
}

@Composable
private fun Bubble(text: String, fromUser: Boolean, thinking: Boolean = false) {
    BubbleFrame(fromUser) {
        if (thinking) {
            ThinkingDots()
        } else {
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** The user's message while it is still being spoken: the sentence in progress may change. */
@Composable
private fun DraftBubble(transcript: String, partial: String, isRecording: Boolean) {
    val partialColor = MaterialTheme.colorScheme.onSurfaceVariant
    BubbleFrame(fromUser = true) {
        if (transcript.isEmpty() && partial.isEmpty() && !isRecording) {
            // Mic released, the end of the message is still being transcribed
            ThinkingDots()
        } else if (transcript.isEmpty() && partial.isEmpty()) {
            Text(
                text = stringResource(R.string.status_listening),
                style = MaterialTheme.typography.bodyLarge,
                color = partialColor,
                fontStyle = FontStyle.Italic,
            )
        } else {
            Text(
                text = buildAnnotatedString {
                    append(transcript)
                    if (partial.isNotEmpty()) {
                        if (transcript.isNotEmpty()) append(" ")
                        withStyle(SpanStyle(color = partialColor, fontStyle = FontStyle.Italic)) {
                            append(partial)
                        }
                    }
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun BubbleFrame(fromUser: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (fromUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (fromUser) 18.dp else 4.dp,
                bottomEnd = if (fromUser) 4.dp else 18.dp,
            ),
            color = if (fromUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) { content() }
        }
    }
}

@Composable
private fun StatusLine(state: ChatUiState, permissionDenied: Boolean) {
    // While the models load (or failed), the conversation area already says everything
    if (!state.modelsReady) return
    val message = when {
        permissionDenied -> R.string.status_permission_denied
        state.awaitingConfirmation -> R.string.status_confirm
        state.awaitingInfo -> R.string.status_awaiting_info
        state.isRecording -> R.string.status_release_to_send
        state.isFinishing -> R.string.status_finishing
        state.isGenerating -> R.string.status_answering
        else -> R.string.status_ready
    }
    Text(
        text = stringResource(message),
        style = MaterialTheme.typography.bodyMedium,
        color = if (permissionDenied) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun LoadingPanel(state: ChatUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(56.dp), strokeWidth = 5.dp)
        Spacer(Modifier.size(20.dp))
        Text(stringResource(R.string.loading_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.loading_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(24.dp))
        LoadingStep(R.string.loading_speech_model, R.string.loading_error_speech_model, state.speechModel)
        Spacer(Modifier.size(8.dp))
        LoadingStep(R.string.loading_llm, R.string.loading_error_llm, state.llmModel)
    }
}

@Composable
private fun LoadingStep(label: Int, errorLabel: Int, model: ModelState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (model) {
            ModelState.LOADING -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            ModelState.READY -> Icon(
                Icons.Filled.CheckCircle, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp),
            )
            ModelState.ERROR -> Icon(
                Icons.Filled.Error, contentDescription = null,
                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.size(10.dp))
        Text(
            text = stringResource(if (model == ModelState.ERROR) errorLabel else label),
            style = MaterialTheme.typography.bodyLarge,
            color = if (model == ModelState.ERROR) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Three dots pulsing one after the other: the assistant is thinking. */
@Composable
private fun ThinkingDots() {
    val transition = rememberInfiniteTransition(label = "thinking")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 160),
                ),
                label = "dot$index",
            )
            Box(
                Modifier
                    .size(9.dp)
                    .alpha(alpha)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
            )
        }
    }
}

/** Push-to-talk: the message lasts as long as the button is held down. */
@Composable
private fun MicButton(
    isRecording: Boolean,
    enabled: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant
        isRecording -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = modifier
            .size(if (isRecording) 96.dp else 84.dp)
            .clip(CircleShape)
            .background(color)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        onPress()
                        tryAwaitRelease()
                        onRelease()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = stringResource(R.string.button_talk),
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(40.dp),
        )
    }
}

private const val DRAFT_ID = -1L
