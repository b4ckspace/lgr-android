package de.backspace.lgr.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import de.backspace.lgr.BuildConfig
import de.backspace.lgr.viewmodel.AppViewModel

private val MID_GREY = Color(0xFF9E9E9E)

@Composable
fun LoginScreen(viewModel: AppViewModel) {
    var serverUrl by remember { mutableStateOf(viewModel.serverUrl) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loginAttempted by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val authState = viewModel.auth

    fun doLogin() {
        if (serverUrl.isNotBlank()) viewModel.applyServerUrl(serverUrl)
        if (username.isNotBlank() && password.isNotBlank()) {
            loginAttempted = true
            viewModel.login(username, password)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("LGR", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(40.dp))

        val errorMsg = when {
            authState.error != null -> authState.error
            loginAttempted && authState.data?.authenticated == false -> "Login failed — check username and password"
            else -> null
        }
        if (errorMsg != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    text = errorMsg,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("Server URL") },
            colors = lgrTextFieldColors(),
            placeholder = { Text("http://192.168.1.1:8000") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, keyboardType = KeyboardType.Uri),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            colors = lgrTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            colors = lgrTextFieldColors(),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Password),
            keyboardActions = KeyboardActions(onDone = { doLogin() })
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { doLogin() },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !authState.isLoading && username.isNotBlank() && password.isNotBlank() && serverUrl.isNotBlank()
        ) {
            if (authState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Login")
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { viewModel.enterReadonlyMode(serverUrl) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = serverUrl.isNotBlank()
        ) {
            Text("Read-only without login")
        }
    }

    if (!BuildConfig.DEBUG) {
        Text(
            text = buildAnnotatedString {
                append("Version: ${BuildConfig.VERSION_NAME}")
                withStyle(SpanStyle(color = MID_GREY)) {
                    append(" (${BuildConfig.BUILD_DATE})")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
        )
    }
    } // Box
}
