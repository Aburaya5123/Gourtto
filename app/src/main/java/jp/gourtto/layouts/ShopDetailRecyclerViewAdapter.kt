package jp.gourtto.layouts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import jp.gourtto.R
import jp.gourtto.gourmet_api.Shop


/**
 * 店舗詳細画面のRecyclerViewに表示する情報を作成
 * [Shop]インスタンスの各プロパティを List<name,value> に変換
 */
class ArrangeShopInfo(private val instance: Shop) {
    data class ShopInfo(
        val titile: String,
        val body: String?,
    )
    fun arrange(): List<ShopInfo> {
        return listOf(

        ShopInfo("おすすめ",
            instance.catch
            ?.takeIf { it.isNotEmpty() } ?: instance.shopDetailMemo
            ?.takeIf { it.isNotEmpty() } ?: instance.otherMemo
            ?.takeIf { it.isNotEmpty() } ?: instance.genre?.catch
            ?.takeIf { it.isNotEmpty() } ?: instance.genre?.name
            ?.takeIf { it.isNotEmpty() } ?: "情報なし"),

        ShopInfo("住所",
            instance.address
            ?.takeIf { it.isNotEmpty() } ?: "情報なし"),

        ShopInfo("アクセス",
            instance.access
            ?.takeIf { it.isNotEmpty() } ?: instance.mobileAccess
            ?.takeIf { it.isNotEmpty() } ?: "情報なし"),

        ShopInfo("料金",
            (instance.budget?.average
            ?.takeIf { it.isNotEmpty() } ?: instance.name
            ?.takeIf { it.isNotEmpty() } ?: "情報なし").apply {
                instance.budgetMemo?.takeIf { it.isNotEmpty() }.let{
                    this + "\n${it}"
                }
            }),

        ShopInfo("営業時間",
            instance.open
            ?.takeIf { it.isNotEmpty() } ?: "情報なし"),

        // 定休日
        ShopInfo("定休日",
            instance.close
            ?.takeIf { it.isNotEmpty() } ?: "情報なし")
        )

    }
}

/**
 * 店舗詳細画面のRecyclerViewのAdapter
 */
class ShopDetailRecyclerViewAdapter(
    private val shopInfo: List<ArrangeShopInfo.ShopInfo>) :
    RecyclerView.Adapter<ShopDetailItemHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShopDetailItemHolder {
        val inflate: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.recycler_item_detail, parent, false)
        return ShopDetailItemHolder(inflate)
    }

    override fun onBindViewHolder(holder: ShopDetailItemHolder, position: Int) {
        holder.rowTitle.text = shopInfo[position].titile
        holder.rowBody.text = shopInfo[position].body
    }

    override fun getItemCount(): Int {
        return shopInfo.size
    }
}