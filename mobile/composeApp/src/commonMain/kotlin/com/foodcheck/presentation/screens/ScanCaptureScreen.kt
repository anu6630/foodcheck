package com.foodcheck.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodcheck.domain.entities.ScanOutcome
import com.foodcheck.presentation.components.ErrorBanner
import com.foodcheck.presentation.state.ScanViewModel
import com.foodcheck.presentation.theme.AppColors
import com.foodcheck.util.ImagePickerLauncher
import com.foodcheck.util.ImagePickerSource
import kotlinx.coroutines.launch

@Composable
fun ScanCaptureScreen(
    imagePickerLauncher: ImagePickerLauncher,
    onScanOutcome: (ScanOutcome) -> Unit,
    viewModel: ScanViewModel
) {
    var frontImage by remember { mutableStateOf<ByteArray?>(null) }
    var ingredientsImage by remember { mutableStateOf<ByteArray?>(null) }
    val loading by viewModel.loading.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val error by viewModel.error.collectAsState()
    val outcome by viewModel.outcome.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(outcome) {
        outcome?.let { onScanOutcome(it) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("AI Vision Label Scanner", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = androidx.compose.ui.graphics.Color.White)
        }
        item {
            PhotoSlot(
                title = "Front of product",
                subtitle = "Required",
                bytes = frontImage,
                onCamera = { scope.launch { frontImage = imagePickerLauncher.pickImage(ImagePickerSource.CAMERA) } },
                onGallery = { scope.launch { frontImage = imagePickerLauncher.pickImage(ImagePickerSource.GALLERY) } }
            )
        }
        item {
            PhotoSlot(
                title = "Ingredients label",
                subtitle = "Optional — improves match accuracy",
                bytes = ingredientsImage,
                onCamera = { scope.launch { ingredientsImage = imagePickerLauncher.pickImage(ImagePickerSource.CAMERA) } },
                onGallery = { scope.launch { ingredientsImage = imagePickerLauncher.pickImage(ImagePickerSource.GALLERY) } }
            )
        }
        item {
            Button(
                onClick = {
                    val images = listOfNotNull(frontImage, ingredientsImage)
                    viewModel.scan(images)
                },
                enabled = frontImage != null && !loading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
            ) {
                Text("Scan", fontWeight = FontWeight.Bold)
            }
        }
        if (loading) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = AppColors.Primary.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, AppColors.Primary.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AppColors.Primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(statusText, color = AppColors.TextFaint, fontSize = 13.sp)
                    }
                }
            }
        }
        if (error != null) {
            item { ErrorBanner(error ?: "") }
        }
    }
}

@Composable
private fun PhotoSlot(
    title: String,
    subtitle: String,
    bytes: ByteArray?,
    onCamera: () -> Unit,
    onGallery: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().border(1.dp, AppColors.Border.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
        color = AppColors.Surface.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (bytes != null) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = androidx.compose.ui.graphics.Color.White)
            }
            Text(subtitle, color = AppColors.TextMuted, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onCamera, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Camera", fontSize = 12.sp)
                }
                OutlinedButton(onClick = onGallery, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gallery", fontSize = 12.sp)
                }
            }
        }
    }
}
