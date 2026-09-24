package com.martini.attestation

import android.app.Application
import com.martini.attestation.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class AttestationApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@AttestationApplication)
            modules(appModule)
        }
    }
}
