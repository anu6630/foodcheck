package com.foodcheck.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.foodcheck.presentation.theme.AppColors
import kotlinx.coroutines.delay

@Composable
fun CompactMessageDialog(
    title: String,
    message: String,
    confirmLabel: String = "OK",
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            color = AppColors.BackgroundSecondary,
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AppColors.TextMuted, modifier = Modifier.size(18.dp))
                    }
                }
                Text(message, color = AppColors.TextMuted, fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDismiss),
                    color = AppColors.Primary,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        confirmLabel,
                        modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun CompactChoiceDialog(
    title: String,
    options: List<CompactChoiceOption>,
    onSelect: (CompactChoiceOption) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            color = AppColors.BackgroundSecondary,
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AppColors.TextMuted, modifier = Modifier.size(18.dp))
                    }
                }
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(option.accent, RoundedCornerShape(50)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            option.label,
                            modifier = Modifier.weight(1f),
                            color = if (option.selected) option.accent else Color.White,
                            fontWeight = if (option.selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp,
                        )
                        if (option.selected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = option.accent, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

data class CompactChoiceOption(
    val id: String,
    val label: String,
    val accent: Color,
    val selected: Boolean = false,
)

/** Brief centered toast card; auto-dismisses. */
@Composable
fun CompactToast(
    message: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    autoDismissMs: Long = 1400,
) {
    if (!visible) return
    LaunchedEffect(message) {
        delay(autoDismissMs)
        onDismiss()
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 28.dp)
                .fillMaxWidth(),
            color = Color(0xFF1A1F35).copy(alpha = 0.96f),
            shape = RoundedCornerShape(14.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(18.dp))
                Text(message, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun CompactSavingOverlay(visible: Boolean, message: String = "Saving…") {
    if (!visible) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = AppColors.BackgroundSecondary,
            shape = RoundedCornerShape(16.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = AppColors.Primary,
                    strokeWidth = 2.dp,
                )
                Text(message, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
