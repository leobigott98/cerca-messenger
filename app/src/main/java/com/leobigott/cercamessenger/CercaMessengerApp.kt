package com.leobigott.cercamessenger

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.leobigott.cercamessenger.core.design.theme.CercaColors
import com.leobigott.cercamessenger.ui.navigation.AppNavGraph

@Composable
fun CercaMessengerApp() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = CercaColors.Background
    ) {
        AppNavGraph()
    }
}
