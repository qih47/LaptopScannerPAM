package com.dev.scanlaptop.ui.screens

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.dev.scanlaptop.R
import com.dev.scanlaptop.data.UserData
import com.dev.scanlaptop.data.SessionManager
import com.dev.scanlaptop.ui.viewmodel.AuthUiState
import com.dev.scanlaptop.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2000)
        onTimeout()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0D47A1), Color(0xFF001233)))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.logo_pindad),
                contentDescription = "App Logo",
                modifier = Modifier.size(140.dp).clip(CircleShape),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text("MONITORING LAPTOP", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text("PT PINDAD", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFFD600), letterSpacing = 4.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (UserData) -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    var npp by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionManager = remember { SessionManager(context) }

    // Observe state dari ViewModel
    val uiState by authViewModel.uiState.collectAsState()
    val isLoading = uiState is AuthUiState.Loading

    // Biometric & Saved User
    val biometricEnabled by sessionManager.biometricEnabledFlow.collectAsState(initial = false)
    val savedUser by sessionManager.savedUserFlow.collectAsState(initial = null)

    val executor = ContextCompat.getMainExecutor(context)
    val biometricPrompt = remember {
        androidx.biometric.BiometricPrompt(context as FragmentActivity, executor,
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    savedUser?.let { 
                        scope.launch { sessionManager.saveSession(it) }
                        onLoginSuccess(it) 
                    } ?: Toast.makeText(context, "Sesi kedaluwarsa. Silakan login dengan password.", Toast.LENGTH_SHORT).show()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED) {
                        Toast.makeText(context, "Biometrik error: $errString", Toast.LENGTH_SHORT).show()
                    }
                }
            })
    }

    val promptInfo = remember {
        androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle("Login Biometrik")
            .setSubtitle("Gunakan sidik jari untuk masuk")
            .setNegativeButtonText("Gunakan Password")
            .build()
    }

    // Handle side effects dari state
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is AuthUiState.Success -> {
                scope.launch { sessionManager.saveSession(state.user) }
                onLoginSuccess(state.user)
                authViewModel.resetState()
            }
            is AuthUiState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                authViewModel.resetState()
            }
            else -> Unit
        }
    }

    val primaryBlue = Color(0xFF0D47A1)
    val darkText = Color(0xFF001233)

    Box(modifier = Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(primaryBlue.copy(alpha = 0.1f), CircleShape)
                    .border(2.dp, primaryBlue, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, null, modifier = Modifier.size(50.dp), tint = primaryBlue)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Selamat Datang", fontSize = 30.sp, fontWeight = FontWeight.Black, color = darkText)
            Text("Silahkan masuk ke akun Anda", fontSize = 15.sp, color = darkText.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(32.dp))

            // Input NPP
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("NPP Pegawai", fontSize = 14.sp, fontWeight = FontWeight.Black, color = darkText)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = npp,
                    onValueChange = { if (it.all { c -> c.isDigit() }) npp = it },
                    placeholder = { Text("Masukkan NPP", color = Color.LightGray) },
                    leadingIcon = { Icon(Icons.Default.Badge, null, tint = primaryBlue) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    readOnly = isLoading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryBlue,
                        unfocusedBorderColor = darkText,
                        focusedTextColor = darkText,
                        unfocusedTextColor = darkText,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Input Password
                Text("Kata Sandi", fontSize = 14.sp, fontWeight = FontWeight.Black, color = darkText)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("Masukkan Password", color = Color.LightGray) },
                    leadingIcon = { Icon(Icons.Default.Lock, null, tint = primaryBlue) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                null,
                                tint = primaryBlue
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    readOnly = isLoading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryBlue,
                        unfocusedBorderColor = darkText,
                        focusedTextColor = darkText,
                        unfocusedTextColor = darkText,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Tombol Login (Bisa manual / Sidik Jari)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isLoading) Brush.linearGradient(listOf(Color.DarkGray, Color.Black))
                        else Brush.horizontalGradient(listOf(primaryBlue, Color(0xFF1A237E)))
                    )
                    .clickable(enabled = !isLoading) {
                        if (biometricEnabled && password.isEmpty()) {
                            biometricPrompt.authenticate(promptInfo)
                        } else {
                            authViewModel.login(npp, password)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (biometricEnabled) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("MASUK SEKARANG", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(100.dp)) // Jarak yang cukup jauh ke bawah
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Bermasalah dengan akun?", color = darkText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                TextButton(onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse("https://wa.me/6285716008651")
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Tidak ada browser atau WhatsApp", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SupportAgent, null, modifier = Modifier.size(22.dp), tint = primaryBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("HUBUNGI IT SUPPORT", color = primaryBlue, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp)) // Padding bawah sedikit
        }
    }
}