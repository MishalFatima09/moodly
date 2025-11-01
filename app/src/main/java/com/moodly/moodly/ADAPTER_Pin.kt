package com.moodly.moodly

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class ADAPTER_Pin(private val pins: List<DATA_Pin>) :
    RecyclerView.Adapter<ADAPTER_Pin.PinViewHolder>() {

    inner class PinViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val postImage: ImageView = itemView.findViewById(R.id.post_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PinViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pin, parent, false)
        return PinViewHolder(view)
    }

    override fun onBindViewHolder(holder: PinViewHolder, position: Int) {
        val pin = pins[position]
        holder.postImage.setImageResource(pin.imageResource)
    }

    override fun getItemCount() = pins.size
}
