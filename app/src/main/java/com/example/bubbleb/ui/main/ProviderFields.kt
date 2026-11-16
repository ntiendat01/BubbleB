package com.example.bubbleb.ui.main

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.bubbleb.R
import com.example.bubbleb.domain.llm.LlmLimits
import com.example.bubbleb.presentation.ProviderForm

@Composable
fun ProviderFields(form: ProviderForm, edit: ((ProviderForm) -> ProviderForm) -> Unit) {
    val enabled = !form.loading && !form.saving
    Field(form.name, R.string.provider_name, enabled) { value -> edit { it.copy(name = value) } }
    Field(form.baseUrl, R.string.provider_url, enabled, KeyboardType.Uri) { value -> edit { it.copy(baseUrl = value) } }
    Field(form.model, R.string.provider_model, enabled) { value -> edit { it.copy(model = value) } }
    OutlinedTextField(
        value = form.key,
        onValueChange = { value -> if (value.length <= LlmLimits.MAX_KEY_CHARS) edit { it.copy(key = value) } },
        label = { Text(stringResource(R.string.provider_key)) },
        supportingText = { Text(stringResource(R.string.provider_key_keep)) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth(),
    )
    Field(form.tokens, R.string.provider_tokens, enabled, KeyboardType.Number) { value -> edit { it.copy(tokens = value) } }
    Field(form.inputLimit, R.string.provider_input_cap, enabled, KeyboardType.Number) { value -> edit { it.copy(inputLimit = value) } }
    val streamingLabel = stringResource(R.string.provider_streaming)
    Row(Modifier.fillMaxWidth()) {
        Text(streamingLabel, modifier = Modifier.weight(1f))
        Switch(checked = form.streaming, enabled = enabled, onCheckedChange = { value -> edit { it.copy(streaming = value) } },
            modifier = Modifier.semantics { contentDescription = streamingLabel })
    }
    Text(stringResource(R.string.provider_streaming_help))
    Field(form.inputPrice, R.string.provider_price_input, enabled, KeyboardType.Decimal) { value -> edit { it.copy(inputPrice = value) } }
    Field(form.outputPrice, R.string.provider_price_output, enabled, KeyboardType.Decimal) { value -> edit { it.copy(outputPrice = value) } }
}

@Composable
private fun Field(value: String, label: Int, enabled: Boolean, type: KeyboardType = KeyboardType.Text, change: (String) -> Unit) {
    OutlinedTextField(value, change, label = { Text(stringResource(label)) }, enabled = enabled,
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = type, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
}
