package jp.gourtto.google_api

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsResponse
import com.google.android.libraries.places.api.net.PlacesClient


// AutoCompleteのレスポンスに対応したリスナー
interface PlaceAutoCompleteListener{
    // 入力テキストから候補が見つかった場合
    fun onCandidateFound()
    // 入力テキストから候補が見つからなかった場合
    fun onCandidateNotFound()
    // 候補を選択し、緯度経度を含む位置情報の取得に成功
    fun onFetchSucceed(place: Place)
}

/**
 * AutoCompleteの候補を表示するRecyclerViewのAdapter
 */
class PlacesAutoCompleteAdapter(
    context: Context,
    private val listener: PlaceAutoCompleteListener) :
    RecyclerView.Adapter<PlacesAutoCompleteHolder>(){

    companion object{
        private val TAG = PlacesAutoCompleteAdapter::class.java.simpleName
    }

    /**
     * 見つかった候補を格納
     * このListを更新し、notifyDataSetChanged() を呼ぶことで、Viewが更新される
     */
    private var resultList: List<PlaceAutocomplete> = listOf()

    // PlaceApiインスタンス
    private val placesClient: PlacesClient = Places.createClient(context)

    /**
     * セッショントークン
     * findAutoCompleteRequestを開始してから、FetchPlaceRequestが完了するまでの間有効
     */
    private lateinit var placesToken: AutocompleteSessionToken
    // トークンが有効であるかどうか
    private var placesTokenAvailable: Boolean = false
    // 現在位置
    private var myLocation: LatLng? = null

    // AutoCompleteの候補を保持
    data class PlaceAutocomplete(
        val placeId: String,
        val name: String,
        val address: String,
        val distance: Int? = null
    )


    /**
     * トークンのリセット
     * FetchPlaceRequestの完了後に再度取得する
     */
    private fun resetToken(){
        placesToken = AutocompleteSessionToken.newInstance()
        placesTokenAvailable = true
    }

    // MainActivityにて現在位置が更新された際に呼び出される
    fun myLocationChanged(latlng: LatLng){
        myLocation = latlng
    }

    // 候補の取得
    fun getPredictions(input: String){
        if (placesTokenAvailable.not()){
            resetToken()
        }
        val results: MutableList<PlaceAutocomplete> = mutableListOf()

        val request =
            FindAutocompletePredictionsRequest.builder().apply {
                if (myLocation!=null){
                    origin = myLocation // myLocationからの距離を取得
                }
                /**
                 * setLocationBias(bounds)　bounds矩形の範囲を優先して表示
                 * setTypesFilter(listOf(PlaceTypes.ADDRESS)) 検索フィルター
                 */
                setCountries("JP")
                sessionToken = placesToken
                query = input
            }.build()
        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response: FindAutocompletePredictionsResponse ->
                for (prediction in response.autocompletePredictions) {
                    if (prediction.placeId.isNullOrEmpty()) continue
                    results.add(
                        PlaceAutocomplete(prediction.placeId,
                                          prediction.getPrimaryText(null).toString(),
                                          prediction.getSecondaryText(null).toString(),
                                          prediction.distanceMeters))
                }
                resultList = results
                notifyDataSetChanged() // RecyclerViewの更新
                // 候補なし
                if (resultList.isEmpty()){
                    listener.onCandidateNotFound()
                }
                // 候補あり
                else{
                    listener.onCandidateFound()
                }

            }.addOnFailureListener { exception: Exception? ->
                if (exception is ApiException) {
                    Log.e(TAG, "Place not found: ${exception.statusCode}")
                }
                listener.onCandidateNotFound()
            }
    }

    /*
     * AutoComplete候補を選択した際のコールバック
     * positionは、RecyclerViewの中でのIndex
     */
    private fun onClickPredictionsCallback(position: Int){
        /**
         * [FetchPlaceRequest]のタイミングでTokenは無効となる
         */
        placesTokenAvailable = false

        val item = resultList[position]
        // FetchPlaceRequestで要求する項目
        val placeFields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.LAT_LNG,
            Place.Field.ADDRESS
        )
        val request = FetchPlaceRequest.builder(item.placeId, placeFields).build()
        placesClient.fetchPlace(request).addOnSuccessListener { response ->
            // Fieldで指定した情報の取得に成功
            listener.onFetchSucceed(response.place)
        }.addOnFailureListener { exception ->
            if (exception is ApiException) {
                Log.e(TAG, exception.toString())
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlacesAutoCompleteHolder {
        val inflate: View = LayoutInflater.from(parent.context)
            .inflate(jp.gourtto.R.layout.recycler_item_auto_complete, parent, false)
        return PlacesAutoCompleteHolder(inflate)
    }

    override fun onBindViewHolder(holder: PlacesAutoCompleteHolder, position: Int) {
        holder.primaryText.text = resultList[position].address
        holder.secondaryText.text = resultList[position].name
        resultList[position].distance?.let{
            // メートル　を キロメートル　に変換
            if (it<1000){
                holder.distance.text = "${it}m"
            }
            else{
                holder.distance.text = "${it.toFloat()/1000}km"
            }
        }
        holder.row.setOnClickListener{
            onClickPredictionsCallback(position)
        }
    }

    override fun getItemCount(): Int {
        return resultList.size
    }
}