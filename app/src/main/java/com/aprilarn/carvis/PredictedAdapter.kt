package com.aprilarn.carvis

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aprilarn.carvis.databinding.ItemPredictedBinding

class PredictedAdapter(var items: List<String>) :
    RecyclerView.Adapter<PredictedAdapter.PredictedViewHolder>() {

    inner class PredictedViewHolder(
        private val binding: ItemPredictedBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(text: String) {
            binding.predictedText.text = text
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PredictedViewHolder {
        val binding = ItemPredictedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PredictedViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PredictedViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<String>) {
        items = newItems
        notifyDataSetChanged()
    }
}
