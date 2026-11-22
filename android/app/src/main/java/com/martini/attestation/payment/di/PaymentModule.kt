package com.martini.attestation.payment.di

import com.martini.attestation.payment.data.repositories.PaymentRepository
import com.martini.attestation.payment.data.services.PaymentApiService
import com.martini.attestation.payment.domain.ProcessPayment
import com.martini.attestation.payment.ui.PaymentViewModel
import io.ktor.client.HttpClient
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val paymentModule = module {
    single { PaymentApiService(get<HttpClient>()) }
    single { PaymentRepository(get<PaymentApiService>()) }
    factory { ProcessPayment(get<PaymentRepository>(), get()) }
    viewModel { params -> PaymentViewModel(userId = params.get(), deviceId = params.get(), processPayment = get()) }
}
