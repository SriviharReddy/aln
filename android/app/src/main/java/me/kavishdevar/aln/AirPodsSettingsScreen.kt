package me.kavishdevar.aln

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlin.math.roundToInt

@Composable
fun BatteryView() {
    val batteryStatus = remember { mutableStateOf<List<Battery>>(listOf()) }
    @Suppress("DEPRECATION") val batteryReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                batteryStatus.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { intent.getParcelableArrayListExtra("data", Battery::class.java) } else { intent.getParcelableArrayListExtra("data") }?.toList() ?: listOf()
            }
        }
    }
    val context = LocalContext.current

    LaunchedEffect(context) {
        val batteryIntentFilter = IntentFilter(AirPodsNotifications.BATTERY_DATA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(batteryReceiver, batteryIntentFilter, Context.RECEIVER_EXPORTED)
        }
    }

    Row {
        Column (
            modifier = Modifier
                .fillMaxWidth(0.5f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image (
                bitmap = ImageBitmap.imageResource(R.drawable.pro_2_buds),
                contentDescription = "Buds",
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(0.50f)
            )
            val left = batteryStatus.value.find { it.component == BatteryComponent.LEFT }
            val right = batteryStatus.value.find { it.component == BatteryComponent.RIGHT }
            if ((right?.status == BatteryStatus.CHARGING && left?.status == BatteryStatus.CHARGING) || (left?.status == BatteryStatus.NOT_CHARGING && right?.status == BatteryStatus.NOT_CHARGING))
            {
                BatteryIndicator(right.level.let { left.level.coerceAtMost(it) }, left.status == BatteryStatus.CHARGING)
            }
            else {
                Row {
                    if (left?.status != BatteryStatus.DISCONNECTED) {
                        Text(text = "\uDBC6\uDCE5", fontFamily = FontFamily(Font(R.font.sf_pro)))
                        BatteryIndicator(left?.level ?: 0, left?.status == BatteryStatus.CHARGING)
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    if (right?.status != BatteryStatus.DISCONNECTED) {
                        Text(text = "\uDBC6\uDCE8", fontFamily = FontFamily(Font(R.font.sf_pro)))
                        BatteryIndicator(right?.level ?: 0, right?.status == BatteryStatus.CHARGING)
                    }
                }
            }
        }

        Column (
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val case = batteryStatus.value.find { it.component == BatteryComponent.CASE }

            Image(
                bitmap = ImageBitmap.imageResource(R.drawable.pro_2_case),
                contentDescription = "Case",
                modifier = Modifier
                    .fillMaxWidth()
            )
            BatteryIndicator(case?.level ?: 0)
        }
    }
}

@SuppressLint("MissingPermission", "NewApi")
@Composable
fun AirPodsSettingsScreen(paddingValues: PaddingValues, device: BluetoothDevice?, service: AirPodsService?,
                          navController: NavController) {
    var deviceName by remember { mutableStateOf(TextFieldValue(device?.name ?: "AirPods Pro (fallback, should never show up)")) }

    val verticalScrollState  = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(vertical = 24.dp, horizontal = 12.dp)
            .verticalScroll(
                state = verticalScrollState,
                enabled = true,
            )
    ) {
        LaunchedEffect(service) {
            service?.let {
                it.sendBroadcast(Intent(AirPodsNotifications.BATTERY_DATA).apply {
                    putParcelableArrayListExtra("data", ArrayList(it.getBattery()))
                })
                it.sendBroadcast(Intent(AirPodsNotifications.ANC_DATA).apply {
                    putExtra("data", it.getANC())
                })
            }
        }
        BatteryView()
        val sharedPreferences = LocalContext.current.getSharedPreferences("settings", Context.MODE_PRIVATE)

        if (service != null) {
            StyledTextField(
                name = "Name",
                value = deviceName.text,
                onValueChange = { deviceName = TextFieldValue(it) }
            )

            Spacer(modifier = Modifier.height(32.dp))

            NoiseControlSettings(service = service)

            Spacer(modifier = Modifier.height(16.dp))
            AudioSettings(service = service, sharedPreferences = sharedPreferences)

            Spacer(modifier = Modifier.height(16.dp))

            IndependentToggle(name = "Automatic Ear Detection", service = service, functionName = "setEarDetection", sharedPreferences = sharedPreferences, true)

//            Spacer(modifier = Modifier.height(16.dp))

//            val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5
//            val textColor = if (isDarkTheme) Color.White else Color.Black

            // localstorage stuff
            // TODO: localstorage and call the setButtons() with previous configuration and new configuration
//            Box (
//                modifier = Modifier
//                    .padding(vertical = 8.dp)
//                    .background(
//                        if (isDarkTheme) Color(0xFF1C1B20) else Color(0xFFFFFFFF),
//                        RoundedCornerShape(14.dp)
//                    )
//            )
//            {
//                // TODO: A Column Rows with text at start and a check mark if ticked
//            }

            Spacer(modifier = Modifier.height(16.dp))

            Row (
                modifier = Modifier
                    .background(if (MaterialTheme.colorScheme.surface.luminance() < 0.5) Color(0xFF1C1B20) else Color(0xFFFFFFFF), RoundedCornerShape(14.dp))
                    .height(55.dp)
                    .clickable {
                        navController.navigate("debug")
                    }
            ) {
                Text(text = "Debug", modifier = Modifier.padding(16.dp), color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5) Color.White else Color.Black)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { navController.navigate("debug") },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5) Color.White else Color.Black ),
                    modifier = Modifier.padding(start = 16.dp).fillMaxHeight()
                ) {
                    @Suppress("DEPRECATION")
                    Icon(imageVector = Icons.Default.KeyboardArrowRight, contentDescription = "Debug")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoiseControlSlider(service: AirPodsService, sharedPreferences: SharedPreferences) {
    val sliderValue = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(sliderValue) {
        if (sharedPreferences.contains("adaptive_strength")) {
            sliderValue.floatValue = sharedPreferences.getInt("adaptive_strength", 0).toFloat()
        }
    }
    LaunchedEffect(sliderValue.floatValue) {
        sharedPreferences.edit().putInt("adaptive_strength", sliderValue.floatValue.toInt()).apply()
    }

    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5

    val trackColor = if (isDarkTheme) Color(0xFFB3B3B3) else Color(0xFFD9D9D9)
    val activeTrackColor = if (isDarkTheme) Color(0xFFFFFFFF) else Color(0xFF007AFF)
    val thumbColor = if (isDarkTheme) Color(0xFFFFFFFF) else Color(0xFFFFFFFF)
    val labelTextColor = if (isDarkTheme) Color.White else Color.Black

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Slider
        Slider(
            value = sliderValue.floatValue,
            onValueChange = {
                sliderValue.floatValue = it
                service.setAdaptiveStrength(100 - it.toInt())
            },
            valueRange = 0f..100f,
            onValueChangeFinished = {
                // Round the value when the user stops sliding
                sliderValue.floatValue = sliderValue.floatValue.roundToInt().toFloat()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),  // Adjust height to ensure thumb fits well
            colors = SliderDefaults.colors(
                thumbColor = thumbColor,
                activeTrackColor = activeTrackColor,
                inactiveTrackColor = trackColor
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(24.dp)  // Circular thumb size
                        .shadow(4.dp, CircleShape)  // Apply shadow to the thumb
                        .background(thumbColor, CircleShape)  // Circular thumb
                )
            },
            track = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .background(trackColor, RoundedCornerShape(6.dp))
                )
            }
        )

        // Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Less Noise",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    color = labelTextColor
                ),
                modifier = Modifier.padding(start = 4.dp)
            )
            Text(
                text = "More Noise",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    color = labelTextColor
                ),
                modifier = Modifier.padding(end = 4.dp)
            )
        }
    }
}

@Preview
@Composable
fun Preview() {
    IndependentToggle("Case Charging Sounds", AirPodsService(), "setCaseChargingSounds", LocalContext.current.getSharedPreferences("settings", Context.MODE_PRIVATE))
}

@Composable
fun IndependentToggle(name: String, service: AirPodsService, functionName: String, sharedPreferences: SharedPreferences, default: Boolean = false) {
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5
    val textColor = if (isDarkTheme) Color.White else Color.Black

    // Standardize the key
    val snakeCasedName = name.replace(Regex("[\\W\\s]+"), "_").lowercase()

    // State for the toggle
    var checked by remember { mutableStateOf(default) }

    // Load initial state from SharedPreferences
    LaunchedEffect(sharedPreferences) {
        checked = sharedPreferences.getBoolean(snakeCasedName, true)
    }
    Box (
        modifier = Modifier
            .padding(vertical = 8.dp)
            .background(
                if (isDarkTheme) Color(0xFF1C1B20) else Color(0xFFFFFFFF),
                RoundedCornerShape(14.dp)
            )
    )
    {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .padding(horizontal = 12.dp)
                .clickable {
                    // Toggle checked state and save to SharedPreferences
                    checked = !checked
                    sharedPreferences
                        .edit()
                        .putBoolean(snakeCasedName, checked)
                        .apply()

                    // Call the corresponding method in the service
                    val method = service::class.java.getMethod(functionName, Boolean::class.java)
                    method.invoke(service, checked)
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = name, modifier = Modifier.weight(1f), fontSize = 16.sp, color = textColor)
            StyledSwitch(
                checked = checked,
                onCheckedChange = {
                    checked = it
                    sharedPreferences.edit().putBoolean(snakeCasedName, it).apply()

                    // Call the corresponding method in the service
                    val method = service::class.java.getMethod(functionName, Boolean::class.java)
                    method.invoke(service, it)
                },
            )
        }
    }
}

@Composable
fun AudioSettings(service: AirPodsService, sharedPreferences: SharedPreferences) {
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5
    val textColor = if (isDarkTheme) Color.White else Color.Black

    // Load the conversational awareness state from sharedPreferences
    var conversationalAwarenessEnabled by remember {
        mutableStateOf(
            sharedPreferences.getBoolean("conversational_awareness", true)
        )
    }

    // Update the service when the toggle is changed
    fun updateConversationalAwareness(enabled: Boolean) {
        conversationalAwarenessEnabled = enabled
        sharedPreferences.edit().putBoolean("conversational_awareness", enabled).apply()
        service.setCAEnabled(enabled)
    }

    Text(
        text = "AUDIO",
        style = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            color = textColor.copy(alpha = 0.6f)
        ),
        modifier = Modifier.padding(8.dp, bottom = 2.dp)
    )

    val backgroundColor = if (isDarkTheme) Color(0xFF1C1B20) else Color(0xFFFFFFFF)
    val isPressed = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(14.dp))
            .padding(top = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isPressed.value) Color(0xFFE0E0E0) else Color.Transparent
                )
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .pointerInput(Unit) { // Detect press state for iOS-like effect
                    detectTapGestures(
                        onPress = {
                            isPressed.value = true
                            tryAwaitRelease() // Wait until release
                            isPressed.value = false
                        }
                    )
                }
                .clickable(
                    indication = null, // Disable ripple effect
                    interactionSource = remember { MutableInteractionSource() } // Required for clickable
                ) {
                    // Toggle the conversational awareness value
                    updateConversationalAwareness(!conversationalAwarenessEnabled)
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Conversational Awareness",
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
                color = textColor
            )

            StyledSwitch(
                checked = conversationalAwarenessEnabled,
                onCheckedChange = {
                    updateConversationalAwareness(it)
                },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Adaptive Audio",
                modifier = Modifier
                    .padding(end = 8.dp, bottom = 2.dp, start = 2.dp)
                    .fillMaxWidth(),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )
            )
            Text(
                text = "Adaptive audio dynamically responds to your environment and cancels or allows external noise. You can customize Adaptive Audio to allow more or less noise.",
                modifier = Modifier
                    .padding(8.dp, top = 2.dp)
                    .fillMaxWidth(),
                style = TextStyle(
                    fontSize = 12.sp,
                    color = textColor.copy(alpha = 0.6f)
                )
            )

            NoiseControlSlider(service = service, sharedPreferences = sharedPreferences)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun NoiseControlSettings(service: AirPodsService) {
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5
    val backgroundColor = if (isDarkTheme) Color(0xFF1C1B20) else Color(0xFFE3E3E8)
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val textColorSelected = if (isDarkTheme) Color.White else Color.Black
    val selectedBackground = if (isDarkTheme) Color(0xFF5C5A5F) else Color(0xFFFFFFFF)

    val noiseControlMode = remember { mutableStateOf(NoiseControlMode.OFF) }

    val noiseControlReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                noiseControlMode.value = NoiseControlMode.entries.toTypedArray()[intent.getIntExtra("data", 3) - 1]
            }
        }
    }

    val context = LocalContext.current
    val noiseControlIntentFilter = IntentFilter(AirPodsNotifications.ANC_DATA)
    context.registerReceiver(noiseControlReceiver, noiseControlIntentFilter, Context.RECEIVER_EXPORTED)

//    val paddingAnim by animateDpAsState(
//        targetValue = when (noiseControlMode.value) {
//            NoiseControlMode.OFF -> 0.dp
//            NoiseControlMode.TRANSPARENCY -> 150.dp
//            NoiseControlMode.ADAPTIVE -> 250.dp
//            NoiseControlMode.NOISE_CANCELLATION -> 350.dp
//        }, label = ""
//    )

    val d1a = remember { mutableFloatStateOf(0f) }
    val d2a = remember { mutableFloatStateOf(0f) }
    val d3a = remember { mutableFloatStateOf(0f) }

    fun onModeSelected(mode: NoiseControlMode) {
        noiseControlMode.value = mode
        service.setANCMode(mode.ordinal+1)
        when (mode) {
            NoiseControlMode.NOISE_CANCELLATION -> {
                d1a.floatValue = 1f
                d2a.floatValue = 1f
                d3a.floatValue = 0f
            }
            NoiseControlMode.OFF -> {
                d1a.floatValue = 0f
                d2a.floatValue = 1f
                d3a.floatValue = 1f
            }
            NoiseControlMode.ADAPTIVE -> {
                d1a.floatValue = 1f
                d2a.floatValue = 0f
                d3a.floatValue = 0f
            }
            NoiseControlMode.TRANSPARENCY -> {
                d1a.floatValue = 0f
                d2a.floatValue = 0f
                d3a.floatValue = 1f
            }
        }
    }

    Text(
        text = "NOISE CONTROL",
        style = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            color = textColor.copy(alpha = 0.6f)
        ),
        modifier = Modifier.padding(8.dp, bottom = 2.dp)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(75.dp)
                .padding(8.dp)
        ) {
//            Box(
//                modifier = Modifier
//                    .fillMaxHeight()
//                    .width(80.dp)
//                    .offset(x = paddingAnim)
//                    .background(selectedBackground, RoundedCornerShape(8.dp))
//            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor, RoundedCornerShape(14.dp))
            ) {
                NoiseControlButton(
                    icon = ImageBitmap.imageResource(R.drawable.noise_cancellation),
                    onClick = { onModeSelected(NoiseControlMode.OFF) },
                    textColor = if (noiseControlMode.value == NoiseControlMode.OFF) textColorSelected else textColor,
                    backgroundColor = if (noiseControlMode.value == NoiseControlMode.OFF) selectedBackground else Color.Transparent,
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(
                    thickness = 1.dp,
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .alpha(d1a.floatValue),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                )
                NoiseControlButton(
                    icon = ImageBitmap.imageResource(R.drawable.transparency),
                    onClick = { onModeSelected(NoiseControlMode.TRANSPARENCY) },
                    textColor = if (noiseControlMode.value == NoiseControlMode.TRANSPARENCY) textColorSelected else textColor,
                    backgroundColor = if (noiseControlMode.value == NoiseControlMode.TRANSPARENCY) selectedBackground else Color.Transparent,
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(
                    thickness = 1.dp,
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .alpha(d2a.floatValue),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                )
                NoiseControlButton(
                    icon = ImageBitmap.imageResource(R.drawable.adaptive),
                    onClick = { onModeSelected(NoiseControlMode.ADAPTIVE) },
                    textColor = if (noiseControlMode.value == NoiseControlMode.ADAPTIVE) textColorSelected else textColor,
                    backgroundColor = if (noiseControlMode.value == NoiseControlMode.ADAPTIVE) selectedBackground else Color.Transparent,
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(
                    thickness = 1.dp,
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .alpha(d3a.floatValue),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                )
                NoiseControlButton(
                    icon = ImageBitmap.imageResource(R.drawable.noise_cancellation),
                    onClick = { onModeSelected(NoiseControlMode.NOISE_CANCELLATION) },
                    textColor = if (noiseControlMode.value == NoiseControlMode.NOISE_CANCELLATION) textColorSelected else textColor,
                    backgroundColor = if (noiseControlMode.value == NoiseControlMode.NOISE_CANCELLATION) selectedBackground else Color.Transparent,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(top = 1.dp)
        ) {
            Text(
                text = "Off",
                style = TextStyle(fontSize = 12.sp, color = textColor),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Transparency",
                style = TextStyle(fontSize = 12.sp, color = textColor),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Adaptive",
                style = TextStyle(fontSize = 12.sp, color = textColor),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Noise Cancellation",
                style = TextStyle(fontSize = 12.sp, color = textColor),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun NoiseControlButton(
    icon: ImageBitmap,
    onClick: () -> Unit,
    textColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .background(color = backgroundColor, shape = RoundedCornerShape(11.dp))
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            bitmap = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(40.dp)
        )
    }
}

enum class NoiseControlMode {
    OFF,  NOISE_CANCELLATION, TRANSPARENCY, ADAPTIVE
}

@Composable
fun StyledSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5

    val thumbColor = Color.White
    val trackColor = if (checked) Color(0xFF34C759) else if (isDarkTheme) Color(0xFF5B5B5E) else Color(0xFFD1D1D6)

    // Animate the horizontal offset of the thumb
    val thumbOffsetX by animateDpAsState(targetValue = if (checked) 20.dp else 0.dp, label = "Test")

    Box(
        modifier = Modifier
            .width(51.dp)
            .height(31.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(trackColor) // Dynamic track background
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffsetX) // Animate the offset for smooth transition
                .size(27.dp)
                .clip(CircleShape)
                .background(thumbColor) // Dynamic thumb color
                .clickable { onCheckedChange(!checked) } // Make the switch clickable
        )
    }
}

@Composable
fun StyledTextField(
    name: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5

    val backgroundColor = if (isDarkTheme) Color(0xFF1C1B20) else Color(0xFFFFFFFF)
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val cursorColor = if (isDarkTheme) Color.White else Color.Black

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(55.dp)
            .background(
                backgroundColor,
                RoundedCornerShape(14.dp)
            ) // Dynamic background based on theme
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = name,
            style = TextStyle(
                fontSize = 16.sp,
                color = textColor // Text color based on theme
            )
        )

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                color = textColor, // Dynamic text color
                fontSize = 16.sp,
            ),
            cursorBrush = SolidColor(cursorColor), // Dynamic cursor color
            decorationBox = { innerTextField ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    innerTextField()
                }
            },
            modifier = Modifier
                .fillMaxWidth() // Ensures text field takes remaining available space
                .padding(start = 8.dp), // Padding to adjust spacing between text field and icon,
        )
    }
}

@Composable
fun BatteryIndicator(batteryPercentage: Int, charging: Boolean = false) {
    val batteryOutlineColor = Color(0xFFBFBFBF) // Light gray outline
    val batteryFillColor = if (batteryPercentage > 30) Color(0xFF30D158) else Color(0xFFFC3C3C)
    val batteryTextColor = MaterialTheme.colorScheme.onSurface

    // Battery indicator dimensions
    val batteryWidth = 30.dp
    val batteryHeight = 15.dp
    val batteryCornerRadius = 4.dp
    val tipWidth = 5.dp
    val tipHeight = batteryHeight * 0.3f

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Row for battery icon and tip
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.padding(bottom = 4.dp) // Padding between icon and percentage text
        ) {
            // Battery Icon
            Box(
                modifier = Modifier
                    .width(batteryWidth)
                    .height(batteryHeight)
                    .border(1.dp, batteryOutlineColor, RoundedCornerShape(batteryCornerRadius))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(2.dp)
                        .width(batteryWidth * (batteryPercentage / 100f))
                        .background(batteryFillColor, RoundedCornerShape(2.dp))
                )
                if (charging) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(), // Take up the entire size of the outer Box
                        contentAlignment = Alignment.Center // Center the charging bolt within the Box
                    ) {
                        Text(
                            text = "\uDBC0\uDEE6",
                            fontSize = 12.sp,
                            fontFamily = FontFamily(Font(R.font.sf_pro)),
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }

            // Battery Tip (Protrusion)
            Box(
                modifier = Modifier
                    .width(tipWidth)
                    .height(tipHeight)
                    .padding(start = 1.dp)
                    .background(
                        batteryOutlineColor,
                        RoundedCornerShape(
                            topStart = 0.dp,
                            topEnd = 12.dp,
                            bottomStart = 0.dp,
                            bottomEnd = 12.dp
                        )
                    )
            )
        }

        // Battery Percentage Text
        Text(
            text = "$batteryPercentage%",
            color = batteryTextColor,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
        )
    }
}