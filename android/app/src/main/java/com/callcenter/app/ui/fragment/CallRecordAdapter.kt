package com.callcenter.app.ui.fragment

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.callcenter.app.R
import com.callcenter.app.data.model.CallRecord

/**
 * 通话记录适配器
 */
class CallRecordAdapter : RecyclerView.Adapter<CallRecordAdapter.ViewHolder>() {

    private var items: List<CallRecord> = emptyList()

    /**
     * 提交新数据
     */
    fun submitList(newItems: List<CallRecord>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_call_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    /**
     * ViewHolder 类
     */
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvPhone: TextView = itemView.findViewById(R.id.tvPhone)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvNote: TextView = itemView.findViewById(R.id.tvNote)

        fun bind(record: CallRecord) {
            // 设置姓名
            tvName.text = record.customerName

            // 设置状态
            tvStatus.text = getStatusText(record.status)
            setStatusBackground(tvStatus, record.status)

            // 设置电话
            tvPhone.text = record.phoneNumber

            // 设置通话时长
            tvDuration.text = formatDuration(record.duration)

            // 设置通话时间
            tvTime.text = record.calledAt

            // 设置备注
            tvNote.text = record.note ?: ""
        }

        /**
         * 获取状态文本
         */
        private fun getStatusText(status: String?): String {
            return when (status) {
                "connected" -> "已接通"
                "no_answer" -> "未接听"
                "busy" -> "忙线"
                "failed" -> "失败"
                else -> "未知"
            }
        }

        /**
         * 设置状态背景颜色
         */
        private fun setStatusBackground(textView: TextView, status: String?) {
            val color = when (status) {
                "connected" -> Color.parseColor("#4CAF50") // 绿色
                "no_answer" -> Color.parseColor("#FF9800") // 橙色
                "busy" -> Color.parseColor("#2196F3")      // 蓝色
                "failed" -> Color.parseColor("#F44336")    // 红色
                else -> Color.parseColor("#9E9E9E")        // 灰色
            }

            val drawable = GradientDrawable()
            drawable.cornerRadius = 4f.dpToPx(textView.context)
            drawable.setColor(color)
            textView.background = drawable
        }

        /**
         * 格式化通话时长
         */
        private fun formatDuration(seconds: Int?): String {
            if (seconds == null || seconds <= 0) {
                return "0秒"
            }

            val minutes = seconds / 60
            val remainingSeconds = seconds % 60

            return when {
                minutes > 0 -> "${minutes}分${remainingSeconds}秒"
                else -> "${remainingSeconds}秒"
            }
        }

        /**
         * dp 转 px 扩展函数
         */
        private fun Float.dpToPx(context: android.content.Context): Float {
            return this * context.resources.displayMetrics.density
        }
    }
}
