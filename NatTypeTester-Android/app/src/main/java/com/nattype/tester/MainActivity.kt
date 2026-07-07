package com.nattype.tester

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nattype.tester.ui.NatTypeTesterApp
import com.nattype.tester.ui.theme.NatTypeTesterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NatTypeTesterTheme {
                NatTypeTesterApp()
            }
        }
    }
}
