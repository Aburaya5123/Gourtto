package jp.gourtto.google_api

import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import jp.gourtto.R

/**
 * PlacesAutoCompleteのRecyclerViewのアイテム参照
 */
class PlacesAutoCompleteHolder(itemView: View): RecyclerView.ViewHolder(itemView){
    val primaryText: TextView
    val secondaryText: TextView
    val distance: TextView
    val row: ConstraintLayout

    init {
        primaryText = itemView.findViewById(R.id.place_address)
        secondaryText = itemView.findViewById(R.id.place_name)
        distance = itemView.findViewById(R.id.place_distance)
        row = itemView.findViewById(R.id.place_item_view)
        }
}