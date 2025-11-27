package com.moodly.moodly

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ADAPTER_BoardSelection(
    private val boards: List<DATA_Board>,
    private val onBoardClicked: (DATA_Board) -> Unit
) : RecyclerView.Adapter<ADAPTER_BoardSelection.BoardViewHolder>() {

    inner class BoardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val coverImage: ImageView = itemView.findViewById(R.id.iv_board_cover)
        val nameText: TextView = itemView.findViewById(R.id.tv_board_name)
        val countText: TextView = itemView.findViewById(R.id.tv_pin_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BoardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_board_selection, parent, false)
        return BoardViewHolder(view)
    }

    override fun onBindViewHolder(holder: BoardViewHolder, position: Int) {
        val board = boards[position]
        holder.nameText.text = board.title
        holder.countText.text = "${board.pinCount} Pins"

        if (board.coverImageUrl.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(board.coverImageUrl)
                .centerCrop()
                .error(R.color.black)
                .placeholder(R.color.black)
                .into(holder.coverImage)
        } else {
            holder.coverImage.setImageResource(R.drawable.empty_placeholder)
        }

        holder.itemView.setOnClickListener {
            onBoardClicked(board)
        }
    }

    override fun getItemCount() = boards.size
}