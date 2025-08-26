package com.example.nova.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.nova.R
import com.example.nova.net.ChatMessage

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.Holder>() {
    private val items = mutableListOf<ChatMessage>()

    override fun getItemViewType(position: Int): Int {
        return if (items[position].role.equals("user", true)) 0 else 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val layout = if (viewType == 0) R.layout.item_message_user else R.layout.item_message_bot
        val v = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    fun append(msg: ChatMessage) {
        items.add(msg)
        notifyItemInserted(items.size - 1)
    }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        private val tv: TextView = v.findViewById(R.id.msgText)
        fun bind(m: ChatMessage) { tv.text = m.content }
    }
}
