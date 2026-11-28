package com.sonder.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sonder.app.ui.SonderRoot
import com.sonder.app.ui.SonderViewModel
import com.sonder.app.widget.SonderWidgetProvider

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: SonderViewModel = viewModel()
            SonderRoot(viewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        SonderWidgetProvider.refresh(this)
    }
}
