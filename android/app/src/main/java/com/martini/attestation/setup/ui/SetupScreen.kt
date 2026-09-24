package com.martini.attestation.setup.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.martini.attestation.common.LoadState
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun SetupScreen(
    viewModel: SetupViewModel = koinViewModel()
) {
    val state by viewModel.setupStateFlow.collectAsStateWithLifecycle()

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = viewModel.snackbarHostState)
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                is LoadState.Failure, LoadState.Initial, is LoadState.Success -> {
                    ElevatedButton(
                        onClick = viewModel::onStartSetup
                    ) {
                        Text(text = "Verify device")
                    }
                }
                is LoadState.Loading -> {
                    CircularProgressIndicator()
                }
            }
        }
    }
}