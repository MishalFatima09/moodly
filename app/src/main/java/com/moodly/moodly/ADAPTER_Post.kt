package com.moodly.moodly

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager


class PostAdapter(private val data: List<DATA_Post>) :
        RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

        private fun getColor(position: Int): String {
            return when (position % 5) {
                0 -> "#FF6B6B" // Light Red
                1 -> "#4ECDC4" // Teal
                2 -> "#45B7D1" // Blue
                3 -> "#F7F671" // Yellow
                4 -> "#A178D1" // Purple
                else -> "#CCCCCC"
            }
        }

        inner class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val postImage: ImageView = itemView.findViewById(R.id.post_image)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
            // Inflate the item_post.xml layout
            val view = LayoutInflater.from(parent.context).inflate(R.layout.pin_item, parent, false)
            return PostViewHolder(view)
        }

        override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
            val item = data[position]

            val density = holder.itemView.context.resources.displayMetrics.density
            val baseHeightDp = 200
            val baseHeightPx = (baseHeightDp * density).toInt()

            val actualHeight = (baseHeightPx * item.heightRatio).toInt()

            holder.postImage.layoutParams.height = actualHeight

            holder.postImage.setBackgroundColor(Color.parseColor(getColor(position)))

            val params = holder.itemView.layoutParams as StaggeredGridLayoutManager.LayoutParams
            params.isFullSpan = false // Ensure it stays in a column and doesn't span across both
            holder.itemView.layoutParams = params
        }

        override fun getItemCount(): Int = data.size
    }
