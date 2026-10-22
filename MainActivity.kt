package com.fahad.bikemeter

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import android.graphics.Typeface
import android.view.ViewGroup
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("bike_data", MODE_PRIVATE) }
    private lateinit var root: LinearLayout
    private lateinit var summary: TextView
    private lateinit var status: TextView
    private lateinit var odometerInput: EditText
    private lateinit var fuelInput: EditText
    private lateinit var moneyInput: EditText
    private lateinit var kmInput: EditText
    private lateinit var noteInput: EditText
    private lateinit var petrolPriceInput: EditText
    private val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        refresh()
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 11)
        }
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 24, 22, 30)
            setBackgroundColor(0xFFF5F7FA.toInt())
        }
        scroll.addView(root)
        setContentView(scroll)

        addText("Fahad's Bike Meter & Fuel", 25, true)
        addText("Hero Passion X Pro 110cc • Offline tracker", 14, false)
        summary = addText("", 16, true)
        status = addText("GPS tracking is stopped.", 14, false)

        addText("1. Set current odometer", 18, true)
        odometerInput = edit("Current total km (e.g. 12500)")
        addButton("Save odometer baseline") {
            val value = odometerInput.text.toString().toDoubleOrNull()
            if (value == null || value < 0) toast("Enter a valid odometer value.")
            else { prefs.edit().putFloat("odo", value.toFloat()).apply(); refresh(); toast("Odometer saved.") }
        }

        addText("2. Trip distance", 18, true)
        kmInput = edit("Distance travelled in km")
        noteInput = edit("Trip note (optional, e.g. Foodpanda)")
        addButton("Add manual trip") {
            val km = kmInput.text.toString().toDoubleOrNull()
            if (km == null || km <= 0) toast("Enter a distance greater than zero.")
            else {
                addTrip(km, "Manual", noteInput.text.toString())
                kmInput.setText(""); noteInput.setText(""); refresh(); toast("Trip added.")
            }
        }
        addButton("Start GPS tracking") { startGps() }
        addButton("Stop GPS tracking") { stopService(Intent(this, TrackingService::class.java)); status.text = "GPS tracking stopped." }

        addText("3. Fuel refill / starting fuel", 18, true)
        fuelInput = edit("Fuel amount in litres")
        moneyInput = edit("Money paid in taka (optional)")
        petrolPriceInput = edit("Price per litre in taka (default 160)")
        petrolPriceInput.setText(prefs.getFloat("price", 160f).toString())
        addButton("Save fuel / refill") {
            val litres = fuelInput.text.toString().toDoubleOrNull()
            val money = moneyInput.text.toString().toDoubleOrNull()
            val price = petrolPriceInput.text.toString().toDoubleOrNull() ?: 160.0
            if ((litres == null || litres <= 0) && (money == null || money <= 0)) {
                toast("Enter litres or money paid.")
            } else {
                val l = if (litres != null && litres > 0) litres else (money!! / max(price, 0.01))
                val cost = if (money != null && money > 0) money else l * price
                val current = prefs.getFloat("fuel", 0f).toDouble()
                prefs.edit().putFloat("fuel", (current + l).toFloat()).putFloat("price", price.toFloat()).apply()
                addFuelLog(l, cost)
                fuelInput.setText(""); moneyInput.setText(""); refresh()
                toast(String.format(Locale.getDefault(), "Added %.2f L to estimated fuel.", l))
            }
        }
        addButton("Record fuel used (litres)") {
            val used = fuelInput.text.toString().toDoubleOrNull()
            if (used == null || used <= 0) toast("Enter litres used in the fuel amount field.")
            else {
                val current = prefs.getFloat("fuel", 0f).toDouble()
                prefs.edit().putFloat("fuel", max(0.0, current - used).toFloat()).apply()
                fuelInput.setText(""); refresh(); toast("Fuel estimate updated.")
            }
        }

        addText("4. Recent records", 18, true)
        addButton("View / refresh summary") { refresh() }
        addButton("Reset all app data") {
            android.app.AlertDialog.Builder(this)
                .setTitle("Reset all data?")
                .setMessage("This permanently deletes odometer, trip and fuel records on this phone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset") { _, _ -> prefs.edit().clear().apply(); refresh(); toast("App data reset.") }
                .show()
        }
    }

    private fun addText(value: String, size: Int, bold: Boolean): TextView {
        val v = TextView(this).apply {
            text = value; textSize = size.toFloat(); setTextColor(0xFF172033.toInt())
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(0, 10, 0, 10)
        }
        root.addView(v, ViewGroup.LayoutParams(-1, -2))
        return v
    }

    private fun edit(hintText: String): EditText {
        val e = EditText(this).apply {
            hint = hintText; textSize = 15f; singleLine = true
            setPadding(14, 8, 14, 8)
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        val lp = LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 5, 0, 7)
        root.addView(e, lp)
        return e
    }

    private fun addButton(label: String, action: () -> Unit) {
        val b = Button(this).apply { text = label; setOnClickListener { action() } }
        val lp = LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 3, 0, 6)
        root.addView(b, lp)
    }

    private fun startGps() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 11)
            toast("Allow location permission, then tap Start GPS tracking again.")
            return
        }
        val i = Intent(this, TrackingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
        status.text = "GPS tracking requested. Keep location enabled; screen can be off."
    }

    private fun stopGpsFromService() { status.text = "GPS tracking stopped." }

    private fun addTrip(km: Double, source: String, note: String) {
        val arr = readArray("trips")
        arr.put(JSONObject().put("km", km).put("source", source).put("note", note).put("time", System.currentTimeMillis()))
        prefs.edit().putString("trips", arr.toString()).apply()
        val odo = prefs.getFloat("odo", 0f).toDouble()
        prefs.edit().putFloat("odo", (odo + km).toFloat()).apply()
        val fuel = prefs.getFloat("fuel", 0f).toDouble()
        val kmPerL = prefs.getFloat("kmpl", 40f).toDouble()
        if (kmPerL > 0 && fuel > 0) prefs.edit().putFloat("fuel", max(0.0, fuel - km / kmPerL).toFloat()).apply()
    }

    private fun addFuelLog(litres: Double, cost: Double) {
        val arr = readArray("fuel_logs")
        arr.put(JSONObject().put("litres", litres).put("cost", cost).put("time", System.currentTimeMillis()))
        prefs.edit().putString("fuel_logs", arr.toString()).apply()
    }

    private fun readArray(key: String): JSONArray = try { JSONArray(prefs.getString(key, "[]")) } catch (_: Exception) { JSONArray() }

    private fun refresh() {
        val trips = readArray("trips")
        var totalKm = 0.0
        val recent = mutableListOf<String>()
        for (i in 0 until trips.length()) {
            val o = trips.optJSONObject(i) ?: continue
            totalKm += o.optDouble("km", 0.0)
            if (i >= max(0, trips.length() - 5)) {
                recent.add("• ${"%.1f".format(o.optDouble("km"))} km — ${o.optString("source")} — ${fmt.format(Date(o.optLong("time")))}")
            }
        }
        val odo = prefs.getFloat("odo", 0f).toDouble()
        val fuel = prefs.getFloat("fuel", 0f).toDouble()
        val kmpl = prefs.getFloat("kmpl", 40f).toDouble()
        val costTotal = run {
            val a = readArray("fuel_logs"); var sum = 0.0
            for (i in 0 until a.length()) sum += a.optJSONObject(i)?.optDouble("cost", 0.0) ?: 0.0
            sum
        }
        summary.text = "TOTAL ODOMETER: ${"%.1f".format(odo)} km\n" +
                "TRIPS RECORDED: ${trips.length()} • DISTANCE LOGGED: ${"%.1f".format(totalKm)} km\n" +
                "EST. FUEL REMAINING: ${"%.2f".format(fuel)} L\n" +
                "EST. RANGE LEFT: ${"%.0f".format(fuel * kmpl)} km (at ${"%.1f".format(kmpl)} km/L)\n" +
                "RECORDED FUEL SPEND: ৳${"%.0f".format(costTotal)}\n\n" +
                "Note: fuel use is estimated using your set mileage. It is not measured by a bike sensor."
        status.text = (if (TrackingService.isRunning) "GPS TRACKING ACTIVE" else "GPS tracking stopped") +
                "\nRecent trips:\n" + (recent.reversed().joinToString("\n").ifBlank { "No trips recorded yet." })
    }

    override fun onResume() { super.onResume(); if (::root.isInitialized) refresh() }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    companion object {
        fun recordGpsTrip(context: android.content.Context, km: Double) {
            val p = context.getSharedPreferences("bike_data", MODE_PRIVATE)
            val arr = try { JSONArray(p.getString("trips", "[]")) } catch (_: Exception) { JSONArray() }
            arr.put(JSONObject().put("km", km).put("source", "GPS").put("note", "GPS tracked trip").put("time", System.currentTimeMillis()))
            val odo = p.getFloat("odo", 0f).toDouble()
            val fuel = p.getFloat("fuel", 0f).toDouble()
            val kmpl = p.getFloat("kmpl", 40f).toDouble()
            p.edit().putString("trips", arr.toString()).putFloat("odo", (odo + km).toFloat())
                .putFloat("fuel", max(0.0, fuel - km / max(1.0, kmpl)).toFloat()).apply()
        }
    }
}
