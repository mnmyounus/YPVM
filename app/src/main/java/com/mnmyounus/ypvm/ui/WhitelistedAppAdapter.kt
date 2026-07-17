package com.mnmyounus.ypvm.ui

import android.content.pm.ApplicationInfo
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mnmyounus.ypvm.databinding.ItemGuestAppBinding

class WhitelistedAppAdapter(
    private val apps: List<ApplicationInfo>,
    private val onClick: (ApplicationInfo) -> Unit
) : RecyclerView.Adapter<WhitelistedAppAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemGuestAppBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGuestAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        val pm = holder.itemView.context.packageManager
        holder.binding.appIcon.setImageDrawable(app.loadIcon(pm))
        holder.binding.appLabel.text = app.loadLabel(pm)
        holder.itemView.setOnClickListener { onClick(app) }
    }

    override fun getItemCount() = apps.size
}
