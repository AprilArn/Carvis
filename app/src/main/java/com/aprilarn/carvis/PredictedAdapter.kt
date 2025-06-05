package com.aprilarn.carvis

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PredictedAdapter(var items: List<String>) :
    RecyclerView.Adapter<PredictedAdapter.PredictedViewHolder>() {

    inner class PredictedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.predicted_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PredictedViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_predicted, parent, false)
        return PredictedViewHolder(view)
    }

    override fun onBindViewHolder(holder: PredictedViewHolder, position: Int) {
        holder.text.text = items[position]
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<String>) {
        items = newItems
        notifyDataSetChanged()
    }
}
