package jp.gourtto.layouts

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import jp.gourtto.R


/**
 * 店舗詳細画面のRecyclerViewの各行のアイテム参照を保持するクラス
 */
class ShopDetailItemHolder(itemView: View): RecyclerView.ViewHolder(itemView){

    val row: View
    val rowTitle: TextView
    val rowBody: TextView

    init {
        row = itemView.findViewById(R.id.shop_detail_row)
        rowTitle = itemView.findViewById(R.id.row_title_detail)
        rowBody = itemView.findViewById(R.id.row_body_detail)
    }
}