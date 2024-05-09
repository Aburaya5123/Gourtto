package jp.gourtto.gourmet_api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import jp.gourtto.BuildConfig.RECRUIT_API_KEY
import jp.gourtto.google_api.PlaceDetailsRequest
import jp.gourtto.google_api.PlaceIdRequest
import jp.gourtto.google_api.StreetViewMetadata
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.QueryMap
import retrofit2.http.Url


private const val GOURMET_BASE_URL = "https://webservice.recruit.co.jp/hotpepper/"

/**
 * [ApiClientService]の実装を行う
 * RetrofitのConverterにはjsonを選択しているため、
 *   リクエストのクエリパラメータに format=json を指定する必要がある
 */
object ApiClient {
    val retrofitService : ApiClientService by lazy {
        retrofit.create(ApiClientService::class.java)
    }
}

// Apiへのリクエストを行うインターフェイス
interface ApiClientService{
    // ジャンルマスターapi
    @GET("genre/v1/?key=${RECRUIT_API_KEY}&format=json")
    suspend fun getGenreMasterApi():GourmetGenre
    // グルメサーチapi: Map<'クエリパラメータ名', 'クエリパラメータ値'>
    @GET("gourmet/v1/")
    suspend fun getGourmetSearchApi(@QueryMap query:Map<String,String>):Gourmet

    /**
     * GoogleApiの呼び出しには、[GOURMET_BASE_URL]は使用できないので、
     *   @Url でURL全体を文字列で指定する
     */
    // GooglePlaceApi - TextSearch
    @GET
    suspend fun getPlaceId(@Url url: String): PlaceIdRequest
    // GooglePlaceApi - PlaceDetails
    @GET
    suspend fun getPlaceDetailInfo(@Url url: String): PlaceDetailsRequest
    // StreetViewStaticApi
    @GET
    suspend fun getStreetViewMeta(@Url url: String): StreetViewMetadata
}

// Parserとレスポンスのキーに相違があった際に、エラーを出さないように設定
@OptIn(ExperimentalSerializationApi::class)
private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

private val retrofit = Retrofit.Builder()
    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
    .baseUrl(GOURMET_BASE_URL)
    .build()