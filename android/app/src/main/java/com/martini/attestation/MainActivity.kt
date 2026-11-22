package com.martini.attestation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.martini.attestation.setup.ui.SetupScreen
import com.martini.attestation.ui.theme.AttestationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AttestationTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "setup") {
                    composable("setup") {
                        val setupViewModel: com.martini.attestation.setup.ui.SetupViewModel = org.koin.androidx.compose.koinViewModel()
                        val setupState by setupViewModel.setupStateFlow.collectAsStateWithLifecycle()

                        LaunchedEffect(setupState) {
                            if (setupState is com.martini.attestation.common.LoadState.Success) {
                                val credentials = (setupState as com.martini.attestation.common.LoadState.Success<com.martini.attestation.setup.domain.DeviceCredentials>).data
                                navController.navigate("payment/${credentials.userId}/${credentials.deviceId}") {
                                    launchSingleTop = true
                                }
                            }
                        }

                        SetupScreen(viewModel = setupViewModel)
                    }
                    composable(
                        route = "payment/{userId}/{deviceId}",
                        arguments = listOf(
                            navArgument("userId") { type = NavType.StringType },
                            navArgument("deviceId") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val userId = backStackEntry.arguments?.getString("userId") ?: ""
                        val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
                        com.martini.attestation.payment.ui.PaymentScreen(userId = userId, deviceId = deviceId)
                    }
                }
            }
        }
    }
}