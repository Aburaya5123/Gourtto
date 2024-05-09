package jp.gourtto.layouts

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.maps.model.LatLng
import jp.gourtto.R
import jp.gourtto.fragments.SearchScreenFragment
import jp.gourtto.gourmet_api.Shop


/**
 * 検索結果画面のRecyclerViewで、生成された子オブジェクトがクリックされた際のリスナー
 */
interface RecyclerClickListener{
    // オブジェクト全体をクリック
    fun onRecyclerObjectClicked(shopId: String)
    // マップアイコンをクリック
    fun onMapButtonClicked(instance: Shop)
}

/**
 * 検索結果画面のRecyclerViewのAdapter
 * GlideのcontextにはHolderViewを指定
 */
class ShopListRecyclerViewAdapter(
    private val searchResult: List<Shop>,
    private val clickListener: RecyclerClickListener) :
    RecyclerView.Adapter<ShopListItemHolder>() {

        companion object{
            private val TAG = ShopListRecyclerViewAdapter::class.java.simpleName
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShopListItemHolder {
        val inflate: View = LayoutInflater.from(parent.context)
                .inflate(R.layout.recycler_item, parent, false)
        return ShopListItemHolder(inflate)
    }

    override fun onBindViewHolder(holder: ShopListItemHolder, position: Int) {
        // Object全体をカバーするonClickListener, 該当店舗のShopIdを返す
        holder.recyclerObject.setOnClickListener{
            searchResult[position].id?.let { clickListener.onRecyclerObjectClicked(it) }
        }
        // MapButton
        holder.openMapButton.setOnClickListener{
            clickListener.onMapButtonClicked(searchResult[position])
        }
        // キャッチ
        holder.shopCatchText.text = searchResult[position].catch
            ?.takeIf { it.isNotEmpty() } ?: searchResult[position].shopDetailMemo
            ?.takeIf { it.isNotEmpty() } ?: searchResult[position].otherMemo
            ?.takeIf { it.isNotEmpty() } ?: searchResult[position].genre?.catch
            ?.takeIf { it.isNotEmpty() } ?: searchResult[position].genre?.name
            ?.takeIf { it.isNotEmpty() } ?: "情報なし"
        // 店名
        holder.shopNameText.text = searchResult[position].name
            ?.takeIf { it.isNotEmpty() } ?: "情報なし"
        // アクセス
        holder.shopAccessText.text = searchResult[position].mobileAccess
            ?.takeIf { it.isNotEmpty() } ?: searchResult[position].access
            ?.takeIf { it.isNotEmpty() } ?: searchResult[position].address
            ?.takeIf { it.isNotEmpty() } ?: "情報なし"
        // 予算
        holder.shopBudgetText.text = searchResult[position].budget?.average
            ?.takeIf { it.isNotEmpty() } ?: searchResult[position].budget?.name
            ?.takeIf { it.isNotEmpty() } ?: "情報なし"
        // 営業時間
        holder.shopOpenText.text = searchResult[position].open
            ?.takeIf { it.isNotEmpty() } ?: "情報なし"

        // URLがnull,空文字列でない場合に限り、GlideでImageをダウンロードし、ImageViewに挿入
        searchResult[position].photo?.mobile?.l?.takeIf { it.isNotEmpty() }
            ?.let {
                Glide.with(holder.itemView.context).load(it).into(holder.shopMainImage)
            }
    }

    override fun getItemCount(): Int {
        return searchResult.size
    }
}