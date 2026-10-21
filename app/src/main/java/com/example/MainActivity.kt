package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.Gp180ViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: Gp180ViewModel = viewModel()

                // Permission launcher for Bluetooth BLE MIDI scanning
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { /* permissions handled gracefully */ }

                LaunchedEffect(Unit) {
                    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        arrayOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT
                        )
                    } else {
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    }
                    permissionLauncher.launch(permissions)
                }

                MainScreen(viewModel = viewModel)
            }
        }
    }
}
