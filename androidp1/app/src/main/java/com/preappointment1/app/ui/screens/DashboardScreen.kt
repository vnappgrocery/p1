package com.preappointment1.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preappointment1.app.data.AuthHelper
import com.preappointment1.app.data.api.ApiClient
import com.preappointment1.app.data.model.AgentResponse
import com.preappointment1.app.data.model.SubscriptionResponse
import com.preappointment1.app.ui.components.*
import com.preappointment1.app.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class FollowUpUi(
    val id: String,
    val title: String,
    val daysRemaining: Int,
    val totalDays: Int,
    val progress: Float,
    val isActive: Boolean,
    val rules: com.preappointment1.app.data.model.FollowUpRules?,
    val schedule: Map<String, List<String>>?,
    val startsAt: String = "",
    val expiresAt: String = ""
)

@Composable
fun DashboardScreen(
    followUps: List<FollowUpUi>,
    onNewFollowUp: () -> Unit,
    onOpenJourney: (FollowUpUi) -> Unit,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeFollowUps = remember(followUps) {
        followUps.filter { it.isActive }
    }
    var showComingSoon by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (showComingSoon) {
        ComingSoonDialog(onDismiss = { showComingSoon = false })
    }


    Scaffold(
        containerColor = White,
        modifier = modifier
    ) { padding ->
        if (activeFollowUps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Welcome to Pre-Appointment 1",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = Black,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "You have no active trackings selected. Select a conversation from the top right menu, or start a new tracking protocol.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Gray600,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    LpmPrimaryButton(text = "Start a new tracking", onClick = onNewFollowUp)
                }
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
            ) {
                items(activeFollowUps) { followUp ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenJourney(followUp) }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Gray200, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = followUp.title.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = Gray600
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = followUp.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Black
                            )
                            Text(
                                text = "Active tracking protocol",
                                style = MaterialTheme.typography.bodySmall,
                                color = Gray400
                            )
                        }
                    }
                    androidx.compose.material3.Divider(
                        color = Gray200,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        }
    }
}





@Composable
fun ComingSoonDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        shape = RoundedCornerShape(4.dp),
        title = {
            Text(
                "Coming soon",
                fontWeight = FontWeight.Bold,
                color = Black
            )
        },
        text = {
            Text(
                "Scanning prescriptions and doctor protocols will be available in a future update.",
                color = Gray600
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", color = Black, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

fun SubscriptionResponse.toFollowUpUi(agents: Map<String, AgentResponse>): FollowUpUi {
    val start = runCatching { Instant.parse(starts_at) }.getOrNull() ?: Instant.now()
    val end = runCatching { Instant.parse(expires_at) }.getOrNull() ?: start.plus(14, ChronoUnit.DAYS)
    val now = Instant.now()
    val totalDays = ChronoUnit.DAYS.between(start, end).coerceAtLeast(1).toInt()
    val elapsedDays = ChronoUnit.DAYS.between(start, now).coerceAtLeast(0)
    val daysRemaining = ChronoUnit.DAYS.between(now, end).coerceAtLeast(0).toInt()
    val progress = (elapsedDays.toFloat() / totalDays.toFloat()).coerceIn(0f, 1f)
    val startDateStr = start.toString().take(10)
    val agent = agents[agent_id]
    val title = parameters?.get("title")?.toString() ?: agent?.name ?: "Tracking from $startDateStr"

    var parsedRules: com.preappointment1.app.data.model.FollowUpRules? = null
    val rulesMap = parameters?.get("rules") as? Map<*, *>
    if (rulesMap != null) {
        parsedRules = com.preappointment1.app.data.model.FollowUpRules(
            temperature = rulesMap["temperature"] as? Boolean ?: false,
            pain = rulesMap["pain"] as? Boolean ?: false,
            photos = rulesMap["photos"] as? Boolean ?: false,
            smartwatch = rulesMap["smartwatch"] as? Boolean ?: false,
            bloodPressure = rulesMap["blood_pressure"] as? Boolean ?: false
        )
    }

    var parsedSchedule: Map<String, List<String>>? = null
    val scheduleMap = parameters?.get("schedule") as? Map<*, *>
    if (scheduleMap != null) {
        parsedSchedule = scheduleMap.mapNotNull { (k, v) ->
            val key = k as? String
            val valueList = (v as? List<*>)?.mapNotNull { it as? String }
            if (key != null && valueList != null) key to valueList else null
        }.toMap()
    }

    return FollowUpUi(
        id = id,
        title = title,
        daysRemaining = daysRemaining,
        totalDays = totalDays,
        progress = progress,
        isActive = daysRemaining > 0,
        rules = parsedRules,
        schedule = parsedSchedule,
        startsAt = starts_at,
        expiresAt = expires_at
    )
}
