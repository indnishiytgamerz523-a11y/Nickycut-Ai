package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.components.NickyCutTopBar
import com.example.ui.components.ToolGridCard
import com.example.ui.navigation.AppRoute
import com.example.ui.navigation.StudioToolCatalog
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.EmeraldDim
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.StudioBackgroundBrush
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AllToolsScreen(
    onNavigate: (AppRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = remember {
        listOf("All", "Core Studio", "Typography & Captions", "Audio & Voice", "Visual FX", "Storytelling", "Gemini AI")
    }
    var selectedCategory by remember { mutableStateOf("All") }
    var search by remember { mutableStateOf("") }

    val filtered = remember(selectedCategory, search) {
        StudioToolCatalog.allTools.filter { tool ->
            val catMatch = selectedCategory == "All" || tool.category == selectedCategory
            val q = search.trim().lowercase()
            val queryMatch = q.isEmpty() || tool.title.lowercase().contains(q) || tool.subtitle.lowercase().contains(q)
            catMatch && queryMatch
        }
    }
    val rows = remember(filtered) { filtered.chunked(2) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StudioBackgroundBrush)
            .testTag("all_tools_screen")
    ) {
        NickyCutTopBar(
            title = "All Creator Tools",
            subtitle = "Offline media engines & optional Gemini AI"
        )

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Filter tools...", color = TextSecondary) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = NeonEmerald) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceCard,
                unfocusedContainerColor = SurfaceCard,
                focusedBorderColor = NeonEmerald,
                unfocusedBorderColor = BorderSubtle,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldDim,
                        selectedLabelColor = NeonEmerald,
                        containerColor = SurfaceCard,
                        labelColor = TextSecondary
                    )
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(rows) { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { tool ->
                        ToolGridCard(
                            tool = tool,
                            onClick = { onNavigate(tool.route) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
