package com.medidiary.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.medidiary.app.ui.theme.MediDiaryTheme
import com.medidiary.app.ui.theme.Mocha

/**
 * Design playground for the neumorphic kit. Open this file in Android Studio and use the
 * Split/Design view — no phone or emulator needed. Click "Start Interactive Mode" to press buttons.
 */
@Preview(name = "Neumorphic kit · Mocha", widthDp = 380, heightDp = 640, backgroundColor = 0xFF1E1E2E, showBackground = true)
@Composable
private fun NeuKitPreview() {
    MediDiaryTheme {
        var lang by remember { mutableStateOf("bn") }
        var retention by remember { mutableStateOf(30) }
        var text by remember { mutableStateOf("সকালে একটা Napa খেয়েছি") }
        Column(
            Modifier.background(Mocha.Base).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                NeuIconButton(Icons.Filled.Settings, "Settings", {}, tint = Mocha.Mauve)
                NeuSegmented(listOf("en" to "EN", "bn" to "BN"), lang, { lang = it })
            }
            NeuCard {
                SectionLabel("Voice model", color = Mocha.Blue)
                NeuButton({}, accent = Mocha.Blue, alwaysAccent = true) { androidx.compose.material3.Icon(Icons.Filled.Mic, null); Text("Record") }
            }
            NeuTextField(text, { text = it }, minHeight = 80.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                NeuButton({ retention = 30 }, selected = retention == 30) { Text("30 days") }
                NeuButton({ retention = 60 }, selected = retention == 60) { Text("60 days") }
            }
            MessageBanner("Saved 2 item(s) to your diary.", BannerKind.SUCCESS)
        }
    }
}
