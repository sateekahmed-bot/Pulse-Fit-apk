package com.example.ui.screens.tracking

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.LiveTrackingState
import com.example.data.model.HeartRateZone
import com.example.data.model.MotionActivity
import com.example.data.model.PersonBiometrics
import com.example.data.model.PersonTrackRecord
import com.example.data.model.RoutePoint
import com.example.ui.FitnessViewModel
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.FlameOrange
import com.example.ui.theme.NeonLime
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonTrackerScreen(
    viewModel: FitnessViewModel
) {
    val context = LocalContext.current
    val trackingState by viewModel.liveTrackingState.collectAsStateWithLifecycle()
    val allRecords by viewModel.allTrackRecords.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()

    var selectedActivity by remember { mutableStateOf(MotionActivity.RUNNING) }
    var showPulseDialog by remember { mutableStateOf(false) }

    // Check permissions
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    val biometrics = remember(userProfile) {
        PersonBiometrics(
            weightKg = userProfile.weightKg,
            heightCm = userProfile.heightCm,
            age = userProfile.age,
            gender = userProfile.gender
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Person Tracker",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (trackingState.isTracking) "Live Tracking Active • ${trackingState.detectedMotion}" else "PulseFit Real-time Biometrics",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (trackingState.isTracking) NeonLime else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Quick Heart Rate Pulse Adjuster / Tap Tool
                    IconButton(
                        onClick = { showPulseDialog = true },
                        modifier = Modifier.testTag("pulse_rate_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Pulse Monitor",
                            tint = Color(trackingState.heartRateZone.colorHex)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission Banner if location not granted
            if (!hasLocationPermission) {
                item {
                    PermissionNoticeCard(
                        onRequestPermissions = {
                            val perms = mutableListOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                perms.add(Manifest.permission.ACTIVITY_RECOGNITION)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                                perms.add(Manifest.permission.BODY_SENSORS)
                            }
                            permissionLauncher.launch(perms.toTypedArray())
                        }
                    )
                }
            }

            // Activity Type Selector (if not tracking)
            if (!trackingState.isTracking) {
                item {
                    ActivitySelectorSection(
                        selectedActivity = selectedActivity,
                        onSelectActivity = { selectedActivity = it }
                    )
                }
            }

            // Real-time GPS & Movement Radar Map Canvas
            item {
                LiveRadarMapCard(
                    trackingState = trackingState,
                    isTracking = trackingState.isTracking
                )
            }

            // Real-Time Pulse & Cardiac Zone HUD
            item {
                LiveHeartRateCard(
                    heartRateBpm = trackingState.heartRateBpm,
                    zone = trackingState.heartRateZone,
                    maxHeartRate = biometrics.maxHeartRate,
                    onOpenPulseDialog = { showPulseDialog = true }
                )
            }

            // 4-Metric Grid (Distance, Steps, Speed/Pace, Calories)
            item {
                MetricsGridCard(trackingState = trackingState)
            }

            // Controls (Start / Pause / Resume / Stop & Save)
            item {
                TrackingControlsSection(
                    trackingState = trackingState,
                    onStart = {
                        if (!hasLocationPermission) {
                            val perms = mutableListOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                perms.add(Manifest.permission.ACTIVITY_RECOGNITION)
                            }
                            permissionLauncher.launch(perms.toTypedArray())
                        }
                        viewModel.startPersonTracking(selectedActivity)
                    },
                    onPause = { viewModel.pausePersonTracking() },
                    onResume = { viewModel.resumePersonTracking() },
                    onStopAndSave = { viewModel.stopAndSavePersonTracking() },
                    onDiscard = { viewModel.discardPersonTracking() }
                )
            }

            // Person Vital Statistics & Biometrics Card
            item {
                PersonBiometricsCard(
                    userName = userProfile.name,
                    biometrics = biometrics
                )
            }

            // Past Tracked Sessions History
            item {
                Text(
                    text = "Tracked Sessions History (${allRecords.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (allRecords.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsRun,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No tracked sessions yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Hit 'Start Tracking' above to record live movement, GPS route & pulse!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(allRecords, key = { it.id }) { record ->
                    TrackRecordItem(
                        record = record,
                        onDelete = { viewModel.deleteTrackRecord(record) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    if (showPulseDialog) {
        ManualPulseDialog(
            currentBpm = trackingState.heartRateBpm,
            onDismiss = { showPulseDialog = false },
            onSaveBpm = { bpm ->
                viewModel.setManualHeartRate(bpm)
                showPulseDialog = false
            }
        )
    }
}

@Composable
fun PermissionNoticeCard(onRequestPermissions: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = FlameOrange.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.GpsNotFixed,
                contentDescription = null,
                tint = FlameOrange,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sensor & Location Access",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = FlameOrange
                )
                Text(
                    text = "Enable GPS and Motion sensors for real-time person route and step tracking.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onRequestPermissions,
                colors = ButtonDefaults.buttonColors(containerColor = FlameOrange),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("enable_gps_button")
            ) {
                Text("Enable", fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivitySelectorSection(
    selectedActivity: MotionActivity,
    onSelectActivity: (MotionActivity) -> Unit
) {
    Column {
        Text(
            text = "Select Tracking Activity",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MotionActivity.entries.forEach { act ->
                FilterChip(
                    selected = selectedActivity == act,
                    onClick = { onSelectActivity(act) },
                    label = { Text("${act.iconEmoji} ${act.displayName}") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = FlameOrange,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.testTag("activity_chip_${act.name}")
                )
            }
        }
    }
}

@Composable
fun LiveRadarMapCard(
    trackingState: LiveTrackingState,
    isTracking: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 36f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radius"
    )

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color(0xFF141923)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val center = Offset(w / 2f, h / 2f)

                // Background radar rings
                drawCircle(
                    color = Color(0xFF263238),
                    radius = w.coerceAtMost(h) * 0.42f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
                drawCircle(
                    color = Color(0xFF263238),
                    radius = w.coerceAtMost(h) * 0.25f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
                drawLine(
                    color = Color(0xFF263238),
                    start = Offset(center.x, 0f),
                    end = Offset(center.x, h),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = Color(0xFF263238),
                    start = Offset(0f, center.y),
                    end = Offset(w, center.y),
                    strokeWidth = 1.dp.toPx()
                )

                // Draw Route Points path if available
                val points = trackingState.routePoints
                if (points.size >= 2) {
                    val minLat = points.minOf { it.latitude }
                    val maxLat = points.maxOf { it.latitude }
                    val minLng = points.minOf { it.longitude }
                    val maxLng = points.maxOf { it.longitude }

                    val latSpan = (maxLat - minLat).coerceAtLeast(0.0001)
                    val lngSpan = (maxLng - minLng).coerceAtLeast(0.0001)

                    val path = Path()
                    val margin = 32.dp.toPx()
                    val drawW = w - 2 * margin
                    val drawH = h - 2 * margin

                    points.forEachIndexed { index, pt ->
                        val px = margin + ((pt.longitude - minLng) / lngSpan * drawW).toFloat()
                        val py = margin + ((maxLat - pt.latitude) / latSpan * drawH).toFloat()
                        if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }

                    drawPath(
                        path = path,
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFF00E5FF), Color(0xFF76FF03), Color(0xFFFF9100))
                        ),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw Start Marker
                    val startX = margin + ((points.first().longitude - minLng) / lngSpan * drawW).toFloat()
                    val startY = margin + ((maxLat - points.first().latitude) / latSpan * drawH).toFloat()
                    drawCircle(color = Color(0xFF00E5FF), radius = 6.dp.toPx(), center = Offset(startX, startY))

                    // Draw Current Position Pulsing Marker
                    val curX = margin + ((points.last().longitude - minLng) / lngSpan * drawW).toFloat()
                    val curY = margin + ((maxLat - points.last().latitude) / latSpan * drawH).toFloat()
                    drawCircle(
                        color = Color(0xFFFF9100).copy(alpha = pulseAlpha),
                        radius = pulseRadius,
                        center = Offset(curX, curY)
                    )
                    drawCircle(color = Color(0xFFFF9100), radius = 7.dp.toPx(), center = Offset(curX, curY))
                } else {
                    // Simulated radar scan / waiting indicator
                    if (isTracking) {
                        drawCircle(
                            color = CyanNeon.copy(alpha = pulseAlpha),
                            radius = pulseRadius * 2,
                            center = center
                        )
                        drawCircle(
                            color = CyanNeon,
                            radius = 6.dp.toPx(),
                            center = center
                        )
                    }
                }
            }

            // Top Status Bar on Map
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (trackingState.hasGpsFix) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
                            contentDescription = null,
                            tint = if (trackingState.hasGpsFix) NeonLime else Color(0xFFFF5252),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (trackingState.hasGpsFix) "GPS Locked (${String.format(Locale.getDefault(), "±%.1fm", trackingState.gpsAccuracyMeters)})" else "Acquiring GPS...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${trackingState.activity.iconEmoji} ${trackingState.activity.displayName}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlameOrange
                    )
                }
            }

            // Bottom Map telemetry overlay
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Motion: ${trackingState.detectedMotion}",
                    color = Color(0xFFE0E0E0),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Waypoints: ${trackingState.routePoints.size}",
                    color = CyanNeon,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun LiveHeartRateCard(
    heartRateBpm: Int,
    zone: HeartRateZone,
    maxHeartRate: Int,
    onOpenPulseDialog: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "heart_beat")
    val heartScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenPulseDialog() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(zone.colorHex).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Pulse Rate",
                    tint = Color(zone.colorHex),
                    modifier = Modifier
                        .size(30.dp)
                        .scale(heartScale)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$heartRateBpm",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(zone.colorHex)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "BPM",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Text(
                    text = "${zone.title} Zone (${zone.percentageRange} • Max: $maxHeartRate)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                color = Color(zone.colorHex).copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Tap to Adjust",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(zone.colorHex),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun MetricsGridCard(trackingState: LiveTrackingState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Row 1: Duration & Distance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(
                    label = "DURATION",
                    value = formatSeconds(trackingState.durationSeconds),
                    unit = "",
                    icon = Icons.Default.Timer,
                    iconTint = CyanNeon,
                    modifier = Modifier.weight(1f)
                )
                MetricItem(
                    label = "DISTANCE",
                    value = if (trackingState.distanceMeters >= 1000) {
                        String.format(Locale.getDefault(), "%.2f", trackingState.distanceMeters / 1000.0)
                    } else {
                        String.format(Locale.getDefault(), "%.0f", trackingState.distanceMeters)
                    },
                    unit = if (trackingState.distanceMeters >= 1000) "km" else "m",
                    icon = Icons.Default.DirectionsRun,
                    iconTint = NeonLime,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Row 2: Steps/Cadence & Speed/Pace
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(
                    label = "STEPS / CADENCE",
                    value = "${trackingState.stepCount}",
                    unit = "${trackingState.cadenceSpm} spm",
                    icon = Icons.Default.DirectionsWalk,
                    iconTint = Color(0xFFFFB74D),
                    modifier = Modifier.weight(1f)
                )
                MetricItem(
                    label = "ACTIVE CALORIES",
                    value = "${trackingState.caloriesBurned}",
                    unit = "kcal",
                    icon = Icons.Default.LocalFireDepartment,
                    iconTint = FlameOrange,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun MetricItem(
    label: String,
    value: String,
    unit: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TrackingControlsSection(
    trackingState: LiveTrackingState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStopAndSave: () -> Unit,
    onDiscard: () -> Unit
) {
    if (!trackingState.isTracking) {
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("start_tracking_button"),
            colors = ButtonDefaults.buttonColors(containerColor = FlameOrange),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start Live Person Tracking", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (trackingState.isPaused) {
                Button(
                    onClick = onResume,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("resume_tracking_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonLime),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = DarkBackground)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Resume", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = onPause,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("pause_tracking_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(imageVector = Icons.Default.Pause, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pause", fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = onStopAndSave,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .testTag("finish_tracking_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Finish & Save", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PersonBiometricsCard(
    userName: String,
    biometrics: PersonBiometrics
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Personal Biometrics: $userName",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    color = EmeraldGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "BMI: ${String.format(Locale.getDefault(), "%.1f", biometrics.bmi)} (${biometrics.bmiCategory})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldGreen,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Age & Sex", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${biometrics.age} yrs • ${biometrics.gender}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Column {
                    Text("Height / Weight", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${biometrics.heightCm.toInt()}cm / ${biometrics.weightKg.toInt()}kg", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Column {
                    Text("Max Heart Rate", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${biometrics.maxHeartRate} BPM", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = FlameOrange)
                }
            }
        }
    }
}

@Composable
fun TrackRecordItem(
    record: PersonTrackRecord,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(FlameOrange.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsRun,
                    contentDescription = null,
                    tint = FlameOrange,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${record.activityType} • ${record.getFormattedDistance()}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${record.getFormattedDuration()} • ${record.steps} steps • ${record.caloriesBurned} kcal • ${record.avgHeartRate} BPM",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete record",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ManualPulseDialog(
    currentBpm: Int,
    onDismiss: () -> Unit,
    onSaveBpm: (Int) -> Unit
) {
    var bpmSlider by remember { mutableStateOf(currentBpm.toFloat()) }
    var tapTimes by remember { mutableStateOf(listOf<Long>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pulse & Heart Rate Monitor", fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${bpmSlider.toInt()}",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FlameOrange
                )
                Text(
                    text = "Beats Per Minute (BPM)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Slider(
                    value = bpmSlider,
                    onValueChange = { bpmSlider = it },
                    valueRange = 50f..200f,
                    colors = SliderDefaults.colors(
                        thumbColor = FlameOrange,
                        activeTrackColor = FlameOrange
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Tap-along button to measure actual heart rate
                Button(
                    onClick = {
                        val now = System.currentTimeMillis()
                        val updated = (tapTimes + now).takeLast(6)
                        tapTimes = updated
                        if (updated.size >= 3) {
                            val intervals = updated.zipWithNext { a, b -> b - a }
                            val avgInterval = intervals.average()
                            if (avgInterval > 250) {
                                val calculatedBpm = (60000.0 / avgInterval).toInt().coerceIn(50, 200)
                                bpmSlider = calculatedBpm.toFloat()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Favorite, contentDescription = null, tint = FlameOrange)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tap Here with Pulse (${tapTimes.size} taps)", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveBpm(bpmSlider.toInt()) },
                colors = ButtonDefaults.buttonColors(containerColor = FlameOrange)
            ) {
                Text("Apply BPM")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun formatSeconds(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    val hrs = mins / 60
    return if (hrs > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hrs, mins % 60, secs)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }
}
