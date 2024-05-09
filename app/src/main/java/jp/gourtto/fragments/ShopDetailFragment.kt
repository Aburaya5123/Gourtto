package jp.gourtto.fragments

import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.transition.TransitionInflater
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.addCallback
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.gms.maps.OnStreetViewPanoramaReadyCallback
import com.google.android.gms.maps.StreetViewPanorama
import com.google.android.gms.maps.SupportStreetViewPanoramaFragment
import jp.gourtto.BuildConfig.PLACE_API_KEY
import jp.gourtto.R
import jp.gourtto.databinding.FragmentShopDetailBinding
import jp.gourtto.google_api.PlaceDetailsRequest
import jp.gourtto.google_api.StreetViewMetadata
import jp.gourtto.gourmet_api.DataShareViewModel
import jp.gourtto.gourmet_api.ErrorType
import jp.gourtto.gourmet_api.GoogleApiRequestListener
import jp.gourtto.gourmet_api.GoogleRequestType
import jp.gourtto.gourmet_api.Shop
import jp.gourtto.layouts.ArrangeShopInfo
import jp.gourtto.layouts.CustomDialog
import jp.gourtto.layouts.ShopDetailRecyclerViewAdapter


/**
 * 店舗詳細を表示するFragment
 * hotpepperApiに加えて、GooglePlaceApiも使用し、店舗の写真や電話番号、ストリートビューの取得、表示を行う
 * StreetViewの呼び出しはコストが高いため、注意が必要
 */
class ShopDetailFragment : Fragment(), OnStreetViewPanoramaReadyCallback,
    GoogleApiRequestListener {

    companion object{
        // PlaceIdのリクエスト(TextSearch)に用いるBASE_URL (FieldでPlaceIdのみ指定した場合、リクエストは無料)
        private const val PLACE_ID_REQUEST = "https://maps.googleapis.com/maps/api/place/findplacefromtext/"
        // PlaceDetailsリクエストに用いるBASE_URL (従量課金)
        private const val PLACE_DETAILS_REQUEST: String = "https://maps.googleapis.com/maps/api/place/details/"
        // PlacePhotoリクエストに用いるBASE_URL (従量課金)
        private const val PLACE_PHOTO_REQUEST: String = "https://maps.googleapis.com/maps/api/place/photo?"
        // StreetViewメタデータリクエストに用いるBASE_URL (リクエストは無料)
        private const val STREET_VIEW_META_REQUEST: String = "https://maps.googleapis.com/maps/api/streetview/metadata?"

        // PlacePhotoで取得する画像の最大サイズ
        private const val MAX_HEIGHT_PX: Int = 500
        private const val MAX_WIDTH_PX: Int = 500
        // PlacePhotoで取得する画像の最大枚数
        private const val MAX_DISPLAY_IMAGE_COUNT: Int = 10

        private val TAG = ShopDetailFragment::class.java.simpleName
    }

    private val viewModel: DataShareViewModel by lazy {
        ViewModelProvider(requireActivity())[DataShareViewModel::class.java]
    }

    private var _fragmentShopDetailBinding: FragmentShopDetailBinding? = null
    private val fragmentShopDetailBinding
        get() = _fragmentShopDetailBinding!!

    // StreetView
    private lateinit var streetViewPanoramaFragment: SupportStreetViewPanoramaFragment
    private lateinit var streetViewPanorama: StreetViewPanorama

    private lateinit var gourmetShopInfo: Shop // 詳細画面に表示する店舗情報(グルメサーチapiから取得)
    private lateinit var googlePlaceDetails: PlaceDetailsRequest // 詳細画面に表示する店舗情報(GooglePlaceDetailsから取得)
    private lateinit var streetViewPanoramaId: String // 店舗住所のStreetViewを表示するために必要なID

    // 店舗詳細画面のRecyclerView
    private lateinit var recyclerAdapterForDetails: ShopDetailRecyclerViewAdapter
    private lateinit var recyclerForDetails: RecyclerView

    // StreetViewのLocationが一度設定されるとtrue
    private var locationSet: Boolean = false
    // StreetViewFragmentが見える状態であればtrue
    private var streetViewVisibility: Boolean = false
    /*
     * Glideでロード済みの画像枚数
     * カウントが0になった時点でprogressバーを非表示にする
     */
    private var loadingPhotoCount: Int = 0

    /**
     * onResumeで true に設定
     * fragmentの生成と同時にonBackPressedが呼び出されるとエラーが出るため実装
     */
    private var initializationDone: MutableLiveData<Boolean> = MutableLiveData()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 店舗情報の取得に失敗した場合は、Fragmentを破棄
        viewModel.getShopDetailInfo()?.let{
            gourmetShopInfo = it
            // 店舗情報を基にGooglePlaceIdを取得
            getPlaceId(gourmetShopInfo)
            // 店舗情報を基にStreetViewMetaリクエストを行い、StreetViewが利用可能であるか確認
            getStreetViewMeta(gourmetShopInfo)
        }
        if (::gourmetShopInfo.isInitialized.not()){
            destroyFragment()
        }
        // Fragment遷移のアニメーションの設定
        val inflater = TransitionInflater.from(requireContext())
        enterTransition = inflater.inflateTransition(R.transition.slide_right)
        exitTransition = inflater.inflateTransition(R.transition.slide_right)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentShopDetailBinding =
            FragmentShopDetailBinding.inflate(inflater, container, false)

        // デバイスのBackボタンが押された際のコールバックを設定
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            isEnabled =false
            if (initializationDone.value == true){
                destroyFragment()
            }
            else{
                initializationDone.observe(viewLifecycleOwner, Observer {
                    var done = false
                    if (it && done.not()){
                        done = true
                        destroyFragment()
                    }
                })
            }
        }
        return fragmentShopDetailBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // gourmetShopInfoを基に、UI画面を作成
        setUiElements()
        adduiListeners()
    }

    override fun onDestroy() {
        detachUiListeners()
        _fragmentShopDetailBinding = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        initializationDone.value = true
    }

    private fun adduiListeners(){
        fragmentShopDetailBinding.backButton.setOnClickListener{
            destroyFragment()
        }
    }

    private fun detachUiListeners(){
        if (_fragmentShopDetailBinding == null) return
        fragmentShopDetailBinding.couponAvailable.setOnClickListener(null)
        fragmentShopDetailBinding.originalSite.setOnClickListener(null)
        fragmentShopDetailBinding.streetviewSwitch.setOnClickListener(null)
        fragmentShopDetailBinding.callNumber.setOnClickListener(null)
        fragmentShopDetailBinding.backButton.setOnClickListener(null)
    }

    /**
     * 店舗の名前と住所を基に、店舗を識別するGoogleのPlaceIdを取得
     * PlaceIdは、電話番号やレビュー等の情報にアクセスする際に必要となる
     * [info] 詳細画面に表示する店舗情報(グルメサーチapi)
     */
    private fun getPlaceId(info: Shop){
        val requestUrl: String =
            "${PLACE_ID_REQUEST}json?" +
                    "key=${PLACE_API_KEY}" +
                    "&fields=place_id" +
                    "&language=ja" +
                    "&input=${info.name} ${info.address}" +
                    "&inputtype=textquery"
       viewModel.getGooglePlaceId(requestUrl,this, requireActivity(), parentFragmentManager)
    }

    /**
     * [placeId]を基に、店舗の電話番号、レビュー、評価、写真をリクエスト
     */
    private fun getGoogleShopDetail(placeId: String){
        val requestUrl: String =
            "${PLACE_DETAILS_REQUEST}json?" +
                    "key=${PLACE_API_KEY}" +
                    "&fields=formatted_phone_number,rating,reviews,photos" +
                    "&language=ja" +
                    "&place_id=${placeId}"
        viewModel.getGoogleShopDetail(requestUrl, this, requireActivity(), parentFragmentManager)
    }

    /**
     * 店舗の住所を基に、StreetViewが利用可能であるか確認を行う
     */
    private fun getStreetViewMeta(shopInfo: Shop){
        val requestUrl: String =
            STREET_VIEW_META_REQUEST +
                    "key=${PLACE_API_KEY}" +
                    "&location=${shopInfo.address} ${shopInfo.name}"
        viewModel.getStreetViewMeta(requestUrl, this, requireActivity(), parentFragmentManager)
    }

    // PlaceDetailsのPhotoReferenceを基に、店舗画像のリクエストURLを作成
    private fun getPlacePhotoUrl(ref: String): String{
        return PLACE_PHOTO_REQUEST +
                    "photo_reference=${ref}" +
                    "&key=${PLACE_API_KEY}" +
                    "&maxheight=${MAX_HEIGHT_PX}"
                    //"&maxwidth=${MAX_WIDTH_PX}"
    }

    /**
     * StreetViewの初期化
     * Fragment内では、[onCreateView]でレイアウトをinflate後に呼び出す必要がある
     */
    private fun initStreetViewPanorama(){
        streetViewPanoramaFragment =
            childFragmentManager.findFragmentById(R.id.streetviewpanorama)
                    as SupportStreetViewPanoramaFragment
        streetViewPanoramaFragment.getStreetViewPanoramaAsync(this)
    }

    /*
     * StreetViewの初期化が完了した際のコールバック
     */
    override fun onStreetViewPanoramaReady(panorama: StreetViewPanorama) {
        streetViewPanorama = panorama
        // StreetViewの切り替えButtonの作成
        setStreetViewSwitch()
    }

    // viewModelが持つ店舗詳細画面の店舗IDを削除し、自らFragmentの破棄を行う
    private fun destroyFragment(){
        viewModel.resetDisplayedShopId()
        getParentFragmentManager().beginTransaction().remove(this).commit()
    }

    // hotpapperApiの情報を基に、UI画面の設定を行う
    private fun setUiElements() {
        // 店舗名
        fragmentShopDetailBinding.shopNameDetail.text = gourmetShopInfo.name
        // 店舗の画像（hotpapper）
        gourmetShopInfo.photo?.pc?.l?.takeIf { it.isNotEmpty() }
            ?.let {
                insertImageView(it)
            }
        /*
         * クーポンのリンクをButtonに設定
         * リンクがない場合は、Buttonアイコンを変更
         */
        gourmetShopInfo.couponUrls?.sp?.takeIf { it.isNotEmpty()}
            ?.let{url ->
                fragmentShopDetailBinding.couponAvailable.setOnClickListener{
                    openUrl(url)
                }
            }
            ?:{
                fragmentShopDetailBinding.couponAvailable.apply {
                    text = getString(R.string.no_coupon)
                    background = AppCompatResources.getDrawable(context, R.drawable.coupon_not_available)
                }
            }
        // hotpepperサイトのリンクをButtonに設定
        gourmetShopInfo.urls?.pc?.takeIf { it.isNotEmpty() }?.let{
            url ->
            fragmentShopDetailBinding.originalSite.setOnClickListener{
                openUrl(url)
            }
        }
        // RecyclerViewにその他情報を設定
        recyclerAdapterForDetails = ShopDetailRecyclerViewAdapter(ArrangeShopInfo(gourmetShopInfo).arrange())
        recyclerForDetails = fragmentShopDetailBinding.shopDetailRecyclerView.apply {

            layoutManager = LinearLayoutManager(context)
            setAdapter(recyclerAdapterForDetails)
        }
    }

    /**
     * [googlePlaceDetails]のPhotoReferencesを基に、
     *   店舗に関連する画像を表示する
     * 表示する画像の数については、[MAX_DISPLAY_IMAGE_COUNT]で指定
     */
    private fun setPlacePhotos(placeDetails: PlaceDetailsRequest){
        placeDetails.result?.photos?.let{
            // ロードする画像枚数を取得
            loadingPhotoCount =
                if (it.size > MAX_DISPLAY_IMAGE_COUNT){
                    MAX_DISPLAY_IMAGE_COUNT
                }
                else{
                    it.size
                    }
            // 0枚の場合はprogressバーを非表示に
            if (loadingPhotoCount == 0){
                fragmentShopDetailBinding.progressBarView.visibility = View.GONE
                return
            }
            /**
             * Glideに画像のロードが完了した際のリスナーを渡し、
             *   完了するたびに[loadingPhotoCount]のカウントを減らす
             */
            val loadCompleteListener = object: RequestListener<Drawable>{
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    loadingPhotoCount--
                    // すべての画像のロードが完了
                    if (loadingPhotoCount == 0 && _fragmentShopDetailBinding!=null)
                        fragmentShopDetailBinding.progressBarView.visibility = View.GONE
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    loadingPhotoCount--
                    if (loadingPhotoCount == 0 && _fragmentShopDetailBinding!=null)
                        fragmentShopDetailBinding.progressBarView.visibility = View.GONE
                    return false
                }
            }
            for ((counter, ref) in it.withIndex()){
                insertImageView(getPlacePhotoUrl(ref.photoReference), loadCompleteListener)
                if (counter >= MAX_DISPLAY_IMAGE_COUNT - 1) break
            }
        }?:{
            fragmentShopDetailBinding.progressBarView.visibility = View.GONE
        }
    }

    /**
     * [fragmentShopDetailBinding]の
     *   HorizontalScrollViewに[url]で指定された画像を追加
     * [mListener] PlacePhotosのロード時に使用
     */
    private fun insertImageView(url: String, mListener: RequestListener<Drawable>? = null){
        val image: ImageView = ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, // width
                ViewGroup.LayoutParams.MATCH_PARENT // height
            ).apply {
                setPadding(3, 0, 3, 0) // 水平padding
            }
        }
        // PlacePhotos
        if (mListener != null){
            Glide.with(requireContext()).load(url).listener(mListener).into(image).apply {
                fragmentShopDetailBinding.shopImagesHolder.addView((image))
            }
        }
        // hotpepper画像
        else{
            Glide.with(requireContext()).load(url).into(image).apply {
                fragmentShopDetailBinding.shopImagesHolder.addView((image))
            }
        }
    }

    // urlを開く
    private fun openUrl(url: String){
        val link = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(link)
    }

    // 電話番号が見つかった際に、ダイアルButtonの設定
    private fun setPhoneNumber(phoneNumber: String){
        fragmentShopDetailBinding.callNumber.apply{
            visibility = View.VISIBLE
            setOnClickListener{
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
                startActivity(intent)
            }
        }
    }

    /*
     * StreetViewと店舗画像の切り替えButtonの設定
     * StreetViewMetaの取得と、StreetViewの初期化が完了した後に実行
     */
    private fun setStreetViewSwitch(){
        fragmentShopDetailBinding.streetviewSwitch.apply {
            visibility = View.VISIBLE
            setOnClickListener{
                // Fragmentが初期化されていない,もしくはアタッチされていない場合は、Buttonを無効化
                if (::streetViewPanoramaFragment.isInitialized.not()
                    || streetViewPanoramaFragment.isAdded.not())
                {
                    visibility = View.GONE

                    CustomDialog.create(true,
                        getString(R.string.street_view_error_title),
                        getString(R.string.street_view_error_body),
                        getString(R.string.dialog_close),
                        getString(R.string.dialog_close))
                        .show(parentFragmentManager, ShopDetailFragment::class.simpleName)
                    return@setOnClickListener
                }
                // 位置の更新には課金が発生するため、一度限り行う
                if (locationSet.not()){
                    locationSet = true
                    setStreetViewLocation()
                }

                // StreetViewが非表示の場合
                if (streetViewVisibility.not()){
                    streetViewVisibility = streetViewVisibility.not()
                    // StreetViewを表示
                    childFragmentManager.beginTransaction()
                        .show(streetViewPanoramaFragment)
                        .commit()
                    // 店舗写真Layoutを非表示にする
                    fragmentShopDetailBinding.shopImagesView.visibility = View.INVISIBLE
                    // Buttonを切り替え
                    text = getString(R.string.image_mode)
                    background = AppCompatResources.getDrawable(context, R.drawable.street_view_switch_off)
                }
                // StreetViewが表示されている場合
                else {
                    streetViewVisibility = streetViewVisibility.not()
                    // StreetViewを非表示
                    childFragmentManager.beginTransaction()
                        .hide(streetViewPanoramaFragment)
                        .commit()
                    // 店舗写真Layoutを表示する
                    fragmentShopDetailBinding.shopImagesView.visibility = View.VISIBLE
                    // Buttonを切り替え
                    text = getString(R.string.street_view_mode)
                    background = AppCompatResources.getDrawable(context, R.drawable.street_view_switch_on)
                }
            }
        }
    }

    /**
     * StreetViewに[streetViewPanoramaId]のViewを設定
     * 呼び出し毎に課金が発生するため、ユーザーが StreetViewButton をクリックしたタイミングで、一度だけ実行
     */
    private fun setStreetViewLocation(){
        if (::streetViewPanorama.isInitialized.not() ||
            ::streetViewPanoramaId.isInitialized.not()) return

        streetViewPanorama.isUserNavigationEnabled = false // ユーザーに別のパノラマに移動することを許可しない
        streetViewPanorama.setPosition(streetViewPanoramaId)
    }

    /**
     * GoogleApiのSuccessListener
     */
    override fun onGoogleRequestSucceed(
        type: GoogleRequestType,
        placeId: String?,
        placeDetail: PlaceDetailsRequest?,
        streetViewMeta: StreetViewMetadata?
    ) {
        when(type){
            GoogleRequestType.PLACE_ID_REQUEST ->
                onPlacesIdRequestSucceed(placeId)
            GoogleRequestType.PLACE_DETAIL_REQUEST ->
                onPlacesDetailsRequestSucceed(placeDetail)
            GoogleRequestType.STREET_VIEW_META_REQUEST ->
                onStreetViewMetaRequestSucceed(streetViewMeta)
        }
    }

    /**
     * GoogleApiのFailureListener
     */
    override fun onGoogleRequestFailed(
        type: GoogleRequestType,
        errorType: ErrorType,
        error: String
    ) {
        when(type){
            GoogleRequestType.PLACE_ID_REQUEST ->
                onPlacesIdRequestFailed(errorType, error)
            GoogleRequestType.PLACE_DETAIL_REQUEST ->
                onPlacesDetailsRequestFailed(errorType, error)
            GoogleRequestType.STREET_VIEW_META_REQUEST ->
                onStreetViewMetaRequestFailed(errorType, error)
        }
    }

    /**
     * [getPlaceId]のリクエストに成功
     * 取得した[placeId]を元に、PlaceDetailsリクエストを行う
     */
    private fun onPlacesIdRequestSucceed(placeId: String?) {
        placeId?.let{
            getGoogleShopDetail(it)
        }
    }

    /**
     * [getGoogleShopDetail]リクエストに成功し、店舗の詳細情報の取得に成功
     * 情報を基に、ストリートビューの設定, UI画面の更新を行う
     */
    private fun onPlacesDetailsRequestSucceed(data: PlaceDetailsRequest?) {
        data?.let{detailData ->
            googlePlaceDetails = detailData
            setPlacePhotos(googlePlaceDetails)

            // 電話番号を持っていれば、ダイアルButtonの設定を行う
            googlePlaceDetails.result?.formattedPhoneNumber?.takeIf { it.isNotEmpty() }
                ?.let{ setPhoneNumber(it) }
        }
    }

    /**
     * [getStreetViewMeta]リクエストに成功したので、panoIdを変数に格納
     *   StreetViewFragmentの初期化を行う
     */
   private fun onStreetViewMetaRequestSucceed(data: StreetViewMetadata?) {
       data?.let{metaData ->
           metaData.panoId?.let { streetViewPanoramaId = it }
           initStreetViewPanorama()
       }
    }

    /**
     * [getPlaceId]のリクエストに失敗
     * Googleの情報は使用せずに、hotpepperApiの情報だけで画面設定を行う
     */
    private fun onPlacesIdRequestFailed(errorType: ErrorType, error: String) {
        Log.e(TAG, error)
        if (_fragmentShopDetailBinding!=null){
            fragmentShopDetailBinding.progressBarView.visibility = View.GONE
        }
    }

    /**
     * [getGoogleShopDetail]リクエストに失敗
     * Googleの情報は使用せずに、hotpepperApiの情報だけで画面設定を行う
     */
    private fun onPlacesDetailsRequestFailed(errorType: ErrorType, error: String) {
        Log.e(TAG, error)
        if (_fragmentShopDetailBinding!=null){
            fragmentShopDetailBinding.progressBarView.visibility = View.GONE
        }
    }

    /**
     * [getStreetViewMeta]リクエストに失敗
     * StreetViewのMetaデータの取得に失敗したので、StreetView表示Buttonを無効化する
     */
    private fun onStreetViewMetaRequestFailed(errorType: ErrorType, error: String) {
        Log.e(TAG, error)
        if (_fragmentShopDetailBinding!=null){
            fragmentShopDetailBinding.streetviewSwitch.visibility = View.GONE
        }
    }
}