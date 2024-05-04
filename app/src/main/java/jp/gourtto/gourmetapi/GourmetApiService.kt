package jp.gourtto.gourmetapi

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import jp.gourtto.BuildConfig.RECRUIT_API_KEY
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import retrofit2.Retrofit
import retrofit2.http.GET
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.http.QueryMap


private const val BASEURL = "https://webservice.recruit.co.jp/hotpepper/"

/**
 * [GourmetApiService]の実装を行う
 * RetrofitのConverterにはjsonを選択しているため、
 *   グルメapiのクエリパラメータに format=json を指定する必要がある
 */
object GourmetApi {
    val retrofitService : GourmetApiService by lazy {
        retrofit.create(GourmetApiService::class.java)
    }
}

// Apiへのリクエストを行うインターフェイス
interface GourmetApiService{
    // ジャンルマスターapi
    @GET("genre/v1/?key=${RECRUIT_API_KEY}&format=json")
    suspend fun getGenreParams():GourmetGenre
    // グルメサーチapi
    // query には Map<'クエリパラメータ名', 'クエリパラメータ値'> を指定
    @GET("gourmet/v1/")
    suspend fun getShopData(@QueryMap query:Map<String,String>):Gourmet
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
    .baseUrl(BASEURL)
    .build()