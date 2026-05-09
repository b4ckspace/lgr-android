package de.uhsemann.lgr

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
import de.uhsemann.lgr.ui.navigation.AppNavigation
import de.uhsemann.lgr.ui.screens.LoginScreen
import de.uhsemann.lgr.ui.theme.LgrTheme
import de.uhsemann.lgr.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LgrTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val authState = viewModel.auth
                    when {
                        authState.isLoading -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }

                        authState.data?.authenticated == true -> AppNavigation(viewModel)

                        else -> LoginScreen(viewModel)
                    }
                }
            }
        }
    }
}
