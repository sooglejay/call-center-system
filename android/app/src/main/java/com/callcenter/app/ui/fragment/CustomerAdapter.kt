package com.callcenter.app.ui.fragment

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.callcenter.app.R
import com.callcenter.app.data.model.Customer

/**
 * 客户列表适配器
 * 使用传统的 RecyclerView.Adapter 方式
 */
class CustomerAdapter(
    private val onItemClick: (Customer) -> Unit,
    private val onCallClick: (Customer) -> Unit
) : RecyclerView.Adapter<CustomerAdapter.ViewHolder>() {

    private var items: List<Customer> = emptyList()

    /**
     * 提交新数据
     */
    fun submitList(newItems: List<Customer>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_customer, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val customer = items[position]
        holder.bind(customer, onItemClick, onCallClick)
    }

    override fun getItemCount(): Int = items.size

    /**
     * ViewHolder 类
     */
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvPhone: TextView = itemView.findViewById(R.id.tvPhone)
        private val tvCompany: TextView = itemView.findViewById(R.id.tvCompany)
        private val tvLastContact: TextView = itemView.findViewById(R.id.tvLastContact)
        private val btnCall: Button = itemView.findViewById(R.id.btnCall)
        private val btnDetail: Button = itemView.findViewById(R.id.btnDetail)

        fun bind(
            customer: Customer,
            onItemClick: (Customer) -> Unit,
            onCallClick: (Customer) -> Unit
        ) {
            // 设置姓名
            tvName.text = customer.name

            // 设置状态
            tvStatus.text = getStatusText(customer.status)
            setStatusBackground(tvStatus, customer.status)

            // 设置电话
            tvPhone.text = customer.phone

            // 设置公司
            tvCompany.text = customer.company ?: ""

            // 设置最后联系时间
            tvLastContact.text = customer.lastContactAt ?: "未联系"

            // 设置按钮点击事件
            btnCall.setOnClickListener {
                onCallClick(customer)
            }

            btnDetail.setOnClickListener {
                onItemClick(customer)
            }

            // 整个卡片点击
            itemView.setOnClickListener {
                onItemClick(customer)
            }
        }

        /**
         * 获取状态文本
         */
        private fun getStatusText(status: String?): String {
            return when (status) {
                "pending" -> "待跟进"
                "contacted" -> "已联系"
                "completed" -> "已成交"
                "failed" -> "已失败"
                else -> "未知"
            }
        }

        /**
         * 设置状态背景颜色
         */
        private fun setStatusBackground(textView: TextView, status: String?) {
            val color = when (status) {
                "pending" -> Color.parseColor("#FF9800")   // 橙色
                "contacted" -> Color.parseColor("#2196F3") // 蓝色
                "completed" -> Color.parseColor("#4CAF50") // 绿色
                "failed" -> Color.parseColor("#F44336")    // 红色
                else -> Color.parseColor("#9E9E9E")        // 灰色
            }

            // 创建圆角背景
            val drawable = GradientDrawable()
            drawable.cornerRadius = 4f.dpToPx(textView.context)
            drawable.setColor(color)
            textView.background = drawable
        }

        /**
         * dp 转 px 扩展函数
         */
        private fun Float.dpToPx(context: android.content.Context): Float {
            return this * context.resources.displayMetrics.density
        }
    }
}
