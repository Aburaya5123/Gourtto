package jp.gourtto.layouts

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import jp.gourtto.R


/**
 * 検索結果画面のRecyclerViewの各行のアイテム参照を保持するクラス
 */
class ShopListItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    val recyclerObject: View
    val shopCatchText: TextView
    val shopNameText: TextView
    val shopAccessText: TextView
    val shopBudgetText: TextView
    val shopOpenText: TextView
    val shopMainImage: ImageView

    init {
        recyclerObject = itemView.findViewById(R.id.recycler_object)
        shopCatchText = itemView.findViewById(R.id.shop_catch)
        shopNameText = itemView.findViewById(R.id.shop_name)
        shopAccessText = itemView.findViewById(R.id.shop_access)
        shopBudgetText = itemView.findViewById(R.id.shop_budget)
        shopOpenText = itemView.findViewById(R.id.shop_open)
        shopMainImage = itemView.findViewById(R.id.shop_main_image1)
    }
}