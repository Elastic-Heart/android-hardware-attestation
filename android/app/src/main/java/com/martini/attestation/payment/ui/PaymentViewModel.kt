package com.martini.attestation.payment.ui

import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

import androidx.lifecycle.viewModelScope
import com.martini.attestation.common.LoadState
import com.martini.attestation.payment.domain.ProcessPayment
import kotlinx.coroutines.launch
import java.math.BigDecimal

class PaymentViewModel(
    private val userId: String,
    private val deviceId: String,
    private val processPayment: ProcessPayment
) : ViewModel() {
    val snackbarHostState = SnackbarHostState()

    private val _recipient = MutableStateFlow("")
    val recipient: StateFlow<String> = _recipient.asStateFlow()

    private val _amountText = MutableStateFlow("")
    val amountText: StateFlow<String> = _amountText.asStateFlow()

    val amount: StateFlow<BigDecimal> = _amountText
        .map { it.parseAmountOrZero() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = BigDecimal.ZERO
        )

    private val _paymentState = MutableStateFlow<LoadState<Unit, Throwable>>(LoadState.Initial)
    val paymentState: StateFlow<LoadState<Unit, Throwable>> = _paymentState.asStateFlow()

    fun onRecipientChanged(newRecipient: String) {
        _recipient.value = newRecipient
    }

    fun onAmountChanged(newAmount: String) {
        _amountText.value = newAmount.filter { it.isDigit() }
    }

    fun onPayClicked() {
        val currentRecipient = _recipient.value
        val currentAmount = amount.value
        if (currentRecipient.isBlank() || currentAmount <= BigDecimal.ZERO) {
             viewModelScope.launch {
                 snackbarHostState.showSnackbar("Invalid recipient or amount")
             }
             return
        }
        
        if (_paymentState.value is LoadState.Loading) return

        viewModelScope.launch {
             _paymentState.value = LoadState.Loading
             // Hardcoded user id and device id for PoC
             val result = processPayment(
                 userId = userId,
                 deviceId = deviceId,
                 recipient = currentRecipient,
                 amount = currentAmount
             )
             
             if (result.isSuccess) {
                 _paymentState.value = LoadState.Success(Unit)
                 snackbarHostState.showSnackbar("Payment processed successfully")
             } else {
                 val error = result.exceptionOrNull() ?: Exception("Payment failed")
                 _paymentState.value = LoadState.Failure(error)
                 snackbarHostState.showSnackbar(error.message ?: "Payment failed")
             }
        }
    }

    private fun String.parseAmountOrZero(): BigDecimal {
        val trimmed = trim()
        if (trimmed.isEmpty()) return BigDecimal.ZERO

        // If both ',' and '.' exist, assume ',' is thousands and '.' is decimal (e.g., "1,234.56")
        // Otherwise, replace ',' with '.' (e.g., "12,34" -> "12.34")
        val normalized = if (',' in trimmed && '.' in trimmed) {
            trimmed.replace(",", "")
        } else {
            trimmed.replace(',', '.')
        }

        return normalized.toBigDecimalOrNull() ?: BigDecimal.ZERO
    }
}
