package com.foodcheck.presentation.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodcheck.domain.entities.ScanOutcome
import com.foodcheck.presentation.components.AttachedImageThumb
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
    val loading by viewModel.loading.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val error by viewModel.error.collectAsState()
    val outcome by viewModel.outcome.collectAsState()
    val scope = rememberCoroutineScope()
    var pendingImage by remember { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(outcome) {
        outcome?.let {
            pendingImage = null
            onScanOutcome(it)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = AppColors.Primary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = statusText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        } else if (error != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = null,
                    tint = Color.Red,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = error ?: "An error occurred",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Button(
                    onClick = {
                        viewModel.clear()
                        pendingImage = null
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Try Again")
                }
            }
        } else if (pendingImage != null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Confirm photo",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap the thumb for a full preview",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                AttachedImageThumb(
                    bytes = pendingImage!!,
                    sizeDp = 160,
                    onRemove = { pendingImage = null }
                )
                Spacer(modifier = Modifier.height(28.dp))
                Button(
                    onClick = { viewModel.scan(listOf(pendingImage!!)) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                ) {
                    Text("Start Scan", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { pendingImage = null },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Text("Choose another", color = Color.White)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                ScannerTargetCompose()

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                scope.launch {
                                    val image = imagePickerLauncher.pickImage(ImagePickerSource.CAMERA)
                                    if (image != null) pendingImage = image
                                }
                            },
                        color = Color.White.copy(alpha = 0.04f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(AppColors.Primary.copy(alpha = 0.12f), shape = CircleShape)
                                    .border(BorderStroke(0.5.dp, AppColors.Primary.copy(alpha = 0.3f)), shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Camera",
                                    tint = AppColors.Primary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Camera Scan",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Take a label photo",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.4f)
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                scope.launch {
                                    val image = imagePickerLauncher.pickImage(ImagePickerSource.GALLERY)
                                    if (image != null) pendingImage = image
                                }
                            },
                        color = Color.White.copy(alpha = 0.04f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Color.White.copy(alpha = 0.06f), shape = CircleShape)
                                    .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)), shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = "Gallery",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Upload Image",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Select from library",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScannerTargetCompose(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = modifier
            .size(240.dp)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(BorderStroke(1.5.dp, AppColors.Primary.copy(alpha = alpha)), shape = RoundedCornerShape(30.dp))
        )
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(AppColors.Primary.copy(alpha = 0.08f), shape = RoundedCornerShape(40.dp))
                .border(BorderStroke(1.dp, AppColors.Primary.copy(alpha = alpha * 0.5f)), shape = RoundedCornerShape(40.dp))
        )
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(1.dp)
                .background(AppColors.Primary.copy(alpha = alpha))
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(40.dp)
                .background(AppColors.Primary.copy(alpha = alpha))
        )
    }
}
