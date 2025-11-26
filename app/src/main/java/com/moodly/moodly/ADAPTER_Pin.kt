package com.moodly.moodly

import android.content.Intent
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

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
        val context = holder.itemView.context

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val spanCount = if (isLandscape) 3 else 2

        val columnWidth = screenWidth / spanCount

        // Height = Width * AspectRatio (i.e H/W)
        val ratio = if (pin.aspectRatio > 0) pin.aspectRatio else 1.0f
        val targetHeight = (columnWidth * ratio).toInt()

        // Apply Height
        holder.postImage.layoutParams.height = targetHeight
        holder.postImage.requestLayout()

        Glide.with(context)
            .load(pin.imageUrl)
            .transition(DrawableTransitionOptions.withCrossFade())
            .placeholder(R.color.black)
            .error(R.color.black)
            .into(holder.postImage)

        holder.itemView.setOnClickListener {
            val intent = Intent(context, PinDetails::class.java)
            intent.putExtra("pin_id", pin.pinId)
            context.startActivity(intent)
        }
    }
    override fun getItemCount() = pins.size
}