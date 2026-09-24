package com.martini.attestation.crypto

import org.koin.dsl.module

internal val cryptoModule = module {
    factory { HardwareKeyManager() }
}