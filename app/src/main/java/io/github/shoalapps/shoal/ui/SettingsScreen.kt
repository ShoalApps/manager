@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.shoalapps.shoal.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.shoalapps.shoal.Graph
import io.github.shoalapps.shoal.data.UpdateWorker

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = Graph.store
    val s by store.settings.collectAsState()
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()).wrapContentWidth().widthIn(max = 720.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
            item(key = "support") { SupportCard { open("https://liberapay.com/ShoalApps") } }
            group("Installing") {
                row { sh ->
                    val keys = listOf("auto", "standard", "shizuku", "root")
                    Choices(
                        "Install with",
                        "Automatic uses Shevery or Shizuku if running, then root if you've allowed it, otherwise Android's installer.",
                        listOf("Automatic", "Android", "Shevery", "Root"),
                        keys.indexOf(s.installMethod).coerceAtLeast(0), sh,
                    ) { i -> store.updateSettings { it.copy(installMethod = keys[i]) } }
                }
            }
            group("Background updates") {
                row { sh ->
                    Toggle("Update automatically", "Check once a day; install silently with Shevery (or Shizuku) or root, otherwise notify", s.autoUpdate, sh) { v ->
                        store.updateSettings { it.copy(autoUpdate = v) }
                    }
                }
                row { sh ->
                    Toggle("Only on Wi-Fi", null, s.wifiOnly, sh) { v ->
                        store.updateSettings { it.copy(wifiOnly = v) }
                        UpdateWorker.schedule(context)
                    }
                }
            }
            group("Appearance") {
                row { sh -> Choices("Theme", null, listOf("Auto", "Light", "Dark"), s.theme, sh) { i -> store.updateSettings { it.copy(theme = i) } } }
                if (Build.VERSION.SDK_INT >= 31) {
                    row { sh -> Toggle("Dynamic colour", "Match colours to your wallpaper", s.dynamicColor, sh) { v -> store.updateSettings { it.copy(dynamicColor = v) } } }
                }
            }
            group("About") {
                row { sh -> Link("What's new", "Changes in this version", sh) { io.github.shoalapps.shoal.WhatsNew.open() } }
                if (io.github.shoalapps.shoal.Telemetry.available) row { sh -> Toggle("Send error reports", "Anonymous crash and error reports help fix bugs. Never your accounts or history.", io.github.shoalapps.shoal.Telemetry.enabled, sh) { v -> io.github.shoalapps.shoal.Telemetry.setEnabled(context, v) } }
                row { sh -> Link("Privacy policy", "What Shoal sends, and to whom", sh) { open("https://shoalapps.github.io/privacy.html") } }
                row { sh -> Link("Legal", "Licences, trademarks and notices", sh) { open("https://shoalapps.github.io/legal.html") } }
                row { sh -> Link("Shoal $version", "github.com/ShoalApps/shoal", sh) { open("https://github.com/ShoalApps/shoal") } }
                row { sh -> Link("Shoal", "shoalapps.github.io: all the apps and their credits", sh) { open("https://shoalapps.github.io") } }
                row { sh -> Link("GNU GPL v3 or later", "Free software. Installs only apps signed by Shoal.", sh) { open("https://www.gnu.org/licenses/gpl-3.0.html") } }
                row { sh -> Link("Shevery", "Recommended for silent updates: a Material 3 fork of Shizuku, Apache License 2.0", sh) { open("https://github.com/HmnDev-Tech/shevery") } }
                row { sh -> Link("Shizuku API", "Apache License 2.0, by RikkaApps", sh) { open("https://github.com/RikkaApps/Shizuku-API") } }
            }
        }
    }
}

private class GroupBuilder {
    val rows = mutableListOf<@Composable (ListItemShapes) -> Unit>()
    fun row(content: @Composable (ListItemShapes) -> Unit) { rows += content }
}

private fun LazyListScope.group(title: String, build: GroupBuilder.() -> Unit) {
    val rows = GroupBuilder().apply(build).rows
    if (rows.isEmpty()) return
    item(key = "t-$title") {
        Text(title, style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 32.dp, top = 24.dp, bottom = 8.dp))
    }
    rows.forEachIndexed { i, r ->
        item(key = "$title-$i") {
            Box(Modifier.padding(horizontal = 16.dp, vertical = ListItemDefaults.SegmentedGap / 2)) { r(ListItemDefaults.segmentedShapes(i, rows.size)) }
        }
    }
}

@Composable
private fun tileColors() = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    selectedContentColor = MaterialTheme.colorScheme.onSurface,
    selectedSupportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun Toggle(title: String, body: String?, checked: Boolean, shapes: ListItemShapes, onChange: (Boolean) -> Unit) {
    SegmentedListItem(
        checked = checked, onCheckedChange = onChange, shapes = shapes,
        supportingContent = body?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = tileColors(),
    ) { Text(title) }
}

@Composable
private fun Link(title: String, body: String?, shapes: ListItemShapes, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick, shapes = shapes,
        supportingContent = body?.let { { Text(it) } },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
        colors = tileColors(),
    ) { Text(title) }
}

@Composable
private fun Choices(title: String, body: String?, labels: List<String>, selected: Int, shapes: ListItemShapes, onSelect: (Int) -> Unit) {
    Surface(shape = shapes.shape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                labels.forEachIndexed { i, l ->
                    SegmentedButton(selected = selected == i, onClick = { onSelect(i) }, shape = SegmentedButtonDefaults.itemShape(i, labels.size)) { Text(l, maxLines = 1) }
                }
            }
        }
    }
}
