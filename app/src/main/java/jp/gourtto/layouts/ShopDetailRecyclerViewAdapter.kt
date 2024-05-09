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

    /**
     * RecyclerViewの要素となるデータを格納
     * [title] Viewの左側タイトル
     * [body] Viewの右側本文
     */
    data class ShopInfo(
        val title: String,
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
            ?.takeIf { it.isNotEmpty() } ?: "情報なし"),

        // 席数
        ShopInfo("総席数",
    instance.capacity
            ?.takeIf { it.isNotEmpty() }?: instance.partyCapacity
            ?.takeIf { it.isNotEmpty() }?: "情報なし"),

        // 駐車場
        ShopInfo("駐車場",
            instance.parking
                ?.takeIf { it.isNotEmpty() }?: "情報なし"),

        // コース
        ShopInfo("コース料理",
            instance.course
                ?.takeIf { it.isNotEmpty() }?: "情報なし"),

        // 飲み放題
        ShopInfo("飲み放題",
            instance.freeDrink
                ?.takeIf { it.isNotEmpty() }?: "情報なし"),

        // 食べ放題
        ShopInfo("食べ放題",
            instance.freeFood
                ?.takeIf { it.isNotEmpty() }?: "情報なし"),

        // 個室
        ShopInfo("個室",
            instance.privateRoom
                ?.takeIf { it.isNotEmpty() }?: "情報なし"),

        // クレカ
        ShopInfo("クレジットカード",
            instance.card
                ?.takeIf { it.isNotEmpty() }?: "情報なし"),

        // 禁煙席
        ShopInfo("禁煙席",
            instance.nonSmoking
                ?.takeIf { it.isNotEmpty() }?: "情報なし"),

        // バリアフリー
        ShopInfo("バリアフリー",
            instance.barrierFree
                ?.takeIf { it.isNotEmpty() }?: "情報なし"),

        // Margin
        ShopInfo("",""))
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
        holder.rowTitle.text = shopInfo[position].title
        holder.rowBody.text = shopInfo[position].body
    }

    override fun getItemCount(): Int {
        return shopInfo.size
    }
}