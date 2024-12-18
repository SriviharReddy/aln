package me.kavishdevar.aln

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass

@Suppress("unused")
class AirPodsService: Service() {
    inner class LocalBinder : Binder() {
        fun getService(): AirPodsService = this@AirPodsService
    }

    override fun onBind(intent: Intent?): IBinder {
        return LocalBinder()
    }

    fun showPopup(service: Service, name: String) {
        val window = Window(service.applicationContext)
        window.open(name, batteryNotification)
    }

    private object Receiver: BroadcastReceiver() {
        @SuppressLint("NewApi", "MissingPermission")
        override fun onReceive(context: Context?, intent: Intent) {
            val bluetoothDevice =
                intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE", BluetoothDevice::class.java)
            val action = intent.action
            val context = context?.applicationContext
            val name = context?.getSharedPreferences("settings", MODE_PRIVATE)?.getString("name", bluetoothDevice?.name)
            if (bluetoothDevice != null && action != null && !action.isEmpty()) {
                Log.d("BluetoothReceiver", "Received broadcast")
                if (BluetoothDevice.ACTION_ACL_CONNECTED == action) {
                    val uuid = ParcelUuid.fromString("74ec2172-0bad-4d01-8f77-997b2be0722a")
                    if (bluetoothDevice.uuids.contains(uuid)) {
                        Log.d("AirPodsService", "Service started")
                        val intent = Intent(AirPodsNotifications.AIRPODS_CONNECTION_DETECTED)
                        intent.putExtra("name", name)
                        intent.putExtra("device", bluetoothDevice)
                        context?.sendBroadcast(intent)
                    }
                }

                // Airpods disconnected, remove notification but leave the scanner going.
                if (BluetoothDevice.ACTION_ACL_DISCONNECTED == action
                    || BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED == action
                ) {
                    Log.d("AirPodsService", "Closed Socket")
                    context?.sendBroadcast(
                        Intent(AirPodsNotifications.AIRPODS_DISCONNECTED)
                    )
                }
            }
        }
    }

    var isConnected = false
    var device: BluetoothDevice? = null

    fun startForegroundNotification() {
        val notificationChannel = NotificationChannel(
            "airpods",
            "AirPods",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(notificationChannel)

        val notification = NotificationCompat.Builder(this, "airpods")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("AirPods Service Running")
            .setContentText("AirPods service is running in the background.")
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(2, notification)
    }

    @SuppressLint("InlinedApi", "MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("AirPodsService", "Service started")
        startForegroundNotification()
        registerReceiver(Receiver, BluetoothReceiver.buildFilter(), RECEIVER_EXPORTED)

        registerReceiver(object: BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val name = this@AirPodsService.getSharedPreferences("settings", MODE_PRIVATE).getString("name", device?.name)
                device = intent?.getParcelableExtra("device", BluetoothDevice::class.java)!!
                showPopup(this@AirPodsService, name.toString())
                connectToSocket(device!!)
                sendBroadcast(Intent(AirPodsNotifications.AIRPODS_CONNECTED).apply {
                    putExtra("device", device)
                })
            }
        }, IntentFilter(AirPodsNotifications.AIRPODS_CONNECTION_DETECTED), RECEIVER_EXPORTED)

        registerReceiver(object: BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                device = null
                isConnected = false
            }
        }, IntentFilter(AirPodsNotifications.AIRPODS_DISCONNECTED), RECEIVER_EXPORTED)

        val bluetoothAdapter = getSystemService(BluetoothManager::class.java).adapter
         bluetoothAdapter.bondedDevices.forEach { device ->
            if (device.uuids.contains(ParcelUuid.fromString("74ec2172-0bad-4d01-8f77-997b2be0722a"))) {
                bluetoothAdapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        if (profile == BluetoothProfile.A2DP) {
                            val connectedDevices = proxy.connectedDevices
                            if (connectedDevices.isNotEmpty()) {
                                connectToSocket(device)
                            }
                        }
                        bluetoothAdapter.closeProfileProxy(profile, proxy)
                    }

                    override fun onServiceDisconnected(profile: Int) { }
                }, BluetoothProfile.A2DP)
            }
        }

        return START_STICKY
    }

    private lateinit var socket: BluetoothSocket

    @SuppressLint("MissingPermission", "UnspecifiedRegisterReceiverFlag")
    fun connectToSocket(device: BluetoothDevice) {
        HiddenApiBypass.addHiddenApiExemptions("Landroid/bluetooth/BluetoothSocket;")
        val uuid: ParcelUuid = ParcelUuid.fromString("74ec2172-0bad-4d01-8f77-997b2be0722a")

        try {
            socket = HiddenApiBypass.newInstance(
                BluetoothSocket::class.java,
                3,
                true,
                true,
                device,
                0x1001,
                uuid
            ) as BluetoothSocket
        }
        catch (
            e: Exception
        ) {
            e.printStackTrace()
            try {
                socket = HiddenApiBypass.newInstance(
                    BluetoothSocket::class.java,
                    3,
                    1,
                    true,
                    true,
                    device,
                    0x1001,
                    uuid
                ) as BluetoothSocket
            }
            catch (
                e: Exception
            ) {
                e.printStackTrace()
            }
        }

        try {
            socket.connect()
            this@AirPodsService.device = device
            isConnected = true
            socket.let { it ->
                CoroutineScope(Dispatchers.IO).launch {
                    it.outputStream.write(Enums.HANDSHAKE.value)
                    it.outputStream.flush()
                    delay(500)
                    it.outputStream.write(Enums.SET_SPECIFIC_FEATURES.value)
                    it.outputStream.flush()
                    delay(500)
                    it.outputStream.write(Enums.REQUEST_NOTIFICATIONS.value)
                    it.outputStream.flush()
                    Log.d("AirPodsService","This should run first")
                }
                Log.d("AirPodsService","This should run later")
                sendBroadcast(
                    Intent(AirPodsNotifications.AIRPODS_CONNECTED)
                        .putExtra("device", device)
                )
                CoroutineScope(Dispatchers.IO).launch {
                    while (socket.isConnected == true) {
                        socket.let {
                            val audioManager = this@AirPodsService.getSystemService(AUDIO_SERVICE) as AudioManager
                            MediaController.initialize(audioManager)
                            val buffer = ByteArray(1024)
                            val bytesRead = it.inputStream.read(buffer)
                            var data: ByteArray = byteArrayOf()
                            if (bytesRead > 0) {
                                data = buffer.copyOfRange(0, bytesRead)
                                sendBroadcast(Intent(AirPodsNotifications.AIRPODS_DATA).apply {
                                    putExtra("data", buffer.copyOfRange(0, bytesRead))
                                })
                                val bytes = buffer.copyOfRange(0, bytesRead)
                                val formattedHex = bytes.joinToString(" ") { "%02X".format(it) }
                                Log.d("AirPods Data", "Data received: $formattedHex")
                            }
                            else if (bytesRead == -1) {
                                Log.d("AirPods Service", "Socket closed (bytesRead = -1)")
                                socket.close()
                                sendBroadcast(Intent(AirPodsNotifications.AIRPODS_DISCONNECTED))
                                return@launch
                            }
                            var inEar = false
                            var inEarData = listOf<Boolean>()
                            if (earDetectionNotification.isEarDetectionData(data)) {
                                earDetectionNotification.setStatus(data)
                                sendBroadcast(Intent(AirPodsNotifications.EAR_DETECTION_DATA).apply {
                                    val list = earDetectionNotification.status
                                    val bytes = ByteArray(2)
                                    bytes[0] = list[0]
                                    bytes[1] = list[1]
                                    putExtra("data", bytes)
                                })
                                Log.d("AirPods Parser", "Ear Detection: ${earDetectionNotification.status[0]} ${earDetectionNotification.status[1]}")
                                var justEnabledA2dp = false
                                val earReceiver = object : BroadcastReceiver() {
                                    override fun onReceive(context: Context, intent: Intent) {
                                        val data = intent.getByteArrayExtra("data")
                                        if (data != null && earDetectionEnabled) {
                                            inEar = if (data.find { it == 0x02.toByte() } != null || data.find { it == 0x03.toByte() } != null) {
                                                data[0] == 0x00.toByte() || data[1] == 0x00.toByte()
                                            } else {
                                                data[0] == 0x00.toByte() && data[1] == 0x00.toByte()
                                            }

                                            val newInEarData = listOf(data[0] == 0x00.toByte(), data[1] == 0x00.toByte())
                                            if (newInEarData.contains(true) && inEarData == listOf(false, false)) {
                                                connectAudio(this@AirPodsService, device)
                                                justEnabledA2dp = true
                                                val bluetoothAdapter = this@AirPodsService.getSystemService(BluetoothManager::class.java).adapter
                                                bluetoothAdapter.getProfileProxy(
                                                    this@AirPodsService, object : BluetoothProfile.ServiceListener {
                                                        override fun onServiceConnected(
                                                            profile: Int,
                                                            proxy: BluetoothProfile
                                                        ) {
                                                            if (profile == BluetoothProfile.A2DP) {
                                                                val connectedDevices =
                                                                    proxy.connectedDevices
                                                                if (connectedDevices.isNotEmpty()) {
                                                                    MediaController.sendPlay()
                                                                }
                                                            }
                                                            bluetoothAdapter.closeProfileProxy(
                                                                profile,
                                                                proxy
                                                            )
                                                        }

                                                        override fun onServiceDisconnected(
                                                            profile: Int
                                                        ) {
                                                        }
                                                    }
                                                    ,BluetoothProfile.A2DP
                                                )

                                            }
                                            else if (newInEarData == listOf(false, false)){
                                                disconnectAudio(this@AirPodsService, device)
                                            }

                                            inEarData = newInEarData

                                            if (inEar == true) {
                                                if (!justEnabledA2dp) {
                                                    justEnabledA2dp = false
                                                    MediaController.sendPlay()
                                                }
                                            } else {
                                                MediaController.sendPause()
                                            }
                                        }
                                    }
                                }

                                val earIntentFilter = IntentFilter(AirPodsNotifications.EAR_DETECTION_DATA)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    this@AirPodsService.registerReceiver(earReceiver, earIntentFilter,
                                        RECEIVER_EXPORTED
                                    )
                                } else {
                                    this@AirPodsService.registerReceiver(earReceiver, earIntentFilter)
                                }
                            }
                            else if (ancNotification.isANCData(data)) {
                                ancNotification.setStatus(data)
                                sendBroadcast(Intent(AirPodsNotifications.ANC_DATA).apply {
                                    putExtra("data", ancNotification.status)
                                })
                                Log.d("AirPods Parser", "ANC: ${ancNotification.status}")
                            }
                            else if (batteryNotification.isBatteryData(data)) {
                                batteryNotification.setBattery(data)
                                sendBroadcast(Intent(AirPodsNotifications.BATTERY_DATA).apply {
                                    putParcelableArrayListExtra("data", ArrayList(batteryNotification.getBattery()))
                                })
                                for (battery in batteryNotification.getBattery()) {
                                    Log.d("AirPods Parser", "${battery.getComponentName()}: ${battery.getStatusName()} at ${battery.level}% ")
                                }
//                                if both are charging, disconnect audio profiles
                                if (batteryNotification.getBattery()[0].status == 1 && batteryNotification.getBattery()[1].status == 1) {
                                    disconnectAudio(this@AirPodsService, device)
                                }
                                else {
                                    connectAudio(this@AirPodsService, device)
                                }
//                                updatePodsStatus(device!!, batteryNotification.getBattery())
                            }
                            else if (conversationAwarenessNotification.isConversationalAwarenessData(data)) {
                                conversationAwarenessNotification.setData(data)
                                sendBroadcast(Intent(AirPodsNotifications.CA_DATA).apply {
                                    putExtra("data", conversationAwarenessNotification.status)
                                })


                                if (conversationAwarenessNotification.status == 1.toByte() || conversationAwarenessNotification.status == 2.toByte()) {
                                    MediaController.startSpeaking()
                                } else if (conversationAwarenessNotification.status == 8.toByte() || conversationAwarenessNotification.status == 9.toByte()) {
                                    MediaController.stopSpeaking()
                                }

                                Log.d("AirPods Parser", "Conversation Awareness: ${conversationAwarenessNotification.status}")
                            }
                            else { }
                        }
                    }
                    Log.d("AirPods Service", "Socket closed")
                    isConnected = false
                    this@AirPodsService.stopForeground(STOP_FOREGROUND_REMOVE)
                    socket.close()
                    sendBroadcast(Intent(AirPodsNotifications.AIRPODS_DISCONNECTED))
                }
            }
        }
        catch (e: Exception) {
            e.printStackTrace()
            Log.d("AirPodsService", "Failed to connect to socket")
        }
    }


    fun sendPacket(packet: String) {
        val fromHex = packet.split(" ").map { it.toInt(16).toByte() }
        socket.outputStream?.write(fromHex.toByteArray())
        socket.outputStream?.flush()
    }

    fun setANCMode(mode: Int) {
        when (mode) {
            1 -> {
                socket.outputStream?.write(Enums.NOISE_CANCELLATION_OFF.value)
            }
            2 -> {
                socket.outputStream?.write(Enums.NOISE_CANCELLATION_ON.value)
            }
            3 -> {
                socket.outputStream?.write(Enums.NOISE_CANCELLATION_TRANSPARENCY.value)
            }
            4 -> {
                socket.outputStream?.write(Enums.NOISE_CANCELLATION_ADAPTIVE.value)
            }
        }
        socket.outputStream?.flush()
    }

    fun setCAEnabled(enabled: Boolean) {
        socket.outputStream?.write(if (enabled) Enums.SET_CONVERSATION_AWARENESS_ON.value else Enums.SET_CONVERSATION_AWARENESS_OFF.value)
        socket.outputStream?.flush()
    }

    fun setOffListeningMode(enabled: Boolean) {
        socket.outputStream?.write(byteArrayOf(0x04, 0x00 ,0x04, 0x00, 0x09, 0x00, 0x34, if (enabled) 0x01 else 0x02, 0x00, 0x00, 0x00))
        socket.outputStream?.flush()
    }

    fun setAdaptiveStrength(strength: Int) {
        val bytes = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x2E, strength.toByte(), 0x00, 0x00, 0x00)
        socket.outputStream?.write(bytes)
        socket.outputStream?.flush()
    }

    fun setPressSpeed(speed: Int) {
        val bytes = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x17, speed.toByte(), 0x00, 0x00, 0x00)
        socket.outputStream?.write(bytes)
        socket.outputStream?.flush()
    }

    fun setPressAndHoldDuration(speed: Int) {
        val bytes = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x18, speed.toByte(), 0x00, 0x00, 0x00)
        socket.outputStream?.write(bytes)
        socket.outputStream?.flush()
    }

    fun setNoiseCancellationWithOnePod(enabled: Boolean) {
        val bytes = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x1B, if (enabled) 0x01 else 0x02, 0x00, 0x00, 0x00)
        socket.outputStream?.write(bytes)
        socket.outputStream?.flush()
    }

    fun setVolumeControl(enabled: Boolean) {
        val bytes = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x25, if (enabled) 0x01 else 0x02, 0x00, 0x00, 0x00)
        socket.outputStream?.write(bytes)
        socket.outputStream?.flush()
    }

    fun setVolumeSwipeSpeed(speed: Int) {
        val bytes = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x23, speed.toByte(), 0x00, 0x00, 0x00)
        socket.outputStream?.write(bytes)
        socket.outputStream?.flush()
    }

    fun setToneVolume(volume: Int) {
        val bytes = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x1F, volume.toByte(), 0x50, 0x00, 0x00)
        socket.outputStream?.write(bytes)
        socket.outputStream?.flush()
    }

    val earDetectionNotification = AirPodsNotifications.EarDetection()
    val ancNotification = AirPodsNotifications.ANC()
    val batteryNotification = AirPodsNotifications.BatteryNotification()
    val conversationAwarenessNotification = AirPodsNotifications.ConversationalAwarenessNotification()

    var earDetectionEnabled = true

    fun setCaseChargingSounds(enabled: Boolean) {
        val bytes = byteArrayOf(0x12, 0x3a, 0x00, 0x01, 0x00, 0x08, if (enabled) 0x00 else 0x01)
        socket.outputStream?.write(bytes)
        socket.outputStream?.flush()
    }

    fun setEarDetection(enabled: Boolean) {
        earDetectionEnabled = enabled
    }

    fun getBattery(): List<Battery> {
        return batteryNotification.getBattery()
    }

    fun getANC(): Int {
        return ancNotification.status
    }

    private fun createNotification(): Notification {
        val channelId = "battery"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.pro_2_buds)
            .setContentTitle("AirPods Connected")
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val channel =
            NotificationChannel(channelId, "Battery Notification", NotificationManager.IMPORTANCE_LOW)

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
        return notificationBuilder.build()
    }

    fun disconnectAudio(context: Context, device: BluetoothDevice?) {
        val bluetoothAdapter = context.getSystemService(BluetoothManager::class.java).adapter

        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.A2DP) {
                    try {
                        val method = proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
                        method.invoke(proxy, device)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
                    }
                }
            }

            override fun onServiceDisconnected(profile: Int) { }
        }, BluetoothProfile.A2DP)

        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HEADSET) {
                    try {
                        val method = proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
                        method.invoke(proxy, device)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy)
                    }
                }
            }

            override fun onServiceDisconnected(profile: Int) { }
        }, BluetoothProfile.HEADSET)
    }

    fun connectAudio(context: Context, device: BluetoothDevice?) {
        val bluetoothAdapter = context.getSystemService(BluetoothManager::class.java).adapter

        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.A2DP) {
                    try {
                        val method = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                        method.invoke(proxy, device)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
                    }
                }
            }

            override fun onServiceDisconnected(profile: Int) { }
        }, BluetoothProfile.A2DP)

        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HEADSET) {
                    try {
                        val method = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                        method.invoke(proxy, device)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy)
                    }
                }
            }

            override fun onServiceDisconnected(profile: Int) { }
        }, BluetoothProfile.HEADSET)
    }

    fun setName(name: String) {
        val nameBytes = name.toByteArray()
        val bytes = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x1a, 0x00, 0x01,
            nameBytes.size.toByte(), 0x00) + nameBytes
        socket.outputStream?.write(bytes)
        socket.outputStream?.flush()
        val hex = bytes.joinToString(" ") { "%02X".format(it) }
        Log.d("AirPodsService", "setName: $name, sent packet: $hex")
    }

    fun setPVEnabled(enabled: Boolean) {
        var hex = "04 00 04 00 09 00 26 ${if (enabled) "01" else "02"} 00 00 00"
        var bytes = hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()
        socket.outputStream?.write(bytes)
        socket.outputStream?.flush()
        hex = "04 00 04 00 17 00 00 00 10 00 12 00 08 E${if (enabled) "6" else "5"} 05 10 02 42 0B 08 50 10 02 1A 05 02 ${if (enabled) "32" else "00"} 00 00 00"
        bytes = hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()
        socket.outputStream?.write(bytes)
        socket.outputStream?.flush()
    }

    fun setLoudSoundReduction(enabled: Boolean) {
        val hex = "52 1B 00 0${if (enabled) "1" else "0"}"
        val bytes = hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()
        socket.outputStream?.write(bytes)
        socket.outputStream?.flush()
    }
}