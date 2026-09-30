package com.example.demo_oral.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.demo_oral.R

/**
 * DEBUG ONLY: write a message instead of speaking it.
 * To remove: delete this file, its call in ChatScreen, ChatViewModel.debugSendText
 * and the debug_* strings.
 */
@Composable
fun DebugTextInput(enabled: Boolean, onSend: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    fun send() {
        if (text.isBlank()) return
        onSend(text)
        text = ""
    }
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            enabled = enabled,
            placeholder = { Text(stringResource(R.string.debug_text_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = ::send, enabled = enabled && text.isNotBlank()) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.debug_text_send))
        }
    }
}
