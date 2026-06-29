package com.leobigott.cercamessenger.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.leobigott.cercamessenger.core.model.LocalizationStore
import com.leobigott.cercamessenger.ui.chat.ChatScreen
import com.leobigott.cercamessenger.ui.chat.ChatViewModel
import com.leobigott.cercamessenger.ui.chat.ChatViewModelFactory
import com.leobigott.cercamessenger.ui.conversations.ConversationsScreen
import com.leobigott.cercamessenger.ui.crisis.CrisisScreen
import com.leobigott.cercamessenger.ui.contacts.ContactsScreen
import com.leobigott.cercamessenger.ui.nearby.NearbyScreen
import com.leobigott.cercamessenger.ui.guide.GuideScreen
import com.leobigott.cercamessenger.ui.settings.SettingsScreen

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val language by LocalizationStore.language.collectAsState()
    val strings = LocalizationStore.strings(language)
    val bottomTabs = listOf(
        BottomTab(AppRoute.Crisis.route, strings.bottomCrisis, Icons.Default.Warning),
        BottomTab(AppRoute.Conversations.route, strings.bottomChats, Icons.Default.Chat),
        BottomTab(AppRoute.Nearby.route, strings.bottomNearby, Icons.Default.WifiTethering),
        //BottomTab(AppRoute.Guide.route, strings.bottomGuide, Icons.Default.MenuBook),
        BottomTab(AppRoute.Contacts.route, strings.bottomContacts, Icons.Default.PersonAdd),
        BottomTab(AppRoute.Settings.route, strings.bottomSettings, Icons.Default.Settings)
    )
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = bottomTabs.any { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(AppRoute.Crisis.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppRoute.Crisis.route
        ) {
            composable(AppRoute.Crisis.route) {
                CrisisScreen(contentPadding = padding)
            }
            composable(AppRoute.Conversations.route) {
                ConversationsScreen(
                    contentPadding = padding,
                    onOpenChat = { conversation ->
                        navController.navigate(
                            AppRoute.Chat.create(conversation.id, conversation.peerId, conversation.peerName)
                        )
                    }
                )
            }
            composable(AppRoute.Nearby.route) {
                NearbyScreen(
                    contentPadding = padding,
                    onOpenChat = { device ->
                        navController.navigate(
                            AppRoute.Chat.create("conv-${device.id}", device.id, device.displayName)
                        )
                    }
                )
            }
            composable(AppRoute.Guide.route) {
                GuideScreen(contentPadding = padding)
            }
            composable(AppRoute.Contacts.route) {
                ContactsScreen(
                    contentPadding = padding,
                    onOpenChat = { contact ->
                        navController.navigate(
                            AppRoute.Chat.create("conv-${contact.nodeId}", contact.nodeId, contact.displayName)
                        )
                    }
                )
            }
            composable(AppRoute.Settings.route) {
                SettingsScreen(contentPadding = padding)
            }
            composable(AppRoute.Chat.route) { entry ->
                val conversationId = entry.arguments?.getString("conversationId") ?: "conv-patricia"
                val peerId = entry.arguments?.getString("peerId") ?: "node-patricia"
                val peerName = entry.arguments?.getString("peerName") ?: "Patricia"
                val vm: ChatViewModel = viewModel(
                    factory = ChatViewModelFactory(conversationId, peerId, peerName)
                )
                ChatScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
