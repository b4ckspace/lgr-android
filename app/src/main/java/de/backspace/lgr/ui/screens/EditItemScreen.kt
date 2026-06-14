// SPDX-FileCopyrightText: 2026 Andreas Uhsemann
// SPDX-License-Identifier: Apache-2.0

package de.backspace.lgr.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlin.math.roundToInt
import de.backspace.lgr.viewmodel.AppViewModel
import java.io.File

private fun ByteArray.applyExifRotation(): ByteArray {
    val exif = android.media.ExifInterface(java.io.ByteArrayInputStream(this))
    val degrees = when (exif.getAttributeInt(
        android.media.ExifInterface.TAG_ORIENTATION,
        android.media.ExifInterface.ORIENTATION_NORMAL
    )) {
        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (degrees == 0f) return this
    val bitmap = BitmapFactory.decodeByteArray(this, 0, size) ?: return this
    val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
    val rotated = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    val out = java.io.ByteArrayOutputStream()
    rotated.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
    return out.toByteArray()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditItemScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val saveState = viewModel.saveItemEditState
    val scrollState = rememberScrollState()

    LaunchedEffect(saveState.data) {
        if (saveState.data != null) onSaved()
    }

    val context = LocalContext.current
    val cameraUri = remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            val uri = cameraUri.value ?: return@rememberLauncherForActivityResult
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) viewModel.setEditItemPendingImage(bytes.applyExifRotation())
        }
    }

    val canSave = viewModel.editItemNameQuery.isNotBlank() && !saveState.isLoading
    var showFullscreenImage by remember { mutableStateOf(false) }

    if (showFullscreenImage) {
        val fsImageUrl = viewModel.currentItem?.image
        val fsBytes = viewModel.editItemPendingImageBytes
        Dialog(
            onDismissRequest = { showFullscreenImage = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
                scale = (scale * zoomChange).coerceIn(1f, 8f)
                offset = if (scale > 1f) offset + panChange / scale else Offset.Zero
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .transformable(state = transformableState)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { if (scale <= 1.05f) showFullscreenImage = false },
                            onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2f }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (fsBytes != null) {
                    val fullBitmap = remember(fsBytes) {
                        BitmapFactory.decodeByteArray(fsBytes, 0, fsBytes.size)?.asImageBitmap()
                    }
                    if (fullBitmap != null) {
                        Image(
                            bitmap = fullBitmap,
                            contentDescription = "Full size photo",
                            modifier = Modifier.fillMaxSize()
                                .scale(scale)
                                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) },
                            contentScale = ContentScale.Fit
                        )
                    }
                } else if (fsImageUrl != null) {
                    AsyncImage(
                        model = fsImageUrl,
                        contentDescription = "Full size item image",
                        imageLoader = viewModel.imageLoader,
                        modifier = Modifier.fillMaxSize()
                            .scale(scale)
                            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) },
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Item") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                windowInsets = WindowInsets(0)
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding()
        ) {
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScrollbar(scrollState)
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (saveState.error != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = saveState.error.replaceFirstChar { it.uppercaseChar() },
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(12.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = viewModel.editItemNameQuery,
                    onValueChange = { viewModel.editItemNameQuery = it },
                    label = { Text("Item *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = lgrTextFieldColors()
                )

                ScrollableMultilineTextField(
                    value = viewModel.editItemDescription,
                    onValueChange = { viewModel.editItemDescription = it },
                    label = "Item description",
                    modifier = Modifier.fillMaxWidth()
                )

                if (viewModel.supportsImages && !viewModel.readonlyMode) {
                    HorizontalDivider()
                    val currentImageUrl = viewModel.currentItem?.image
                    val deleteImage = viewModel.editItemDeleteImage
                    val pendingBytes = viewModel.editItemPendingImageBytes
                    if (currentImageUrl != null && pendingBytes == null && !deleteImage) {
                        ItemImagePreview(
                            model = currentImageUrl,
                            imageLoader = viewModel.imageLoader,
                            contentDescription = "Current item image",
                            maxHeight = 240.dp,
                            onClick = { showFullscreenImage = true }
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (pendingBytes != null) {
                            val bitmap = remember(pendingBytes) {
                                BitmapFactory.decodeByteArray(pendingBytes, 0, pendingBytes.size)?.asImageBitmap()
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = "Pending photo",
                                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(4.dp))
                                        .clickable { showFullscreenImage = true },
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Text(
                                "New photo attached",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.setEditItemPendingImage(null) }) {
                                Icon(Icons.Default.Clear, contentDescription = "Remove staged photo", tint = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            Text(
                                when {
                                    deleteImage -> "Photo will be removed"
                                    currentImageUrl != null -> "Replace photo"
                                    else -> "Add photo"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (deleteImage) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            if (currentImageUrl != null) {
                                if (deleteImage) {
                                    IconButton(onClick = { viewModel.applyEditItemDeleteImage(false) }) {
                                        Icon(Icons.Default.Undo, contentDescription = "Keep photo")
                                    }
                                } else {
                                    IconButton(onClick = { viewModel.applyEditItemDeleteImage(true) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove photo", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                        IconButton(onClick = {
                            val cameraDir = File(context.cacheDir, "camera_images").also { it.mkdirs() }
                            val imageFile = File(cameraDir, "edit_item.jpg")
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
                            cameraUri.value = uri
                            cameraLauncher.launch(uri)
                        }) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "Take photo", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                OutlinedButton(onClick = onBack) { Text("Cancel") }
                Button(
                    onClick = { viewModel.saveItemEdit() },
                    enabled = canSave
                ) {
                    if (saveState.isLoading) CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    else Text("Save")
                }
            }
        }
    }
}
