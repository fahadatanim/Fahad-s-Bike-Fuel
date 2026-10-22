package com.fahad.bikemeter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import kotlin.math.max

class TrackingService : Service(), LocationListener {
    private lateinit var lm: LocationManager
    private var last: Location? = null
    private var tripKm = 0.0
    private var lastSavedTime = 0L
    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createChannel()
        startForeground(7, notification("GPS trip tracking is active"))
        lm = getSystemService(LOCATION_SERVICE) as LocationManager
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000L, 5f, this, Looper.getMainLooper())
        } catch (_: SecurityException) {
            stopSelf()
        } catch (_: Exception) {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onLocationChanged(location: Location) {
        val previous = last
        if (previous != null) {
            val distance = previous.distanceTo(location).toDouble() / 1000.0
            val elapsed = location.time - previous.time
            // Filter implausible jumps and tiny GPS jitter.
            if (distance in 0.008..1.0 && elapsed in 1000..120000 && location.accuracy <= 45f) {
                tripKm += distance
                if (tripKm >= 0.1 && System.currentTimeMillis() - lastSavedTime >= 30000L) {
                    MainActivity.recordGpsTrip(this, tripKm)
                    tripKm = 0.0
                    lastSavedTime = System.currentTimeMillis()
                }
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(7, notification("GPS distance saved in 0.1 km blocks"))
            }
        }
        last = location
    }

    override fun onDestroy() {
        try {
            if (::lm.isInitialized) lm.removeUpdates(this)
        } catch (_: Exception) {}
        // Save final remainder when the tracking session ends.
        if (tripKm >= 0.03) MainActivity.recordGpsTrip(this, tripKm)
        isRunning = false
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel("bike_tracking", "Bike GPS tracking", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }
    private fun notification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "bike_tracking") else Notification.Builder(this)
        return builder.setContentTitle("Fahad's Bike Meter & Fuel").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation).setOngoing(true).build()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Deprecated in Android API") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    companion object { @Volatile var isRunning: Boolean = false }
}
