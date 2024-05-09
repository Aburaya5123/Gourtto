package jp.gourtto.layouts

import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import jp.gourtto.R


/**
 * 検索結果画面RecyclerViewの各行のアイテム参照
 */
class ShopListItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    val recyclerObject: View
    val shopCatchText: TextView
    val shopNameText: TextView
    val shopAccessText: TextView
    val shopBudgetText: TextView
    val shopOpenText: TextView
    val shopMainImage: ImageView
    val openMapButton: Button

    init {
        recyclerObject = itemView.findViewById(R.id.recycler_object)
        shopCatchText = itemView.findViewById(R.id.shop_catch)
        shopNameText = itemView.findViewById(R.id.shop_name)
        shopAccessText = itemView.findViewById(R.id.shop_access)
        shopBudgetText = itemView.findViewById(R.id.shop_budget)
        shopOpenText = itemView.findViewById(R.id.shop_open)
        shopMainImage = itemView.findViewById(R.id.shop_main_image1)
        openMapButton = itemView.findViewById(R.id.open_map)
    }
}