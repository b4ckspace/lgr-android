// SPDX-FileCopyrightText: 2026 Andreas Uhsemann
// SPDX-License-Identifier: Apache-2.0

package de.backspace.lgr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import de.backspace.lgr.ui.navigation.AppNavigation
import de.backspace.lgr.ui.screens.LoginScreen
import de.backspace.lgr.ui.screens.ScanTones
import de.backspace.lgr.ui.theme.LgrTheme
import de.backspace.lgr.viewmodel.AppViewModel
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Warm the shared tone generator up front (off the main thread) so the first scan's
        // acknowledge beep plays immediately and at full length instead of clipped.
        thread { ScanTones.warmUp() }
        setContent {
            LgrTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when {
                        viewModel.auth.data?.authenticated == true || viewModel.readonlyMode ->
                            AppNavigation(viewModel)
                        // Validating a restored session at startup: show a loader instead of the
                        // login screen so it doesn't flash before auto-login completes.
                        viewModel.initialAuthInProgress ->
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        else ->
                            // LoginScreen stays mounted during loading so remember{} state is preserved
                            LoginScreen(viewModel)
                    }
                }
            }
        }
    }
}
