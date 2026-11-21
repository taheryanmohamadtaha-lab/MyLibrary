package com.mylibrary.pdf

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.mylibrary.pdf.ui.AppRoot
import com.mylibrary.pdf.ui.IntentViewModel

class MainActivity : ComponentActivity() {
    private val intentVm: IntentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent { AppRoot(intentVm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        if (i?.action == Intent.ACTION_VIEW && i.data != null) {
            intentVm.uri.value = i.data
        }
    }
}
