package com.moodly.moodly

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ADAPTER_Board(private val boards: List<DATA_Board>) :
    RecyclerView.Adapter<ADAPTER_Board.BoardViewHolder>() {

    inner class BoardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name = itemView.findViewById<TextView>(R.id.tv_board_name)
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
    }

    override fun getItemCount() = boards.size
}
