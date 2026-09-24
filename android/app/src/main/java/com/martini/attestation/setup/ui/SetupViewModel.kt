package com.martini.attestation.setup.ui

import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martini.attestation.common.LoadState
import com.martini.attestation.setup.domain.SetupDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SetupViewModel(
    private val setupDevice: SetupDevice
) : ViewModel() {

    val snackbarHostState = SnackbarHostState()

    private val _setupStateFlow = MutableStateFlow<LoadState<Unit, Throwable>>(LoadState.Initial )
    val setupStateFlow = _setupStateFlow.asStateFlow()

    fun onStartSetup() {
        if (_setupStateFlow.value is LoadState.Loading) return

        viewModelScope.launch {
            _setupStateFlow.update { LoadState.Loading }

            val result = setupDevice()

            _setupStateFlow.update { result }

            if (result is LoadState.Failure) {
                snackbarHostState.showSnackbar(
                    message = "Failed to verify device"
                )
            } else if (result is LoadState.Success) {
                snackbarHostState.showSnackbar(
                    message = "Verification successful"
                )
            }
        }
    }
}