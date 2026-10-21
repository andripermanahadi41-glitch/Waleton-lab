package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FxModule
import com.example.ui.theme.*

@Composable
fun FxChainBar(
    modules: List<FxModule>,
    activeIndex: Int,
    onSelectModule: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        modules.forEachIndexed { index, mod ->
            val isSelected = index == activeIndex
            val categoryColor = getCategoryColor(mod.id, mod.category)

            val borderModifier = if (isSelected) {
                Modifier.border(1.5.dp, NeonCyan, RoundedCornerShape(12.dp))
            } else {
                Modifier.border(1.dp, SurfaceCardBorder, RoundedCornerShape(12.dp))
            }

            Box(
                modifier = Modifier
                    .width(84.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .then(borderModifier)
                    .background(if (isSelected) SurfaceCardActive else SurfaceCard)
                    .clickable { onSelectModule(index) }
                    .padding(8.dp)
                    .alpha(if (mod.isEnabled) 1.0f else 0.45f)
                    .testTag("fx_module_${mod.id}"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top row: ID and Status Dot
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = mod.id,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = categoryColor
                        )

                        // LED On/Off indicator dot
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (mod.isEnabled) NeonCyan else SliderTrack)
                        )
                    }

                    // Middle: Model Name
                    Text(
                        text = mod.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) TextPrimary else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Bottom: CC #
                    Text(
                        text = "CC:${mod.cc}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextTertiary
                    )
                }
            }
        }
    }
}

fun getCategoryColor(id: String, category: String): Color {
    return when (id) {
        "NR" -> ColorGate
        "WAH" -> Color(0xFFFFD600)
        "BST" -> ColorDrive
        "AMP" -> ColorAmp
        "CAB" -> ColorCab
        "EQ" -> ColorEq
        "MOD" -> ColorMod
        "DLY" -> ColorDelay
        "RVB" -> ColorReverb
        "VOL" -> Color(0xFF64B5F6)
        else -> NeonCyan
    }
}
