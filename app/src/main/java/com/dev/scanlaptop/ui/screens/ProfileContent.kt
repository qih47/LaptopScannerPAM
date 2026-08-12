package com.dev.scanlaptop.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dev.scanlaptop.data.SessionManager
import com.dev.scanlaptop.data.UserData
import com.dev.scanlaptop.service.RealtimeNotificationService
import com.dev.scanlaptop.ui.viewmodel.ProfileUiState
import com.dev.scanlaptop.ui.viewmodel.ProfileViewModel
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileContent(
    userData: UserData,
    onLogout: () -> Unit,
    onHistoryClick: () -> Unit,
    navyColor: Color,
    profileViewModel: ProfileViewModel = viewModel()
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val scope = rememberCoroutineScope()
    val uiState by profileViewModel.uiState.collectAsState()

    var showPasswordDialog by remember { mutableStateOf(false) }
    var showBiometricVerifyDialog by remember { mutableStateOf(false) }
    
    // Biometric & Push Notif State
    val biometricEnabled by sessionManager.biometricEnabledFlow.collectAsState(initial = false)
    val pushNotifEnabled by sessionManager.pushNotifEnabledFlow.collectAsState(initial = false)

    val executor = ContextCompat.getMainExecutor(context)
    val biometricPrompt = remember {
        androidx.biometric.BiometricPrompt(context as FragmentActivity, executor,
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    showBiometricVerifyDialog = true
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(context, "Gagal verifikasi: $errString", Toast.LENGTH_SHORT).show()
                }
            })
    }
    
    val promptInfo = remember {
        androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle("Verifikasi Fingerprint")
            .setSubtitle("Konfirmasi sidik jari Anda untuk mengaktifkan fitur ini")
            .setNegativeButtonText("Batal")
            .build()
    }

    // Image Picker & Cropper
    val cropLauncher = rememberLauncherForActivityResult(contract = com.canhub.cropper.CropImageContract()) { result ->
        if (result.isSuccessful) {
            val uriContent = result.uriContent
            uriContent?.let {
                try {
                    val inputStream = context.contentResolver.openInputStream(it)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
                    val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
                    profileViewModel.updateFotoProfil(userData.npp, base64, sessionManager)
                } catch (e: Exception) {
                    Toast.makeText(context, "Gagal memproses gambar", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            val exception = result.error
            if (exception != null) {
                Toast.makeText(context, "Gagal memotong gambar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            is ProfileUiState.Success -> {
                Toast.makeText(context, (uiState as ProfileUiState.Success).message, Toast.LENGTH_SHORT).show()
                showPasswordDialog = false
                profileViewModel.resetState()
            }
            is ProfileUiState.BiometricVerified -> {
                scope.launch { sessionManager.setBiometricEnabled(true) }
                Toast.makeText(context, "Fingerprint diaktifkan!", Toast.LENGTH_SHORT).show()
                showBiometricVerifyDialog = false
                profileViewModel.resetState()
            }
            is ProfileUiState.Error -> {
                Toast.makeText(context, (uiState as ProfileUiState.Error).message, Toast.LENGTH_SHORT).show()
                profileViewModel.resetState()
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- HEADER ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF0D47A1), Color(0xFF001233))))
                .statusBarsPadding()
                .padding(top = 24.dp, bottom = 40.dp, start = 24.dp, end = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Avatar
                Box(contentAlignment = Alignment.BottomEnd, modifier = Modifier.clickable {
                    cropLauncher.launch(
                        com.canhub.cropper.CropImageContractOptions(
                            uri = null,
                            cropImageOptions = com.canhub.cropper.CropImageOptions(
                                imageSourceIncludeCamera = true,
                                imageSourceIncludeGallery = true,
                                aspectRatioX = 1,
                                aspectRatioY = 1,
                                fixAspectRatio = true,
                                cropShape = com.canhub.cropper.CropImageView.CropShape.OVAL
                            )
                        )
                    )
                }) {
                    if (userData.foto_profil.isNullOrBlank()) {
                        Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(120.dp).border(2.dp, Color.White.copy(0.5f), CircleShape)) {
                            Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.padding(25.dp))
                        }
                    } else {
                        val imageBytes = Base64.decode(userData.foto_profil, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Foto Profil",
                                modifier = Modifier.size(120.dp).clip(CircleShape).border(2.dp, Color.White.copy(0.5f), CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(120.dp).border(2.dp, Color.White.copy(0.5f), CircleShape)) {
                                Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.padding(25.dp))
                            }
                        }
                    }
                    Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(36.dp).padding(4.dp)) {
                        Icon(Icons.Default.CameraAlt, null, tint = navyColor, modifier = Modifier.padding(4.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                Text(userData.nama_lengkap, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text("NPP: ${userData.npp}", fontSize = 16.sp, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                
                Spacer(modifier = Modifier.height(12.dp))
                Surface(color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AdminPanelSettings, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(userData.role.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }
            }
        }
        
        // --- KONTEN BAWAH ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

        // Riwayat & Aktivitas
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Aktivitas Petugas", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onHistoryClick() }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.History, null, tint = navyColor)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Riwayat Pindaian", fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Pengaturan
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Pengaturan Keamanan", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))
                
                // Ubah Password
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showPasswordDialog = true }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, null, tint = navyColor)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Ubah Password", fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Color(0xFF1E293B))
                }
                
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                
                // Biometric Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Fingerprint, null, tint = navyColor)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Login Fingerprint", fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Color(0xFF1E293B))
                    }
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                biometricPrompt.authenticate(promptInfo)
                            } else {
                                scope.launch { sessionManager.setBiometricEnabled(false) }
                            }
                        }
                    )
                }
                
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                
                // Push Notification Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, null, tint = navyColor)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Push Notif Realtime", fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Color(0xFF1E293B))
                    }
                    Switch(
                        checked = pushNotifEnabled,
                        onCheckedChange = { isChecked ->
                            scope.launch { sessionManager.setPushNotifEnabled(isChecked) }
                            if (isChecked) {
                                Toast.makeText(context, "Push Notif diaktifkan", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Push Notif dinonaktifkan", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bantuan & Dukungan
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Bantuan & Dukungan", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://wa.me/6285716008651?text=Halo%20Dev,%20saya%20ingin%20melaporkan%20feedback/bug%20pada%20aplikasi:%0A%0A")
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Tidak ada browser atau aplikasi WhatsApp", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.SupportAgent, null, tint = navyColor)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Kirim Feedback / Lapor Bug", fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Color(0xFF1E293B))
                        Text("Terhubung ke WhatsApp Developer", fontSize = 12.sp, color = Color.Gray)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Logout
        Button(
            onClick = {
                context.stopService(Intent(context, RealtimeNotificationService::class.java))
                onLogout()
            },
            modifier = Modifier.fillMaxWidth(0.7f).height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Logout Aplikasi", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "v${com.dev.scanlaptop.BuildConfig.VERSION_NAME}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showPasswordDialog) {
        var oldPassword by remember { mutableStateOf("") }
        var newPassword by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.DarkGray,
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = navyColor,
                        modifier = Modifier.padding(end = 12.dp).size(28.dp)
                    )
                    Text("Ubah Password", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = oldPassword,
                        onValueChange = { oldPassword = it },
                        label = { Text("Password Lama") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Next
                        ),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = navyColor,
                            focusedLabelColor = navyColor,
                            unfocusedBorderColor = Color.LightGray,
                            unfocusedLabelColor = Color.Gray,
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.DarkGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Password Baru") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = navyColor,
                            focusedLabelColor = navyColor,
                            unfocusedBorderColor = Color.LightGray,
                            unfocusedLabelColor = Color.Gray,
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.DarkGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { profileViewModel.updatePassword(userData.npp, oldPassword, newPassword) },
                    colors = ButtonDefaults.buttonColors(containerColor = navyColor, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (uiState is ProfileUiState.Loading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Simpan", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text("Batal", color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showBiometricVerifyDialog) {
        var verifyPassword by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBiometricVerifyDialog = false },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.DarkGray,
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = navyColor,
                        modifier = Modifier.padding(end = 12.dp).size(28.dp)
                    )
                    Text("Verifikasi", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Masukkan password Anda untuk mengaktifkan fitur login dengan sidik jari.",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = verifyPassword,
                        onValueChange = { verifyPassword = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = navyColor,
                            focusedLabelColor = navyColor,
                            unfocusedBorderColor = Color.LightGray,
                            unfocusedLabelColor = Color.Gray,
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.DarkGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { profileViewModel.verifyPasswordAndEnableBiometric(userData.npp, verifyPassword) },
                    colors = ButtonDefaults.buttonColors(containerColor = navyColor, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (uiState is ProfileUiState.Loading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Verifikasi", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showBiometricVerifyDialog = false }) {
                    Text("Batal", color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
    }
}
