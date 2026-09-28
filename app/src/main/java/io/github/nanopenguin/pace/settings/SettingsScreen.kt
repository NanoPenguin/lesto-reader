package io.github.nanopenguin.pace.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nanopenguin.pace.R
import io.github.nanopenguin.pace.appContainer
import io.github.nanopenguin.pace.ui.BackButton
import io.github.nanopenguin.pace.ui.SpeedControl
import io.github.nanopenguin.pace.ui.theme.PaceTheme

private const val SOURCE_URL = "https://github.com/nanopenguin/pace"

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel = viewModel { SettingsViewModel(context.appContainer.settings) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    val uriHandler = LocalUriHandler.current
    val resources = LocalResources.current

    SettingsScreen(
        state = state,
        version = version,
        onBack = onBack,
        onThemeChange = viewModel::setTheme,
        onTextSizeChange = viewModel::setTextSize,
        onShowContextChange = viewModel::setShowContext,
        onSlower = viewModel::slower,
        onFaster = viewModel::faster,
        // Without a browser there is nowhere to open the link; the address is shown anyway.
        onOpenSource = { runCatching { uriHandler.openUri(SOURCE_URL) } },
        thirdPartyLicenses = {
            resources.openRawResource(R.raw.third_party_licenses).bufferedReader().use { it.readText() }
        },
    )
}

@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    version: String,
    onBack: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onTextSizeChange: (TextSize) -> Unit,
    onShowContextChange: (Boolean) -> Unit,
    onSlower: () -> Unit,
    onFaster: () -> Unit,
    onOpenSource: () -> Unit,
    thirdPartyLicenses: () -> String,
    modifier: Modifier = Modifier,
) {
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    val settings = state.settings

    Column(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(onClick = onBack)
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (!state.isLoaded) return@Column

        Column(
            modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            SectionLabel(stringResource(R.string.settings_theme))
            Choices(
                options = ThemeMode.entries,
                selected = settings.theme,
                label = {
                    stringResource(
                        when (it) {
                            ThemeMode.System -> R.string.settings_theme_system
                            ThemeMode.Light -> R.string.settings_theme_light
                            ThemeMode.Dark -> R.string.settings_theme_dark
                        },
                    )
                },
                onSelect = onThemeChange,
            )

            SectionLabel(stringResource(R.string.settings_text_size))
            Choices(
                options = TextSize.entries,
                selected = settings.textSize,
                label = {
                    stringResource(
                        when (it) {
                            TextSize.Small -> R.string.settings_text_size_small
                            TextSize.Medium -> R.string.settings_text_size_medium
                            TextSize.Large -> R.string.settings_text_size_large
                        },
                    )
                },
                onSelect = onTextSizeChange,
            )

            SectionLabel(stringResource(R.string.settings_reading))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .toggleable(value = settings.showContext, role = Role.Switch, onValueChange = onShowContextChange),
            ) {
                Text(
                    text = stringResource(R.string.settings_show_context),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = settings.showContext, onCheckedChange = null)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_reading_speed),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                SpeedControl(settings.wordsPerMinute, onSlower, onFaster)
            }

            SectionLabel(stringResource(R.string.settings_about))
            AboutRow(stringResource(R.string.settings_version), version)
            AboutRow(stringResource(R.string.settings_license), stringResource(R.string.settings_license_name))
            AboutRow(stringResource(R.string.settings_source), SOURCE_URL.removePrefix("https://"), onClick = onOpenSource)
            AboutRow(stringResource(R.string.settings_third_party_licenses), null, onClick = { showLicenses = true })
        }
    }

    if (showLicenses) {
        LicensesDialog(thirdPartyLicenses, onDismiss = { showLicenses = false })
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 32.dp, bottom = 12.dp).semantics { heading() },
    )
}

@Composable
private fun <T> Choices(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(label(option)) },
            )
        }
    }
}

@Composable
private fun AboutRow(
    title: String,
    value: String?,
    onClick: (() -> Unit)? = null,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier =
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        if (value != null) {
            Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LicensesDialog(
    licenses: () -> String,
    onDismiss: () -> Unit,
) {
    val text = remember(licenses) { licenses() }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close)) } },
        title = { Text(stringResource(R.string.settings_third_party_licenses)) },
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    PaceTheme {
        SettingsScreen(
            state = SettingsUiState(isLoaded = true, settings = Settings()),
            version = "0.1.0",
            onBack = {},
            onThemeChange = {},
            onTextSizeChange = {},
            onShowContextChange = {},
            onSlower = {},
            onFaster = {},
            onOpenSource = {},
            thirdPartyLicenses = { "Licenses" },
        )
    }
}
