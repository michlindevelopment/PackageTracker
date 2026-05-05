package com.michlind.packagetracker.ui.add

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun NativeCropOverlay(
    sourceUri: Uri,
    onCropped: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isCropping by remember { mutableStateOf(false) }

    LaunchedEffect(sourceUri) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(sourceUri)?.use {
                    BitmapFactory.decodeStream(it)
                }
            } catch (_: Exception) { null }
        }
        if (bitmap == null) onDismiss()
    }

    var userScale by remember { mutableFloatStateOf(1f) }
    var userOffsetX by remember { mutableFloatStateOf(0f) }
    var userOffsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0D0D)),
            contentAlignment = Alignment.Center
        ) {
            val bmp = bitmap
            if (bmp == null) {
                CircularProgressIndicator(color = Color.White)
            } else {
                val density = LocalDensity.current
                val cropDp = minOf(maxWidth, maxHeight) * 0.88f
                val cropPx = with(density) { cropDp.toPx() }
                // Scale that makes the image fill the square crop area
                val initScale = maxOf(cropPx / bmp.width, cropPx / bmp.height)

                // Image canvas — user pinches/drags to reframe
                Canvas(
                    modifier = Modifier
                        .size(cropDp)
                        .clip(RoundedCornerShape(2.dp))
                        .pointerInput(bmp) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                userScale = (userScale * zoom).coerceIn(0.8f, 6f)
                                userOffsetX += pan.x
                                userOffsetY += pan.y
                            }
                        }
                ) {
                    val total = initScale * userScale
                    val dstW = (bmp.width * total).toInt().coerceAtLeast(1)
                    val dstH = (bmp.height * total).toInt().coerceAtLeast(1)
                    val left = (size.width / 2f + userOffsetX - dstW / 2f).toInt()
                    val top = (size.height / 2f + userOffsetY - dstH / 2f).toInt()
                    drawImage(
                        image = bmp.asImageBitmap(),
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bmp.width, bmp.height),
                        dstOffset = IntOffset(left, top),
                        dstSize = IntSize(dstW, dstH)
                    )
                }

                // Rule-of-thirds grid + border overlay
                Canvas(modifier = Modifier.size(cropDp)) {
                    val lineW = 0.7.dp.toPx()
                    val gridColor = Color.White.copy(alpha = 0.35f)
                    for (i in 1..2) {
                        val x = size.width * i / 3f
                        val y = size.height * i / 3f
                        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), lineW)
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), lineW)
                    }
                    drawRect(Color.White, style = Stroke(width = 2.dp.toPx()))
                }

                // Bottom controls
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.White, fontSize = 16.sp)
                    }
                    Button(
                        onClick = {
                            isCropping = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    cropAndSave(context, bmp, userScale, userOffsetX, userOffsetY, initScale, cropPx)
                                }
                                isCropping = false
                                result?.let { onCropped(it) } ?: onDismiss()
                            }
                        },
                        enabled = !isCropping
                    ) {
                        if (isCropping) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        else Text("Done")
                    }
                }
            }
        }
    }
}

private fun cropAndSave(
    context: Context,
    src: Bitmap,
    userScale: Float,
    userOffsetX: Float,
    userOffsetY: Float,
    initScale: Float,
    cropPx: Float
): Uri? = try {
    val total = initScale * userScale
    val half = cropPx / 2f / total
    // Center of the crop square in bitmap coordinates
    val cx = src.width / 2f - userOffsetX / total
    val cy = src.height / 2f - userOffsetY / total

    val srcX = (cx - half).toInt().coerceIn(0, src.width - 1)
    val srcY = (cy - half).toInt().coerceIn(0, src.height - 1)
    val srcW = (half * 2f).toInt().coerceIn(1, src.width - srcX)
    val srcH = (half * 2f).toInt().coerceIn(1, src.height - srcY)
    val sq = minOf(srcW, srcH)

    val cropped = Bitmap.createBitmap(src, srcX, srcY, sq, sq)
    val output = if (sq > 1024) cropped.scale(1024, 1024) else cropped

    val file = File(context.cacheDir, "crop_${System.currentTimeMillis()}.jpg")
    file.outputStream().use { output.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    Uri.fromFile(file)
} catch (_: Exception) { null }
