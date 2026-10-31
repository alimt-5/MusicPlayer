package com.example.musicplayer.musicFeature.presentation.screens.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicplayer.musicFeature.domain.model.SortType
import com.example.musicplayer.musicFeature.presentation.model.HomeUiState
import com.example.musicplayer.musicFeature.presentation.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(state: HomeUiState, viewModel: MusicViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::onSearchQueryChanged,
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text("Search") },
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors()
        )
        SortChip(
            icon = { Icon(Icons.Default.SortByAlpha, contentDescription = null) },
            label = "A-Z",
            selected = state.sortType == SortType.ALPHABETICAL,
            onClick = { viewModel.chooseSortType(SortType.ALPHABETICAL) }
        )
        SortChip(
            icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
            label = "Date",
            selected = state.sortType == SortType.DATE_ADDED,
            onClick = { viewModel.chooseSortType(SortType.DATE_ADDED) }
        )
    }
}

@Composable
private fun SortChip(
    icon: @Composable () -> Unit,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .size(width = 48.dp, height = 46.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon()
        Text(label, fontSize = 8.sp)
    }
}
