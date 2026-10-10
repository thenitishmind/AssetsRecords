package com.assetmgmt.laptops.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Col(val title: String, val width: Dp = 110.dp, val numeric: Boolean = false, val badge: Boolean = false)

/** Enterprise-style data table with sticky-looking header and horizontal scrolling. */
@Composable
fun DataTable(
    cols: List<Col>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
    footer: List<String>? = null,
    onRowClick: ((Int) -> Unit)? = null,
) {
    val scroll = rememberScrollState()
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface).horizontalScroll(scroll),
    ) {
        Row(Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))) {
            cols.forEach { c -> TableCell(c, c.title, header = true) }
        }
        rows.forEachIndexed { i, r ->
            Row(
                Modifier
                    .background(if (i % 2 == 1) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface)
                    .then(if (onRowClick != null) Modifier.clickable { onRowClick(i) } else Modifier),
            ) {
                cols.forEachIndexed { k, c -> TableCell(c, r.getOrElse(k) { "" }) }
            }
        }
        if (footer != null) {
            Row(Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))) {
                cols.forEachIndexed { k, c -> TableCell(c, footer.getOrElse(k) { "" }, header = true) }
            }
        }
    }
}

@Composable
private fun TableCell(c: Col, text: String, header: Boolean = false) {
    Box(
        Modifier.width(c.width).heightIn(min = 44.dp).padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = if (c.numeric) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        if (c.badge && !header && text.isNotBlank()) {
            StatusBadge(text)
        } else {
            Text(
                text,
                style = if (header) MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodySmall,
                color = if (header) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (c.numeric) TextAlign.End else TextAlign.Start,
            )
        }
    }
}
