package com.hrcoach.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hrcoach.domain.emblem.Emblem

@Composable
fun EmblemPicker(
    selected: Emblem,
    onSelect: (Emblem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(4.dp)
    ) {
        items(Emblem.entries.toList()) { emblem ->
            EmblemIconWithRing(
                emblem = emblem,
                size = if (emblem == selected) 50.dp else 46.dp,
                ringWidth = if (emblem == selected) 2.5.dp else 1.dp,
                modifier = Modifier.clickable { onSelect(emblem) }
            )
        }
    }
}
