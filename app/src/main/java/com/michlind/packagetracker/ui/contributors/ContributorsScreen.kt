package com.michlind.packagetracker.ui.contributors

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val INITIAL_RADIUS_DP = 140f
private const val MIN_RADIUS_DP = 18f
private const val INITIAL_SPEED_DP_PER_SEC = 220f
private const val SHRINK_FACTOR = 0.92f
private const val SPEED_UP_FACTOR = 1.10f
// Tap-forgiveness: hits register within 1.5x the visual radius, so the
// game stays fun rather than punishing as the circle gets tiny.
private const val HIT_RADIUS_MULTIPLIER = 1.5f

@SuppressLint("DiscouragedApi", "LocalContextResourcesRead")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContributorsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Look up the drawable at runtime so the build doesn't fail when the
    // image hasn't been added yet — falls back to a "TD" placeholder circle.
    val tamirResId = remember {
        context.resources.getIdentifier("tamir", "drawable", context.packageName)
    }

    val initialRadiusPx = with(density) { INITIAL_RADIUS_DP.dp.toPx() }
    val minRadiusPx = with(density) { MIN_RADIUS_DP.dp.toPx() }
    val initialSpeedPx = with(density) { INITIAL_SPEED_DP_PER_SEC.dp.toPx() }

    var fieldSize by remember { mutableStateOf(IntSize.Zero) }
    var position by remember { mutableStateOf(Offset.Zero) }
    var velocity by remember { mutableStateOf(Offset.Zero) }
    var radiusPx by remember { mutableFloatStateOf(initialRadiusPx) }
    var score by remember { mutableIntStateOf(0) }
    var bestScore by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var hasStarted by remember { mutableStateOf(false) }
    // Easter-egg gate: the game stays hidden until the user taps the
    // contributor's name 5 times.
    var gameUnlocked by remember { mutableStateOf(false) }
    var nameTapCount by remember { mutableIntStateOf(0) }

    fun launchAtRandom() {
        val angleDeg = Random.nextInt(0, 360).toDouble()
        val rad = Math.toRadians(angleDeg).toFloat()
        velocity = Offset(initialSpeedPx * cos(rad), initialSpeedPx * sin(rad))
    }

    fun resetAndStart() {
        if (fieldSize == IntSize.Zero) return
        score = 0
        radiusPx = initialRadiusPx
        position = Offset(
            x = fieldSize.width / 2f - radiusPx,
            y = fieldSize.height / 2f - radiusPx
        )
        launchAtRandom()
        playing = true
        hasStarted = true
    }

    // Auto-start once the play area has been measured.
    LaunchedEffect(fieldSize) {
        if (!hasStarted && fieldSize != IntSize.Zero) resetAndStart()
    }

    // Animation loop: re-launches whenever `playing` flips on, cancels when off.
    LaunchedEffect(playing, fieldSize) {
        if (!playing || fieldSize == IntSize.Zero) return@LaunchedEffect
        var lastTime = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - lastTime) / 1_000_000_000f).coerceAtMost(0.05f)
            lastTime = now

            val maxX = fieldSize.width - radiusPx * 2f
            val maxY = fieldSize.height - radiusPx * 2f
            var nx = position.x + velocity.x * dt
            var ny = position.y + velocity.y * dt
            var vx = velocity.x
            var vy = velocity.y

            if (nx < 0f) { nx = 0f; vx = kotlin.math.abs(vx) }
            else if (nx > maxX) { nx = maxX; vx = -kotlin.math.abs(vx) }
            if (ny < 0f) { ny = 0f; vy = kotlin.math.abs(vy) }
            else if (ny > maxY) { ny = maxY; vy = -kotlin.math.abs(vy) }

            position = Offset(nx, ny)
            velocity = Offset(vx, vy)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contributors") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (!gameUnlocked) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp, vertical = 32.dp)
            ) {
                Text(
                    text = "Special thanks",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Tamir Davidson",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            nameTapCount += 1
                            if (nameTapCount >= 5) gameUnlocked = true
                        }
                        .padding(vertical = 12.dp)
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Score row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Score: $score",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Best: $bestScore",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }

            // Play area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .onSizeChanged { fieldSize = it }
                    .pointerInput(playing) {
                        detectTapGestures { tap ->
                            if (!playing) return@detectTapGestures
                            val center = Offset(position.x + radiusPx, position.y + radiusPx)
                            val dist = (tap - center).getDistance()
                            if (dist <= radiusPx * HIT_RADIUS_MULTIPLIER) {
                                score += 1
                                if (score > bestScore) bestScore = score
                                radiusPx = (radiusPx * SHRINK_FACTOR).coerceAtLeast(minRadiusPx)
                                velocity = Offset(
                                    velocity.x * SPEED_UP_FACTOR,
                                    velocity.y * SPEED_UP_FACTOR
                                )
                            } else {
                                playing = false
                            }
                        }
                    }
            ) {
                if (fieldSize != IntSize.Zero) {
                    val diameterDp = with(density) { (radiusPx * 2f).toDp() }
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(position.x.toInt(), position.y.toInt()) }
                            .size(diameterDp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        if (tamirResId != 0) {
                            Image(
                                painter = painterResource(tamirResId),
                                contentDescription = "Tamir",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "TD",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                )
                            }
                        }
                    }
                }

                // Game over overlay
                if (!playing && hasStarted) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                shape = MaterialTheme.shapes.large
                            )
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (score == 0) "Missed!" else "Game over",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "You scored $score",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                        if (bestScore > 0) {
                            Text(
                                text = "Best: $bestScore",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { resetAndStart() }) {
                            Text("Play again")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
