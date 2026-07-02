package com.leobigott.cercamessenger

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.leobigott.cercamessenger.core.design.theme.CercaMessengerTheme
import com.leobigott.cercamessenger.protocol.ProtocolEngineProvider
import com.leobigott.cercamessenger.protocol.nearby.NearbyLifecycleService
import com.leobigott.cercamessenger.settings.CercaSettingsStore
import android.net.Uri
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ProtocolEngineProvider.init(applicationContext)
        CercaSettingsStore.load(applicationContext)

        setContent {
            CercaMessengerTheme {
                PermissionGate {
                    LaunchedEffect(Unit) {
                        startNearbyService()

//                        runCatching {
//                            ProtocolEngineProvider.engine.restartNearby()
//                        }
                    }

                    CercaMessengerApp()
                }
            }
        }
    }

    private fun startNearbyService() {
        val intent = Intent(this, NearbyLifecycleService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}

@Composable
private fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val requiredPermissions = remember { requiredNearbyPermissions() }
    val optionalPermissions = remember { optionalNearbyPermissions() }
    val allRequestablePermissions = remember {
        (requiredPermissions + optionalPermissions).distinct().toTypedArray()
    }

    var granted by remember {
        mutableStateOf(
            requiredPermissions.all { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        granted = requiredPermissions.all { permission ->
            results[permission] == true ||
                    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = requiredPermissions.all { permission ->
                    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        context.startActivity(intent)
    }

    LaunchedEffect(Unit) {
        granted = requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

        if (!granted) {
            launcher.launch(allRequestablePermissions)
        }
    }

    if (granted) {
        content()
    } else {
        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "CERCA necesita permisos de dispositivos cercanos y ubicación para descubrir teléfonos y enviar mensajes offline.",
                style = MaterialTheme.typography.bodyLarge
            )

            if (missingPermissions.isNotEmpty()) {
                Text(
                    text = "Permisos faltantes:\n" +
                            missingPermissions.joinToString("\n") { it.substringAfterLast(".") },
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Button(
                onClick = { launcher.launch(allRequestablePermissions) },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Conceder permisos")
            }

            Button(
                onClick = { openAppSettings() },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Abrir ajustes de la app")
            }

            Button(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Activar ubicación del teléfono")
            }
        }
    }
}

@SuppressLint("InlinedApi")
private fun requiredNearbyPermissions(): Array<String> {
    return buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }
        .distinct()
        .toTypedArray()
}

@SuppressLint("InlinedApi")
private fun optionalNearbyPermissions(): Array<String> {
    return buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
        .distinct()
        .toTypedArray()
}
