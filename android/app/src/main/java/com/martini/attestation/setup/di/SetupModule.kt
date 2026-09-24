package com.martini.attestation.setup.di

import com.martini.attestation.setup.data.datasources.AttestationRemoteDataSource
import com.martini.attestation.setup.data.repositories.AttestationRepository
import com.martini.attestation.setup.data.repositories.AttestationRepositoryImpl
import com.martini.attestation.setup.data.services.AttestationApiService
import com.martini.attestation.setup.domain.SetupDevice
import com.martini.attestation.setup.ui.SetupViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

internal val setupModule = module {
    factory { AttestationApiService(get()) }
    factory { AttestationRemoteDataSource(get()) }
    factory<AttestationRepository> { AttestationRepositoryImpl(get()) }
    factory { SetupDevice(get(), get()) }

    viewModel {
        SetupViewModel(get())
    }
}