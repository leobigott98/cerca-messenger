package com.leobigott.cercamessenger.ui.nearby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leobigott.cercamessenger.core.design.components.StatusCard
import com.leobigott.cercamessenger.core.model.DeviceNode
import com.leobigott.cercamessenger.ui.nearby.components.DeviceNodeCard
import com.leobigott.cercamessenger.core.model.LocalizationStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyScreen(
    contentPadding: PaddingValues,
    onOpenChat: (DeviceNode) -> Unit,
    viewModel: NearbyViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val language by LocalizationStore.language.collectAsState()
    val strings = LocalizationStore.strings(language)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.nearbyTitle) },
                actions = { Icon(Icons.Default.WifiTethering, contentDescription = null) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(top = 12.dp)
                .padding(bottom = contentPadding.calculateBottomPadding())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(state.devices) { device ->
                DeviceNodeCard(device = device)
            }
        }
    }
}
