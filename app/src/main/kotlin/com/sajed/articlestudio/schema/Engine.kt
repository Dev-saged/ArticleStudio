package com.sajed.articlestudio.schema

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.sajed.articlestudio.ui.AppChip
import com.sajed.articlestudio.ui.Panel
import com.sajed.articlestudio.ui.icons.Icons
import com.sajed.articlestudio.ui.theme.AppTheme
import com.sajed.articlestudio.ui.theme.NumberStyle
import com.sajed.articlestudio.ui.theme.ltr

/** المحرك العام: نموذج وقائمة ومؤشرات ومرشحات تُرسم كلها من المخطط. */
@Composable
fun SchemaForm(schema: ModuleSchema, values: Row, errors: Map<String, String>, onChange: (String, String) -> Unit) {
    val c = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        schema.fields.forEach { f ->
            val v = values[f.key].orEmpty()
            when (f.type) {
                FieldType.Toggle -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(f.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = c.text)
                    Switch(
                        checked = v == "true", onCheckedChange = { onChange(f.key, it.toString()) },
                        colors = SwitchDefaults.colors(checkedTrackColor = c.goldText, checkedThumbColor = c.surface),
                    )
                }
                FieldType.Choice -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(f.label, style = MaterialTheme.typography.labelMedium, color = c.muted)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        f.options.forEach { o -> AppChip(o, v == o, { onChange(f.key, o) }) }
                    }
                }
                else -> {
                    var shown by remember { mutableStateOf(false) }
                    val secret = f.type == FieldType.Secret
                    OutlinedTextField(
                        value = v, onValueChange = { onChange(f.key, SchemaRules.sanitize(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(f.label) }, singleLine = true,
                        isError = errors.containsKey(f.key),
                        supportingText = errors[f.key]?.let { e -> { Text(e) } },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = c.text, textDirection = if (secret || f.type == FieldType.Number) TextDirection.Ltr else TextDirection.Content,
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = if (f.type == FieldType.Number) KeyboardType.Number else if (secret) KeyboardType.Password else KeyboardType.Text),
                        visualTransformation = if (secret && !shown) PasswordVisualTransformation() else VisualTransformation.None,
                        trailingIcon = if (secret) {
                            { Icon(if (shown) Icons.EyeOff else Icons.Eye, if (shown) "إخفاء" else "إظهار", Modifier.size(44.dp).padding(11.dp).clickable { shown = !shown }, tint = c.muted) }
                        } else null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = c.goldText, unfocusedBorderColor = c.line,
                            focusedLabelColor = c.goldText, unfocusedLabelColor = c.muted, cursorColor = c.goldText,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
fun KpiRow(schema: ModuleSchema, rows: List<Row>, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        schema.kpis.forEach { k ->
            Panel(Modifier.weight(1f)) {
                Column {
                    Text(k.value(rows).ltr(), style = NumberStyle, color = c.goldText)
                    Text(k.label, style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
        }
    }
}

@Composable
fun SchemaRow(schema: ModuleSchema, row: Row) {
    val c = AppTheme.colors
    val cols = schema.listColumns
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(row[cols.first()].orEmpty(), style = MaterialTheme.typography.bodyLarge, color = c.text)
        val rest = cols.drop(1).mapNotNull { k -> row[k]?.takeIf { it.isNotBlank() } }
        if (rest.isNotEmpty()) Text(rest.joinToString(" · ") { it.ltr() }, style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
}
