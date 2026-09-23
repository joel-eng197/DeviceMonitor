package com.example.devicemonitor

import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.StatFs
import android.provider.Settings
import android.widget.Button
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var screenEtat: android.view.View
    private lateinit var screenApps: android.view.View
    private lateinit var screenReglages: android.view.View
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var recyclerApps: RecyclerView
    private lateinit var swipeApps: SwipeRefreshLayout
    private lateinit var usageWarning: TextView
    private lateinit var adapter: AppsAdapter

    private lateinit var prefs: SharedPreferences
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var autoRefresh = true

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (screenEtat.visibility == android.view.View.VISIBLE) loadDeviceInfo()
            if (autoRefresh) handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        screenEtat = findViewById(R.id.screenEtat)
        screenApps = findViewById(R.id.screenApps)
        screenReglages = findViewById(R.id.screenReglages)
        bottomNav = findViewById(R.id.bottomNav)
        recyclerApps = findViewById(R.id.recyclerApps)
        swipeApps = findViewById(R.id.swipeApps)
        usageWarning = findViewById(R.id.txtUsageAccessWarning)

        recyclerApps.layoutManager = LinearLayoutManager(this)
        adapter = AppsAdapter(emptyList(), ::onSuspendApp, ::onOpenAppSettings)
        recyclerApps.adapter = adapter

        bottomNav.setOnItemSelectedListener { item ->
            screenEtat.visibility = android.view.View.GONE
            screenApps.visibility = android.view.View.GONE
            screenReglages.visibility = android.view.View.GONE
            when (item.itemId) {
                R.id.tab_etat -> { screenEtat.visibility = android.view.View.VISIBLE; loadDeviceInfo() }
                R.id.tab_apps -> { screenApps.visibility = android.view.View.VISIBLE; loadBackgroundApps() }
                R.id.tab_reglages -> screenReglages.visibility = android.view.View.VISIBLE
            }
            true
        }

        swipeApps.setOnRefreshListener { loadBackgroundApps() }
        usageWarning.setOnClickListener { openUsageAccessSettings() }

        setupSettingsScreen()
        loadDeviceInfo()
        handler.postDelayed(refreshRunnable, 5000)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun loadDeviceInfo() {
        val battStatus = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = battStatus.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = battStatus.isCharging
        findViewById<TextView>(R.id.txtBattery).text =
            "Batterie : $pct%  ${if (charging) "(en charge)" else "(sur batterie)"}"
        if (pct < 20 && !charging && prefs.getBoolean("battAlert", true)) {
            Toast.makeText(this, "Batterie faible ($pct%)", Toast.LENGTH_SHORT).show()
        }

        val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalGb = memInfo.totalMem / 1073741824.0
        val availGb = memInfo.availMem / 1073741824.0
        findViewById<TextView>(R.id.txtRam).text =
            String.format(Locale.FRANCE, "RAM : %.1f Go dispo / %.1f Go", availGb, totalGb)

        val stat = StatFs(filesDir.absolutePath)
        val totalStorage = stat.totalBytes / 1073741824.0
        val availStorage = stat.availableBytes / 1073741824.0
        findViewById<TextView>(R.id.txtStorage).text =
            String.format(Locale.FRANCE, "Stockage : %.1f Go libres / %.1f Go", availStorage, totalStorage)

        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val netCaps = cm.getNetworkCapabilities(cm.activeNetwork)
        val netLabel = when {
            netCaps == null -> "déconnecté"
            netCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            netCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Données mobiles"
            netCaps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "inconnu"
        }
        findViewById<TextView>(R.id.txtNetwork).text = "Réseau : $netLabel"
        if (netLabel == "Données mobiles" && prefs.getBoolean("dataAlert", false)) {
            Toast.makeText(this, "Connexion hors Wi-Fi", Toast.LENGTH_SHORT).show()
        }

        findViewById<TextView>(R.id.txtCpu).text =
            "Cœurs CPU : ${Runtime.getRuntime().availableProcessors()}"
        findViewById<TextView>(R.id.txtAndroidVersion).text =
            "Android : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        findViewById<TextView>(R.id.txtModel).text =
            "Modèle : ${Build.MANUFACTURER} ${Build.MODEL}"
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun openUsageAccessSettings() {
        Toast.makeText(this, "Active « Moniteur Appareil » dans la liste, puis reviens", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun loadBackgroundApps() {
        swipeApps.isRefreshing = false
        val granted = hasUsageAccess()
        usageWarning.visibility = if (granted) android.view.View.GONE else android.view.View.VISIBLE
        if (!granted) {
            adapter.updateItems(emptyList())
            return
        }
        adapter.updateItems(queryTopDataConsumers())
    }

    private fun queryTopDataConsumers(): List<AppUsageInfo> {
        val nsm = getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val pm = packageManager
        val now = System.currentTimeMillis()
        val since = now - 24L * 60 * 60 * 1000

        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .distinctBy { it.uid }

        val results = mutableListOf<AppUsageInfo>()
        for (appInfo in installed) {
            var totalBytes = 0L
            for (networkType in intArrayOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
                try {
                    val buckets = nsm.queryDetailsForUid(networkType, null, since, now, appInfo.uid)
                    val bucket = android.app.usage.NetworkStats.Bucket()
                    while (buckets.hasNextBucket()) {
                        buckets.getNextBucket(bucket)
                        totalBytes += bucket.rxBytes + bucket.txBytes
                    }
                    buckets.close()
                } catch (e: SecurityException) {
                } catch (e: Exception) {
                }
            }
            if (totalBytes > 0) {
                results.add(
                    AppUsageInfo(
                        packageName = appInfo.packageName,
                        label = pm.getApplicationLabel(appInfo).toString(),
                        icon = try { pm.getApplicationIcon(appInfo) } catch (e: Exception) { null },
                        dataBytes = totalBytes
                    )
                )
            }
        }
        return results.sortedByDescending { it.dataBytes }.take(25)
    }

    private fun onSuspendApp(app: AppUsageInfo) {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        am.killBackgroundProcesses(app.packageName)
        Toast.makeText(
            this,
            "${app.label} : processus en cache libérés (n'arrête pas un service actif)",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun onOpenAppSettings(app: AppUsageInfo) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${app.packageName}")
        }
        startActivity(intent)
    }

    private fun setupSettingsScreen() {
        val swAuto = findViewById<Switch>(R.id.switchAutoRefresh)
        val swBatt = findViewById<Switch>(R.id.switchBattAlert)
        val swData = findViewById<Switch>(R.id.switchDataAlert)
        val btnUsage = findViewById<Button>(R.id.btnUsageAccess)
        val btnReset = findViewById<Button>(R.id.btnReset)

        swAuto.isChecked = prefs.getBoolean("autoRefresh", true)
        swBatt.isChecked = prefs.getBoolean("battAlert", true)
        swData.isChecked = prefs.getBoolean("dataAlert", false)
        autoRefresh = swAuto.isChecked

        swAuto.setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
            prefs.edit().putBoolean("autoRefresh", checked).apply()
            autoRefresh = checked
            if (checked) handler.postDelayed(refreshRunnable, 5000)
        }
        swBatt.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("battAlert", checked).apply()
        }
        swData.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("dataAlert", checked).apply()
        }
        btnUsage.setOnClickListener { openUsageAccessSettings() }
        btnReset.setOnClickListener {
            prefs.edit().clear().apply()
            swAuto.isChecked = true
            swBatt.isChecked = true
            swData.isChecked = false
            autoRefresh = true
            Toast.makeText(this, "Réglages réinitialisés", Toast.LENGTH_SHORT).show()
        }
    }
}
