package com.moodly.moodly

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ADAPTER_Board(private val boards: List<DATA_Board>,
                    private val onBoardLongPressed: (DATA_Board) -> Unit) :
    RecyclerView.Adapter<ADAPTER_Board.BoardViewHolder>() {

    inner class BoardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name = itemView.findViewById<TextView>(R.id.tv_board_name)
        val coverImg = itemView.findViewById<ImageView>(R.id.img_board)
        val count = itemView.findViewById<TextView>(R.id.tv_pin_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BoardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_board, parent, false)
        return BoardViewHolder(view)
    }

    override fun onBindViewHolder(holder: BoardViewHolder, position: Int) {
        val board = boards[position]
        holder.name.text = board.title
        holder.count.text = board.pinCount.toString()
        if(board.coverImageUrl.isNotEmpty())
        {
            Glide.with(holder.itemView.context)
                .load(board.coverImageUrl)
                .centerCrop()
                .placeholder(R.color.black)
                .error(R.drawable.empty_placeholder)
                .into(holder.coverImg)
        }
        else
        {
            holder.coverImg.setImageResource(R.drawable.empty_placeholder)
        }
        // Go to board details on click
        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, BoardDetails::class.java)
            context.startActivity(intent)
        }

        // Handle long press for deletion
        holder.itemView.setOnLongClickListener {
            onBoardLongPressed(board)
            true
        }
    }

    override fun getItemCount() = boards.size
}
