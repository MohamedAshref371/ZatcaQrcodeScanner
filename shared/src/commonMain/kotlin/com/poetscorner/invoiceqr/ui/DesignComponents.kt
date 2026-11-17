package com.poetscorner.invoiceqr.ui

import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun AppIcon(icon: DrawableResource, modifier: Modifier = Modifier,
    description: String? = null, tint: Color = LocalContentColor.current) {
    Icon(painterResource(icon), description, modifier.size(24.dp), tint)
}

@Composable
fun IconTile(icon: DrawableResource, modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    tint: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    Surface(modifier.size(44.dp), color = container, shape = MaterialTheme.shapes.small) {
        Box(contentAlignment = Alignment.Center) { AppIcon(icon, tint = tint) }
    }
}

@Composable
internal fun SectionHeading(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
