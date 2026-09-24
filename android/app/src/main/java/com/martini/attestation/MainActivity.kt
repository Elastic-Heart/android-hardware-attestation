package com.martini.attestation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.martini.attestation.setup.ui.SetupScreen
import com.martini.attestation.ui.theme.AttestationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AttestationTheme {
                SetupScreen()
            }
        }
    }
}