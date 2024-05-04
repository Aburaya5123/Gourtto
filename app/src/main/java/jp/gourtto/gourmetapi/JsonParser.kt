package jp.gourtto.gourmetapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


/**
 * ApiのレスポンスをパースするためのDataClass
 *   ジャンルマスタapi -> GourmetGenre
 *   グルメサーチapi -> Gourmet
 */

@Serializable
data class Gourmet(
    val results: GourmetBody
)

@Serializable
data class GourmetGenre(
    val results: GourmetGenreBody
)

@Serializable
data class GourmetGenreBody(
    @SerialName(value = "api_version")
    val apiVersion: String, // apiバージョン
    @SerialName(value = "results_available")
    val resultsAvailable: Int, // クエリ条件にマッチする、検索結果の全件数
    @SerialName(value = "results_returned")
    val resultsReturned: Int, // このjsonに含まれる検索結果の件数
    @SerialName(value = "results_start")
    val resultsStart: Int, // 検索結果の開始位置
    val genre: List<Genre>?
)

/**
 * ジャンル情報(ジャンルマスタapi)
 */
@Serializable
data class Genre(
    val code: String, // ジャンルコード
    val name: String // ジャンル名
)

@Serializable
data class GourmetBody(
    @SerialName(value = "api_version")
    val apiVersion: String, // apiバージョン
    @SerialName(value = "results_available")
    val resultsAvailable: Int, // クエリ条件にマッチする、検索結果の全件数
    @SerialName(value = "results_returned")
    val resultsReturned: Int, // このjsonに含まれる検索結果の件数
    @SerialName(value = "results_start")
    val resultsStart: Int, // 検索結果の開始位置
    val shop: List<Shop>?
)

@Serializable
data class Shop(
    val id: String?, // 店舗ID
    val name: String?, // 店舗名
    @SerialName(value = "logo_image")
    val logoImage: String?, // ロゴ画像
    @SerialName(value = "name_kana")
    val nameKana: String?, // 店舗名（かな）
    val address: String?, // 住所
    @SerialName(value = "station_name")
    val stationName: String?, // 最寄り駅名
    @SerialName(value = "ktai_coupon")
    val ktaiCoupon: Int?, // 0:携帯クーポン記載あり, 1:なし
    @SerialName(value = "large_service_area")
    val largeServiceArea: Area?, // 大エリア情報
    @SerialName(value = "service_area")
    val serviceArea: Area?, // 中エリア情報
    @SerialName(value = "large_area")
    val largeArea: Area?, // 都道府県情報
    @SerialName(value = "middle_area")
    val middleArea: Area?, // 区・市町村情報
    @SerialName(value = "small_area")
    val smallArea: Area?, // 詳細エリア情報
    val lat: Double?, // 緯度
    val lng: Double?, // 経度
    val genre: MainGenre?, // ジャンル
    @SerialName(value = "sub_genre")
    val subGenre: SubGenre?, // サブジャンル
    val budget: Budget?, // 予算
    @SerialName(value = "budget_memo")
    val budgetMemo: String?, // 予算メモ
    @SerialName(value = "catch")
    val catch: String?, // キャッチフレーズ
    val capacity: String?, // 収容人数
    val access: String?, // アクセス情報
    @SerialName(value = "mobile_access")
    val mobileAccess: String?, // スマホ用アクセス情報
    val urls: Urls?, // URL情報
    val photo: Photo?, // 写真情報
    val open: String?, // 営業時間
    val close: String?, // 定休日
    @SerialName(value = "party_capacity")
    val partyCapacity: String?, // 宴会収容人数
    val wifi: String?, // wifi あり、なし、未確認 のいずれか
    val wedding: String?, // 結婚式,二次会情報
    val course: String?, // コース料理の有無
    @SerialName(value = "free_drink")
    val freeDrink: String?, // 飲み放題の有無
    @SerialName(value = "free_food")
    val freeFood: String?, // 食べ放題の有無
    @SerialName(value = "private_room")
    val privateRoom: String?, // 個室の有無
    val horigotatsu: String?, // 掘りごたつ席の有無
    val tatami: String?, // 座敷の有無
    val card: String?, // クレジットカード利用可否
    @SerialName(value = "non_smoking")
    val nonSmoking: String?, // 禁煙・喫煙可否
    val charter: String?, // 貸切可否
    val ktai: String?, // 携帯電話OK
    val parking: String?, // 駐車場情報
    @SerialName(value = "barrier_free")
    val barrierFree: String?, // バリアフリー情報
    @SerialName(value = "other_memo")
    val otherMemo: String?, // その他設備
    val sommelier: String?, //ソムリエ
    @SerialName(value = "open_air")
    val openAir: String?,// オープンエア
    val show: String?, // ショー開催可否
    val equipment: String?, // エンタメ設備
    val karaoke: String?, // カラオケ設備の有無
    val band: String?, // バンド演奏可否
    val tv: String?, // テレビ視聴可否
    val english: String?, // 英語メニューの有無
    val pet: String?, // ペット同伴可否
    val child: String?, // お子様連れ情報
    val lunch: String?, // ランチ営業の有無
    val midnight: String?, // 深夜営業の有無
    @SerialName(value = "shop_detail_memo")
    val shopDetailMemo: String?, // 備考
    @SerialName(value = "coupon_urls")
    val couponUrls: CouponUrls? // クーポン情報
)

/**
 * エリア情報
 */
@Serializable
data class Area(
    val code: String?, // エリアコード
    val name: String? // エリア名
)

/**
 * ジャンル情報(グルメサーチapi)
 */
@Serializable
data class MainGenre(
    val code: String?, // ジャンルコード
    val name: String?, // ジャンル名
    val catch: String? // キャッチ
)

/**
 * サブジャンル
 */
@Serializable
data class SubGenre(
    val code: String?, // ジャンルコード
    val name: String? // ジャンル名
)

/**
 * 予算情報
 */
@Serializable
data class Budget(
    val code: String?, // 予算コード
    val name: String?, // 予算名
    val average: String? // 平均予算
)

/**
 * URL
 */
@Serializable
data class Urls(
    val pc: String?, // PC用URL
)

/**
 * 写真URL
 */
@Serializable
data class Photo(
    val pc: PCPhoto?, // PC用写真URL
    val mobile: MobilePhoto? // スマホ用写真URL
)

/**
 * PC用写真URL
 */
@Serializable
data class PCPhoto(
    val l: String?, // 一覧ページ用写真URL
    val m: String?, // メニューページ用写真URL
    val s: String? // 店舗詳細ページ用写真URL
)

/**
 * スマホ用写真URL
 */
@Serializable
data class MobilePhoto(
    val l: String?, // 一覧ページ用写真URL
    val s: String? // 店舗詳細ページ用写真URL
)

/**
 * クーポン情報
 */
@Serializable
data class CouponUrls(
    val pc: String?, // PC用クーポンURL
    val sp: String? // スマホ用クーポンURL
)