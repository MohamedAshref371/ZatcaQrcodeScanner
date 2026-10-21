package com.poetscorner.invoiceqr.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.poetscorner.invoiceqr.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

internal data class PrivacyBlock(val text: String, val heading: Boolean)

internal fun privacyBlocks(markdown: String): List<PrivacyBlock> = markdown.trim()
    .split(Regex("\\r?\\n\\s*\\r?\\n"))
    .filter { it.isNotBlank() }
    .map { PrivacyBlock(it.trim().removePrefix("## ").removePrefix("# ").replace("`", ""), it.startsWith("# ") || it.startsWith("## ")) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val language = if (LocalConfiguration.current.locales[0].language == "ar") "ar" else "en"
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val blocks by produceState<List<PrivacyBlock>?>(null, language) {
        value = null
        value = withContext(Dispatchers.IO) {
            try {
                context.assets.open("privacy-policy-$language.md").bufferedReader(Charsets.UTF_8).use {
                    privacyBlocks(it.readText())
                }
            } catch (_: IOException) { emptyList() }
        }
    }
    val openLink: (String) -> Unit = { url ->
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar(context.getString(R.string.privacy_link_error)) }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
                TopAppBar(title = { Text(stringResource(R.string.privacy_policy)) }, actions = {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                })
            }) { padding ->
                LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("privacy_policy_content"),
                    contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (blocks == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    if (blocks?.isEmpty() == true) item { Text(stringResource(R.string.privacy_load_error)) }
                    items(blocks.orEmpty()) { block ->
                        SelectionContainer {
                            Text(privacyText(block.text, MaterialTheme.colorScheme.primary, openLink),
                                style = if (block.heading) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

private fun privacyText(text: String, linkColor: Color, openLink: (String) -> Unit) = buildAnnotatedString {
    var cursor = 0
    Regex("\\[([^]]+)]\\((https://[^\\s)]+)\\)").findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        val url = match.groupValues[2]
        withLink(LinkAnnotation.Url(url, styles = TextLinkStyles(style = SpanStyle(color = linkColor)),
            linkInteractionListener = { openLink(url) })) { append(match.groupValues[1]) }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}
