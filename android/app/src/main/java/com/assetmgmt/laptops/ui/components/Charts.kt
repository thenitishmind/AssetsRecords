package com.assetmgmt.laptops.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.assetmgmt.laptops.data.remote.BranchStock
import com.assetmgmt.laptops.ui.theme.StatusAssigned
import com.assetmgmt.laptops.ui.theme.StatusAvailable
import com.assetmgmt.laptops.ui.theme.StatusDead
import com.assetmgmt.laptops.ui.theme.StatusRepair
import com.assetmgmt.laptops.ui.theme.StatusRetired

data class Slice(val label: String, val value: Int, val color: Color)

@Composable
fun DonutChart(slices: List<Slice>, centerTop: String, centerBottom: String, modifier: Modifier = Modifier, size: Dp = 170.dp) {
    val total = slices.sumOf { it.value }.coerceAtLeast(1)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(slices) { progress.snapTo(0f); progress.animateTo(1f, tween(900)) }
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = size.toPx() * 0.16f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            var start = -90f
            slices.filter { it.value > 0 }.forEach { s ->
                val sweep = 360f * s.value / total * progress.value
                drawArc(s.color, start, (sweep - 1.5f).coerceAtLeast(0.1f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                start += 360f * s.value / total * progress.value
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTop, style = MaterialTheme.typography.headlineMedium)
            Text(centerBottom, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Legend(slices: List<Slice>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        slices.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(s.color))
                Spacer(Modifier.width(8.dp))
                Text(s.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(s.value.toString(), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

data class Bar(val label: String, val value: Double)

/** Simple animated column chart (used for repair cost per month). */
@Composable
fun BarChart(bars: List<Bar>, color: Color, valueFormat: (Double) -> String, modifier: Modifier = Modifier, height: Dp = 170.dp) {
    val max = (bars.maxOfOrNull { it.value } ?: 0.0).coerceAtLeast(1.0)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(bars) { progress.snapTo(0f); progress.animateTo(1f, tween(800)) }
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        bars.forEach { b ->
            Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(valueFormat(b.value), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Clip, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.height(height).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.6f)
                            .fillMaxHeight(((b.value / max).toFloat() * progress.value).coerceAtLeast(0.02f))
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(color),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(b.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** Horizontal stacked bar for one branch: assigned / available / repair / dead / sold / retired. */
@Composable
fun StackedStatusBar(b: BranchStock, modifier: Modifier = Modifier) {
    val parts = listOf(b.assigned to StatusAssigned, b.available to StatusAvailable, b.repair to StatusRepair, b.dead to StatusDead, b.sold to StatusRetired, b.retired to StatusRetired)
    Row(modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        parts.filter { it.first > 0 }.forEach { (n, c) -> Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(c)) }
    }
}

val StatusSlices: (assigned: Int, available: Int, repair: Int, dead: Int, sold: Int, retired: Int) -> List<Slice> = { as_, av, re, de, so, rt ->
    listOf(
        Slice("Available", av, StatusAvailable),
        Slice("Assigned", as_, StatusAssigned),
        Slice("Repair", re, StatusRepair),
        Slice("Dead", de, StatusDead),
        Slice("Sold", so, StatusRetired),
        Slice("Retired", rt, StatusRetired),
    )
}
