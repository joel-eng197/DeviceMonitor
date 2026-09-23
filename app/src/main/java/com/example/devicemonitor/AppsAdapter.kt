package com.example.devicemonitor

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class AppsAdapter(
    private var items: List<AppUsageInfo>,
    private val onSuspend: (AppUsageInfo) -> Unit,
    private val onOpenSettings: (AppUsageInfo) -> Unit
) : RecyclerView.Adapter<AppsAdapter.VH>() {

    class VH(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.imgIcon)
        val name: TextView = itemView.findViewById(R.id.txtAppName)
        val usage: TextView = itemView.findViewById(R.id.txtAppUsage)
        val btnSuspend: android.widget.Button = itemView.findViewById(R.id.btnSuspend)
        val btnSettings: android.widget.Button = itemView.findViewById(R.id.btnOpenSettings)
    }

    fun updateItems(newItems: List<AppUsageInfo>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = items[position]
        holder.icon.setImageDrawable(app.icon)
        holder.name.text = app.label
        holder.usage.text = formatBytes(app.dataBytes) + " sur 24 h"
        holder.btnSuspend.setOnClickListener { onSuspend(app) }
        holder.btnSettings.setOnClickListener { onOpenSettings(app) }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 Mo"
        val mb = bytes / 1048576.0
        return if (mb < 1000) String.format(Locale.FRANCE, "%.1f Mo", mb)
        else String.format(Locale.FRANCE, "%.2f Go", mb / 1024.0)
    }
}
