package com.medidiary.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.medidiary.app.ui.theme.Mocha

enum class BannerKind { ERROR, INFO, MOCK, SUCCESS }

@Composable
fun MessageBanner(text: String, kind: BannerKind, modifier: Modifier = Modifier) {
    val (icon, accent) = when (kind) {
        BannerKind.ERROR -> Icons.Outlined.ErrorOutline to Mocha.Red
        BannerKind.INFO -> Icons.Outlined.Info to Mocha.Sapphire
        BannerKind.MOCK -> Icons.Outlined.Science to Mocha.Yellow
        BannerKind.SUCCESS -> Icons.Outlined.CheckCircle to Mocha.Green
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(14.dp))
            .background(Mocha.Surface0),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
        Spacer(Modifier.width(10.dp))
        Icon(icon, contentDescription = null, tint = accent)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Mocha.Text, modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp))
    }
}

const val MOCK_MODE_TEXT =
    "Demo mode: model files not installed, so AI output is scripted. Settings → Load model."
