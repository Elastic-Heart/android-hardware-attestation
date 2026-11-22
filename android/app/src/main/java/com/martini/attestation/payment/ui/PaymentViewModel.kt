package com.martini.attestation.payment.ui

import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

import androidx.lifecycle.viewModelScope
import com.martini.attestation.payment.domain.ProcessPayment
import kotlinx.coroutines.launch
import java.util.UUID

class PaymentViewModel(
    private val userId: String,
    private val deviceId: String,
    private val processPayment: ProcessPayment
) : ViewModel() {
    val snackbarHostState = SnackbarHostState()

    private val _recipient = MutableStateFlow("")
    val recipient: StateFlow<String> = _recipient

    private val _amount = MutableStateFlow("")
    val amount: StateFlow<String> = _amount

    fun onRecipientChanged(newRecipient: String) {
        _recipient.value = newRecipient
    }

    fun onAmountChanged(newAmount: String) {
        _amount.value = newAmount
    }

    fun onPayClicked() {
        val currentRecipient = _recipient.value
        val currentAmountStr = _amount.value
        val currentAmount = currentAmountStr.toDoubleOrNull()
        if (currentRecipient.isBlank() || currentAmount == null) {
             viewModelScope.launch {
                 snackbarHostState.showSnackbar("Invalid recipient or amount")
             }
             return
        }
        
        viewModelScope.launch {
             // Hardcoded user id and device id for PoC
             val result = processPayment(
                 userId = userId,
                 deviceId = deviceId,
                 recipient = currentRecipient,
                 amount = currentAmount
             )
             
             if (result.isSuccess) {
                 snackbarHostState.showSnackbar("Payment processed successfully")
             } else {
                 snackbarHostState.showSnackbar("Payment failed")
             }
        }
    }
}
