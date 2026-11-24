package com.medidiary.app.ui.settings

import android.content.Intent
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medidiary.app.sync.FamilySync
import com.medidiary.app.ui.components.BannerKind
import com.medidiary.app.ui.components.MessageBanner
import com.medidiary.app.ui.components.NeuButton
import com.medidiary.app.ui.components.NeuCard
import com.medidiary.app.ui.components.NeuTextField
import com.medidiary.app.ui.components.SectionLabel
import com.medidiary.app.ui.theme.Mocha
import kotlinx.coroutines.launch

/**
 * Task 1: FAMILY SHARING. One shared diary for a parent and caregivers, each on their own phone.
 * Off → "Create shared diary" or "Join" (scan QR / paste link). On → invite QR, sync status, leave.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FamilySharingCard(sync: FamilySync, defaultName: String = "") {
    val st by sync.state.collectAsStateWithLifecycle()
    val pendingInvite by sync.pendingInvite.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Pre-filled from User details, so there is one name to type, not two.
    var name by remember { mutableStateOf(st.myName.ifBlank { defaultName }) }
    val nameToUse = name.trim().ifBlank { defaultName.trim() }.ifBlank { "Family member" }
    var label by remember { mutableStateOf("") }
    var inviteText by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var showQr by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }

    val scanner = sync.scanContract?.let { contract ->
        rememberLauncherForActivityResult(contract) { text -> if (text != null) sync.pendingInvite.value = text }
    }

    fun run(block: suspend () -> Unit) {
        working = true
        problem = null
        scope.launch {
            runCatching { block() }.onFailure { problem = it.message ?: "Something went wrong" }
            working = false
        }
    }

    NeuCard(Modifier.fillMaxWidth()) {
        SectionLabel("Family sharing", color = Mocha.Teal)
        if (!st.configured) {
            Text(
                "This build has no sync service configured. Add the Firebase keys (docs/FAMILY_SYNC_SETUP.md) and rebuild the family version.",
                color = Mocha.Yellow, style = MaterialTheme.typography.bodySmall,
            )
        }
        problem?.let { MessageBanner(it, BannerKind.ERROR) }
        if (st.projectId.isNotBlank()) {
            Text("Sync project: ${st.projectId}", color = Mocha.Overlay1, style = MaterialTheme.typography.labelSmall)
        }

        if (!st.enabled) {
            Text(
                "Keep one diary for a parent, shared with their caregivers' phones. Only entries you confirm are shared, " +
                    "end-to-end encrypted. Recordings and transcripts never leave the phone.",
                color = Mocha.Subtext0, style = MaterialTheme.typography.bodySmall,
            )
            Text("Your name (shown to the family on your entries; optional)", color = Mocha.Subtext1, style = MaterialTheme.typography.labelMedium)
            NeuTextField(name, { name = it }, singleLine = true, placeholder = "e.g. Ammu or Rafi")

            if (pendingInvite != null) {
                MessageBanner("You opened a family diary invite. Enter your name and tap Join.", BannerKind.INFO)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeuButton({ run { sync.join(pendingInvite.orEmpty(), nameToUse) } }, enabled = !working && st.configured,
                        accent = Mocha.Green, alwaysAccent = true) { Icon(Icons.Filled.Group, null); Text("Join") }
                    TextButton(onClick = { sync.pendingInvite.value = null }) { Text("Ignore", color = Mocha.Subtext0) }
                }
            } else {
                Text("Start a new shared diary (on the parent's phone, or yours)", color = Mocha.Subtext1, style = MaterialTheme.typography.labelMedium)
                NeuTextField(label, { label = it }, singleLine = true, placeholder = "Diary name, e.g. Ammu's diary")
                NeuButton({ run { sync.create(nameToUse, label) } }, enabled = !working && st.configured,
                    accent = Mocha.Teal, alwaysAccent = true) { Icon(Icons.Filled.Group, null); Text("Create shared diary") }

                Text("…or join one", color = Mocha.Subtext1, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                NeuTextField(inviteText, { inviteText = it }, placeholder = "Paste the invite link")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (scanner != null) {
                        NeuButton({ scanner.launch(Unit) }, enabled = !working && st.configured) {
                            Icon(Icons.Filled.QrCodeScanner, null); Text("Scan QR")
                        }
                    }
                    NeuButton({ run { sync.join(inviteText, nameToUse) } }, enabled = !working && inviteText.isNotBlank() && st.configured,
                        accent = Mocha.Green, alwaysAccent = true) { Text("Join") }
                }
            }
            if (working) CircularProgressIndicator(Modifier.size(22.dp), color = Mocha.Teal, strokeWidth = 2.dp)
        } else {
            Text("Shared diary: ${st.diaryLabel}", color = Mocha.Text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("You appear as “${st.myName}”", color = Mocha.Subtext1, style = MaterialTheme.typography.bodySmall)
            Text(
                when {
                    st.syncing -> "Syncing…"
                    st.lastSyncAt != null -> "Last synced " + DateUtils.getRelativeTimeSpanString(st.lastSyncAt!!)
                    else -> "Waiting for the first sync"
                },
                color = Mocha.Green, style = MaterialTheme.typography.bodySmall,
            )
            st.error?.let { Text(it, color = Mocha.Yellow, style = MaterialTheme.typography.bodySmall) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NeuButton({ showQr = true }, accent = Mocha.Teal, alwaysAccent = true) { Icon(Icons.Filled.QrCode, null); Text("Invite family") }
                NeuButton({ sync.syncNow() }) { Icon(Icons.Filled.Sync, null); Text("Sync now") }
                TextButton(onClick = { confirmLeave = true }) { Text("Leave", color = Mocha.Red) }
            }
        }
    }

    if (showQr) {
        val link = sync.inviteLink()
        val qr = remember(link) { sync.inviteQr(720) }
        AlertDialog(
            onDismissRequest = { showQr = false },
            containerColor = Mocha.Base,
            title = { Text("Invite to ${st.diaryLabel}", color = Mocha.Teal) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    qr?.let {
                        Image(it.asImageBitmap(), "Invite QR code",
                            Modifier.size(240.dp).background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(12.dp)).padding(8.dp))
                    }
                    Text(
                        "On the other phone: MediDiary → Settings → Family sharing → Scan QR. " +
                            "Anyone with this code or link can read and add to the diary, so share it only with family.",
                        color = Mocha.Subtext1, style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(
                        Intent.EXTRA_TEXT,
                        "Join our MediDiary family diary. Open MediDiary → Settings → Family sharing, paste this and tap Join:\n$link",
                    )
                    context.startActivity(Intent.createChooser(send, "Share invite"))
                }) { Icon(Icons.Filled.Share, null, tint = Mocha.Blue); Text(" Share link", color = Mocha.Blue) }
            },
            dismissButton = { TextButton(onClick = { showQr = false }) { Text("Done", color = Mocha.Subtext0) } },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = Mocha.Base,
            title = { Text("Stop sharing on this phone?", color = Mocha.Text) },
            text = { Text("Entries already on this phone stay here. New entries won't be shared until you join again.", color = Mocha.Subtext1) },
            confirmButton = { TextButton(onClick = { confirmLeave = false; run { sync.leave() } }) { Text("Leave", color = Mocha.Red) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancel", color = Mocha.Subtext0) } },
        )
    }
}
