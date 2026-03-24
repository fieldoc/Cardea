package com.hrcoach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaTheme

@Composable
fun EmblemPicker(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        items(EmblemRegistry.allIds) { emblemId ->
            val isSelected = emblemId == selected
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) CardeaGradient
                        else Brush.linearGradient(
                            listOf(
                                CardeaTheme.colors.textTertiary.copy(alpha = 0.3f),
                                CardeaTheme.colors.textTertiary.copy(alpha = 0.1f)
                            )
                        )
                    )
                    .clickable { onSelect(emblemId) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(CardeaTheme.colors.bgPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    EmblemIcon(
                        emblemId = emblemId,
                        size = 28.dp,
                        tinted = isSelected
                    )
                }
            }
        }
    }
}
