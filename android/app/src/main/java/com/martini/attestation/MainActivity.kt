package com.martini.attestation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.martini.attestation.setup.ui.SetupScreen
import com.martini.attestation.ui.theme.AttestationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AttestationTheme {
                val setupViewModel: com.martini.attestation.setup.ui.SetupViewModel = org.koin.androidx.compose.koinViewModel()
                val setupState by setupViewModel.setupStateFlow.collectAsStateWithLifecycle()

                if (setupState is com.martini.attestation.common.LoadState.Success) {
                    val credentials = (setupState as com.martini.attestation.common.LoadState.Success<com.martini.attestation.setup.domain.DeviceCredentials>).data
                    com.martini.attestation.payment.ui.PaymentScreen(userId = credentials.userId, deviceId = credentials.deviceId)
                } else {
                    SetupScreen(viewModel = setupViewModel)
                }
            }
        }
    }
}