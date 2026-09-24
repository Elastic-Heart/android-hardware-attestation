package com.martini.attestation.di

import com.martini.attestation.crypto.cryptoModule
import com.martini.attestation.setup.di.setupModule
import org.koin.dsl.module

val appModule = module {
    includes(networkModule)
    includes(cryptoModule)
    includes(setupModule)
}
