package com.app.snapmind.presentation.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.snapmind.R
import com.app.snapmind.domain.model.PermissionKey
import com.app.snapmind.domain.model.PermissionStatus
import com.app.snapmind.domain.usecase.ResolvePermissionStateUseCase

/**
 * One card per permission, each with a one-sentence reason in plain language.
 *
 * Deliberately not a blocking wizard: the app stays usable via the Share Sheet even if the
 * user declines everything, and "Skip for now" is always available (spec.md 5.3).
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refresh() }

    val mediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringRes(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringRes(R.string.onboarding_intro),
            style = MaterialTheme.typography.bodyMedium
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionCard(
                title = stringRes(R.string.perm_notifications_title),
                reason = stringRes(R.string.perm_notifications_reason),
                status = state.statusOf(PermissionKey.NOTIFICATIONS),
                actionLabel = stringRes(R.string.action_allow),
                onAction = {
                    if (state.statusOf(PermissionKey.NOTIFICATIONS) ==
                        PermissionStatus.PERMANENTLY_DENIED
                    ) {
                        PermissionIntents.appSettings(context)
                    } else {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
        }

        PermissionCard(
            title = stringRes(R.string.perm_media_title),
            reason = stringRes(R.string.perm_media_reason),
            status = state.statusOf(PermissionKey.MEDIA_IMAGES),
            actionLabel = stringRes(R.string.action_allow),
            onAction = {
                val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
                } else {
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                mediaLauncher.launch(perms)
            }
        )

        PermissionCard(
            title = stringRes(R.string.perm_battery_title),
            reason = stringRes(R.string.perm_battery_reason),
            status = state.statusOf(PermissionKey.BATTERY_EXEMPTION),
            actionLabel = stringRes(R.string.action_open_settings),
            onAction = { PermissionIntents.batteryOptimisation(context) }
        )

        if (ResolvePermissionStateUseCase.isRestrictiveOem()) {
            PermissionCard(
                title = stringRes(R.string.perm_autostart_title),
                reason = stringRes(R.string.perm_autostart_reason),
                status = state.statusOf(PermissionKey.OEM_AUTOSTART),
                actionLabel = stringRes(R.string.action_open_settings),
                onAction = { PermissionIntents.appSettings(context) }
            )
        }

        PermissionCard(
            title = stringRes(R.string.perm_hibernation_title),
            reason = stringRes(R.string.perm_hibernation_reason),
            status = state.statusOf(PermissionKey.HIBERNATION_DISABLED),
            actionLabel = stringRes(R.string.action_open_settings),
            onAction = { PermissionIntents.hibernation(context) }
        )

        PermissionCard(
            title = stringRes(R.string.perm_dnd_title),
            reason = stringRes(R.string.perm_dnd_reason),
            status = state.statusOf(PermissionKey.DND_EXCEPTION),
            actionLabel = stringRes(R.string.action_open_settings),
            onAction = { PermissionIntents.dndPolicyAccess(context) }
        )

        Button(
            onClick = {
                viewModel.finish()
                onDone()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringRes(R.string.onboarding_continue))
        }

        TextButton(
            onClick = {
                viewModel.finish()
                onDone()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringRes(R.string.onboarding_skip))
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    reason: String,
    status: PermissionStatus,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = statusLabel(status),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Text(reason, style = MaterialTheme.typography.bodySmall)

            if (status != PermissionStatus.GRANTED &&
                status != PermissionStatus.NOT_APPLICABLE
            ) {
                OutlinedButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun statusLabel(status: PermissionStatus): String = when (status) {
    PermissionStatus.GRANTED -> stringRes(R.string.status_granted)
    PermissionStatus.NOT_APPLICABLE -> stringRes(R.string.status_not_needed)
    PermissionStatus.UNKNOWN -> stringRes(R.string.status_check_manually)
    else -> stringRes(R.string.status_missing)
}

@Composable
private fun stringRes(id: Int): String =
    androidx.compose.ui.res.stringResource(id)
