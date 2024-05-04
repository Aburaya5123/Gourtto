package jp.gourtto.layouts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import jp.gourtto.R
import jp.gourtto.gourmetapi.Shop


/**
 * 店舗一覧を表示するSearchResultsFragmentのRecyclerViewのAdapter
 * [ShopListItemHolder]のインスタンスを作成し、[Shop]データと関連付ける
 * GlideのcontextにはHolderViewを指定
 */
class ShopListRecyclerViewAdapter(
    private val searchResult: List<Shop>) :
    RecyclerView.Adapter<ShopListItemHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShopListItemHolder {
        val inflate: View = LayoutInflater.from(parent.context)
                .inflate(R.layout.recycler_item, parent, false)
        return ShopListItemHolder(inflate)
    }

    override fun onBindViewHolder(holder: ShopListItemHolder, position: Int) {
        holder.shopCatchText.text = searchResult[position].catch
            ?.takeIf { it.isNotEmpty() } ?: searchResult[position].shopDetailMemo
            ?.takeIf { it.isNotEmpty() } ?: searchResult[position].otherMemo
            ?.takeIf { it.isNotEmpty() } ?: searchResult[position].genre?.catch
            ?.takeIf { it.isNotEmpty() } ?: searchResult[position].genre?.name
            ?.takeIf { it.isNotEmpty() } ?: "情報なし"

        holder.shopNameText.text = searchResult[position].name
            ?.takeIf { it.isNotEmpty() } ?: "情報なし"

        holder.shopAccessText.text = searchResult[position].mobileAccess
            ?.takeIf { it.isNotEmpty() } ?: searchResult[position].access
            ?.takeIf { it.isNotEmpty() } ?: searchResult[position].address
            ?.takeIf { it.isNotEmpty() } ?: "情報なし"

        holder.shopBudgetText.text = searchResult[position].budget?.average
            ?.takeIf { it.isNotEmpty() } ?: searchResult[position].budget?.name
            ?.takeIf { it.isNotEmpty() } ?: "情報なし"

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