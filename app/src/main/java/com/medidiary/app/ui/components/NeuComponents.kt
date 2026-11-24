package com.medidiary.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medidiary.app.ui.theme.Mocha
import com.medidiary.app.ui.theme.neumorphic

/** Raised neumorphic container for a screen section. */
@Composable
fun NeuCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    elevation: Dp = 8.dp,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.neumorphic(cornerRadius, elevation).padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** Small caps-style section heading, e.g. "VOICE MODEL". */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Mocha.Lavender) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = color,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        style = MaterialTheme.typography.labelLarge,
    )
}

/**
 * Neumorphic button: raised at rest, inset while pressed or when [selected].
 * [accent] colours the content when selected (and always, if [alwaysAccent]).
 */
@Composable
fun NeuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    accent: Color = Mocha.Blue,
    alwaysAccent: Boolean = false,
    cornerRadius: Dp = 16.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val inset = (pressed || selected) && enabled
    val contentColor = when {
        !enabled -> Mocha.Overlay0
        selected || alwaysAccent -> accent
        else -> Mocha.Text
    }
    Row(
        modifier
            .heightIn(min = 44.dp)
            .neumorphic(cornerRadius, if (inset) 3.dp else 6.dp, inset = inset)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }
}

@Composable
fun NeuIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Mocha.Text,
    size: Dp = 48.dp,
) {
    NeuButton(
        onClick = onClick,
        modifier = modifier.size(size),
        cornerRadius = size / 2,
        accent = tint,
        alwaysAccent = true,
        contentPadding = PaddingValues(0.dp),
    ) { Icon(icon, contentDescription) }
}

/** EN | BN style toggle: an inset track with the selected option raised. */
@Composable
fun NeuSegmented(
    options: List<Pair<String, String>>, // value to label
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Mocha.Blue,
) {
    Row(
        modifier.neumorphic(cornerRadius = 18.dp, elevation = 4.dp, inset = true).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSel = value == selected
            val interaction = remember(value) { MutableInteractionSource() }
            Box(
                Modifier
                    .then(if (isSel) Modifier.neumorphic(cornerRadius = 14.dp, elevation = 4.dp) else Modifier)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Tab) { onSelect(value) }
                    .semantics { this.selected = isSel }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (isSel) accent else Mocha.Overlay1,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** Text input sitting in an inset well. */
@Composable
fun NeuTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = false,
    minHeight: Dp = 52.dp,
    enabled: Boolean = true,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .neumorphic(cornerRadius = 16.dp, elevation = 4.dp, inset = true),
        enabled = enabled,
        singleLine = singleLine,
        placeholder = placeholder?.let { { Text(it, color = Mocha.Overlay0) } },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedTextColor = Mocha.Text,
            unfocusedTextColor = Mocha.Text,
            disabledTextColor = Mocha.Subtext0,
            cursorColor = Mocha.Rosewater,
        ),
    )
}
