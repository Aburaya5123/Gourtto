package jp.gourtto.google_api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


/**
 * GoogleApiのレスポンスをパースする際に使用するdata class
 *   [PlaceIds] -> PlaceIdのリクエスト(条件により無料)
 *   [PlaceDetailsRequest] -> PlaceIdを基に、PlaceDetailsをリクエスト(従量課金)
 *   [StreetViewMetadata] -> 住所,緯度経度を基に、StreetViewのメタデータを取得(無料)
 *
 * status: String ->
 *   'OK' -> リクエストに成功, その他 'ZERO_RESULTS', 'NOT_FOUND', 'INVALID_REQUEST'...
 */

// PlaceIdリクエスト <root>
@Serializable
data class PlaceIdRequest(
    val candidates: List<PlaceIds>,
    val status: String
)

// PlaceIdリクエスト
@Serializable
data class PlaceIds(
    @SerialName(value = "place_id")
    val placeId: String
)

// PlaceDetailsリクエスト <root>
@Serializable
data class PlaceDetailsRequest(
    val result: PlaceDetails?,
    val status: String
)

// PlaceDetailsリクエスト
@Serializable
data class PlaceDetails(
    @SerialName("formatted_phone_number")
    val formattedPhoneNumber: String, // フォーマット済み電話番号 03-xxxx-xxxx
    val photos: List<Photos>,
    val rating: String, // 5つ星満点
    val reviews: List<Review>? // レビュー
)

// StreetViewMetaリクエスト <root>
@Serializable
data class StreetViewMetadata(
    val copyright: String?,
    val date: String?,
    val location: Location?,
    @SerialName(value="pano_id")
    val panoId: String?,
    val status: String
)

// StreetViewMetaリクエスト - 緯度経度
@Serializable
data class Location(
    val lat: Double,
    val lng: Double
)

// PlaceDetailsリクエスト - ユーザーレビュー
@Serializable
data class Review(
    @SerialName("author_name")
    val authorName: String, // レビュアーの名前
    @SerialName("author_url")
    val authorUrl: String, // レビュアーのGoogleMapプロフォールUrl
    val language: String, // レビューの言語
    @SerialName("original_language")
    val originalLanguage: String, // レビューの元の言語
    @SerialName("profile_photo_url")
    val profilePhotoUrl: String, // レビュアーのプロフィール画像
    val rating: Int, // 5つ星満点
    @SerialName("relative_time_description")
    val relativeTimeDescription: String, // レビューの投稿から経過した日数
    val text: String, // レビュー本文
    val time: Long, // UNIXタイムスタンプ
    val translated: Boolean // レビューが翻訳済みであるか
)

// PlaceDetailsリクエスト - PhotoReferences(PlacePhotosのリクエストに必要)
@Serializable
data class Photos(
    val height: Int, // 画像の高さ
    @SerialName("html_attributions")
    val htmlAttributions: List<String>,
    @SerialName("photo_reference")
    val photoReference: String,
    val width: Int // 画像の横幅
)