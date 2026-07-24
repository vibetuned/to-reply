package com.vibetuned.to_reply.ui.training

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vibetuned.to_reply.data.model.SpeakerStat

/**
 * Character picker: every speaker with at least one attributed dialogue line, busiest first.
 * Multi-select — the sheet stays open while roles are toggled so a user can pick several
 * characters (e.g. rehearsing two parts, or a duo practicing together on one device). Shown
 * automatically on the first open of a play (no character chosen yet) and on demand from the
 * top bar. Dismissing with nothing selected is allowed — the play is then just listened to,
 * nothing mutes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakerPickerSheet(
    speakers: List<SpeakerStat>,
    selectedSpeakers: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Who are you rehearsing?",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Selected characters' lines are muted so you can speak them aloud.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onDismiss) { Text("Done") }
        }
        Spacer(Modifier.padding(top = 8.dp))
        LazyColumn {
            items(speakers, key = { it.name }) { stat ->
                val isSelected = stat.name in selectedSpeakers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(stat.name) }
                        .padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.RecordVoiceOver,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stat.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        if (stat.lineCount == 1) "1 line" else "${stat.lineCount} lines",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Visual only — the whole row is the toggle target, which keeps a single
                    // event path (a checkbox with its own onCheckedChange would double-fire).
                    Checkbox(checked = isSelected, onCheckedChange = null,
                        modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
