package com.sappho.audiobooks.presentation.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.sappho.audiobooks.presentation.theme.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sappho.audiobooks.R

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var mfaCode by remember { mutableStateOf("") }
    val serverUrl by viewModel.serverUrl.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    // Navigate on success
    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onLoginSuccess()
        }
    }

    // Determine if we're in MFA mode
    val mfaToken = when (uiState) {
        is LoginUiState.MfaRequired -> (uiState as LoginUiState.MfaRequired).mfaToken
        is LoginUiState.MfaError -> (uiState as LoginUiState.MfaError).mfaToken
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        SapphoBackground,
                        SapphoSurface
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = SapphoSurface,
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    width = 1.dp,
                    color = SapphoSurfaceBorder,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(40.dp)
        ) {
            // Sappho Logo
            Image(
                painter = painterResource(id = R.drawable.sappho_logo),
                contentDescription = "Sappho Logo",
                modifier = Modifier
                    .height(48.dp)
                    .padding(bottom = 8.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (mfaToken != null) {
                // MFA Code Entry
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = SapphoInfo,
                    modifier = Modifier.size(32.dp)
                )

                Text(
                    text = "Two-Factor Authentication",
                    style = MaterialTheme.typography.titleMedium,
                    color = SapphoText,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Enter the 6-digit code from your authenticator app, or a backup code.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SapphoTextMuted,
                    textAlign = TextAlign.Center
                )

                OutlinedTextField(
                    value = mfaCode,
                    onValueChange = { mfaCode = it },
                    label = { Text("Verification Code", color = SapphoTextLight) },
                    placeholder = { Text("000000", color = SapphoTextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SapphoText,
                        unfocusedTextColor = SapphoTextLight,
                        focusedBorderColor = SapphoInfo,
                        unfocusedBorderColor = SapphoProgressTrack,
                        focusedLabelColor = SapphoInfo,
                        unfocusedLabelColor = SapphoTextLight,
                        cursorColor = SapphoInfo
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            viewModel.verifyMfa(mfaToken, mfaCode)
                        }
                    )
                )

                // MFA Error
                if (uiState is LoginUiState.MfaError) {
                    ErrorBanner((uiState as LoginUiState.MfaError).message)
                }

                // Verify Button
                Button(
                    onClick = { viewModel.verifyMfa(mfaToken, mfaCode) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    enabled = uiState !is LoginUiState.Loading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SapphoInfo,
                        contentColor = Color.White,
                        disabledContainerColor = SapphoProgressTrack,
                        disabledContentColor = SapphoTextMuted
                    )
                ) {
                    if (uiState is LoginUiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = "Verify",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                // Back to login
                TextButton(onClick = {
                    mfaCode = ""
                    viewModel.cancelMfa()
                }) {
                    Text("Back to Login", color = SapphoTextMuted)
                }
            } else {
                // Normal Login Form

                // Server URL Input
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { viewModel.updateServerUrl(it) },
                    label = { Text("Server URL", color = SapphoTextLight) },
                    placeholder = { Text("https://your-server.com", color = SapphoTextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SapphoText,
                        unfocusedTextColor = SapphoTextLight,
                        focusedBorderColor = SapphoInfo,
                        unfocusedBorderColor = SapphoProgressTrack,
                        focusedLabelColor = SapphoInfo,
                        unfocusedLabelColor = SapphoTextLight,
                        cursorColor = SapphoInfo
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )

                // Username Input
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username", color = SapphoTextLight) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SapphoText,
                        unfocusedTextColor = SapphoTextLight,
                        focusedBorderColor = SapphoInfo,
                        unfocusedBorderColor = SapphoProgressTrack,
                        focusedLabelColor = SapphoInfo,
                        unfocusedLabelColor = SapphoTextLight,
                        cursorColor = SapphoInfo
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )

                // Password Input
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password", color = SapphoTextLight) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible)
                        VisualTransformation.None
                    else
                        PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible)
                                    Icons.Filled.Visibility
                                else
                                    Icons.Filled.VisibilityOff,
                                contentDescription = if (passwordVisible)
                                    "Hide password"
                                else
                                    "Show password",
                                tint = SapphoIconDefault
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SapphoText,
                        unfocusedTextColor = SapphoTextLight,
                        focusedBorderColor = SapphoInfo,
                        unfocusedBorderColor = SapphoProgressTrack,
                        focusedLabelColor = SapphoInfo,
                        unfocusedLabelColor = SapphoTextLight,
                        cursorColor = SapphoInfo
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            viewModel.login(username, password)
                        }
                    )
                )

                // Error Message
                if (uiState is LoginUiState.Error) {
                    ErrorBanner((uiState as LoginUiState.Error).message)
                }

                // Login Button
                Button(
                    onClick = { viewModel.login(username, password) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    enabled = uiState !is LoginUiState.Loading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SapphoInfo,
                        contentColor = Color.White,
                        disabledContainerColor = SapphoProgressTrack,
                        disabledContentColor = SapphoTextMuted
                    )
                ) {
                    if (uiState is LoginUiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = "Login",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    val errorMessage = message.takeIf { it.isNotBlank() }
        ?: "Login failed. Please check your connection and try again."

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF7F1D1D),
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = Color(0xFFDC2626),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Error",
            tint = Color(0xFFFCA5A5),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = errorMessage,
            color = Color(0xFFFECACA),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
