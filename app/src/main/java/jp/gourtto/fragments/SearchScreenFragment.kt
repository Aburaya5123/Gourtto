package jp.gourtto.fragments

import android.content.Context
import android.os.Bundle
import android.transition.TransitionInflater
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.widget.SearchView
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.Place
import jp.gourtto.R
import jp.gourtto.databinding.FragmentSearchScreenBinding
import jp.gourtto.google_api.PlaceAutoCompleteListener
import jp.gourtto.google_api.PlacesAutoCompleteAdapter
import jp.gourtto.gourmet_api.DataShareViewModel
import jp.gourtto.gourmet_api.ErrorType
import jp.gourtto.gourmet_api.GourmetApiRequestListener
import jp.gourtto.gourmet_api.GourmetGenre
import jp.gourtto.gourmet_api.GourmetRequestType
import jp.gourtto.layouts.CustomDialog
import jp.gourtto.layouts.ExpandableListAdapter


/**
 * 検索画面のViewを作成するFragment
 */
class SearchScreenFragment : Fragment(), GourmetApiRequestListener, PlaceAutoCompleteListener{

    companion object {
        // グルメサーチApiからの最大レスポンス件数
        private const val MAX_OUTPUT_COUNT = 100

        private val TAG = SearchScreenFragment::class.java.simpleName
    }

    private val viewModel: DataShareViewModel by lazy {
        ViewModelProvider(requireActivity())[DataShareViewModel::class.java]
    }

    private var _fragmentSearchingBinding: FragmentSearchScreenBinding? = null
    private val fragmentSearchingBinding
        get() = _fragmentSearchingBinding!!

    // 検索条件を指定する画面のExpandableListViewAdapter
    private lateinit var searchParameterExpandableAdapter: ExpandableListAdapter
    // 検索範囲設定項目
    private lateinit var radiusRadioGroup: RadioGroup

    // PlaceApiのAutoCompleteを表示させるRecyclerView
    private lateinit var  autoCompleteAdapter: PlacesAutoCompleteAdapter
    private lateinit var autoCompleteRecyclerView: RecyclerView
    private lateinit var mLayoutManager: LinearLayoutManager

    // 現在位置のObserver
    private lateinit var mLocationObserver: Observer<LatLng?>
    // ユーザーの操作以外でSearchViewにテキスト入力があった際にtrueとなる
    private var searchViewAutoFill: Boolean = false
    // 検索の準備が完了したさいにtrueとなる
    private var isReadyForSearch: Boolean = false

    /**
     * [radiusRadioGroup]がExpandableListViewのヘッダーとして追加された後に、この処理を実行
     */
    private lateinit var delayedAddListener: Runnable

    /*
     * グルメサーチApiリクエストのクエリパラメータとして、retrofitに渡す値
     * 検索条件を <Key, Value> の形で格納
     */
    private lateinit var gourmetRequestQueryParams: Map<String,String>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fragment遷移のアニメーションの設定
        val inflater = TransitionInflater.from(requireContext())
        enterTransition = inflater.inflateTransition(R.transition.slide_left)
        exitTransition = inflater.inflateTransition(R.transition.slide_left)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentSearchingBinding =
          FragmentSearchScreenBinding.inflate(inflater, container, false)
        return fragmentSearchingBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setAutoCompleteAdapter()
        addUiListeners()
        addObserver()
        // ジャンルマスタapiへリクエストを実行し、ジャンル一覧を取得
        viewModel.getGenreParams(this, requireActivity(), parentFragmentManager)
    }

    override fun onDestroyView(){
        detachUiListeners()
        detachObserver()
        _fragmentSearchingBinding = null
        super.onDestroyView()
    }

    // AutoCompleteRecyclerViewにAdapterを設定
    private fun setAutoCompleteAdapter(){
        autoCompleteAdapter = PlacesAutoCompleteAdapter(requireContext(),this)
        autoCompleteRecyclerView = fragmentSearchingBinding.autoCompleteRecyclerView
        mLayoutManager = LinearLayoutManager(context)
        autoCompleteRecyclerView.apply{
            layoutManager = mLayoutManager
            setAdapter(autoCompleteAdapter)
        }
        autoCompleteAdapter.notifyDataSetChanged()
    }

    // Uiのリスナーを追加
    private fun addUiListeners(){
        // 検索ボタンが押された際のリスナー
        fragmentSearchingBinding.gourmetSearchButton.setOnClickListener {
            onSearchButtonClicked()
        }
        // SearchViewにテキストが入力された際のリスナー
        fragmentSearchingBinding.searchLocationView
            .setOnQueryTextListener(object: SearchView.OnQueryTextListener{
            override fun onQueryTextChange(newText: String?): Boolean {
                // 文字が入力されており、かつユーザーの操作による入力であった場合
                if (newText.isNullOrBlank().not() && searchViewAutoFill.not()) {
                    autoCompleteAdapter.getPredictions(newText!!)
                }
                // コード内でSearchViewのテキストを変更した際は、getPrediction()を呼ばない
                else if (searchViewAutoFill){
                    searchViewAutoFill = false
                    autoCompleteRecyclerView.visibility = View.INVISIBLE
                }
                else {
                    autoCompleteRecyclerView.visibility = View.INVISIBLE
                }
                return false
            }

                /**
                 * このSearchViewはあくまで候補地の選択を行う目的なので、
                 *   Submit機能は実装していない
                 */
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }
        })
        // SearchViewのFocusが変化した際のリスナー
        fragmentSearchingBinding.searchLocationView.setOnQueryTextFocusChangeListener{
                _, hasfocus ->
                if (hasfocus.not()){
                    autoCompleteRecyclerView.visibility = View.INVISIBLE
                }
        }
        // RadioGroupで選択された検索範囲を基に、MainActivityでサークルの再描写を行う
        delayedAddListener = Runnable {
            view?.findViewById<RadioGroup>(R.id.param1_options)?.let{
                radiusRadioGroup = it
            }
            if (::radiusRadioGroup.isInitialized){
                radiusRadioGroup.setOnCheckedChangeListener { group, checkedId ->
                    val selectedIndex: Int =
                        group.indexOfChild(group.findViewById(checkedId))
                    val radius: Double? =
                        when(selectedIndex){
                            0 -> 300.0
                            1 -> 500.0
                            2 -> 1000.0
                            3 -> 2000.0
                            4 -> 3000.0
                            else -> null
                        }
                    radius?.let { viewModel.onSelectedRadiusChanged(radius) }
                }
                viewModel.onSelectedRadiusChanged(300.0)
            }
        }
    }

    private fun detachUiListeners(){
        if (_fragmentSearchingBinding!=null){
            fragmentSearchingBinding.gourmetSearchButton.setOnClickListener(null)
            fragmentSearchingBinding.searchLocationView.setOnQueryTextListener(null)
        }
    }

    // 現在位置を取得していた場合、AutoCompleteRequestを行う際に目的地との距離を受け取る
    private fun addObserver(){
        // MainActivityで取得した現在位置のObserver
        mLocationObserver = Observer{ location ->
            location?.let{
                if (::autoCompleteAdapter.isInitialized){
                    autoCompleteAdapter.myLocationChanged(location)
                }
            }
        }
        viewModel.myLocation.observe(viewLifecycleOwner, mLocationObserver)
    }

    private fun detachObserver(){
        if (::mLocationObserver.isInitialized)
            viewModel.myLocation.removeObserver(mLocationObserver)
    }

    // AutoComplete候補が見つかった際のリスナー
    override fun onCandidateFound() {
        if (autoCompleteRecyclerView.visibility == View.INVISIBLE){
            mLayoutManager.scrollToPositionWithOffset(0, 0)
            autoCompleteRecyclerView.visibility =  View.VISIBLE
        }
    }

    // AutoComplete候補が見つからなかった際のリスナー
    override fun onCandidateNotFound() {
        if (autoCompleteRecyclerView.visibility == View.VISIBLE)
            autoCompleteRecyclerView.visibility =  View.INVISIBLE
    }

    /**
     * AutoCompleteの候補をユーザーが選択し、
     *   緯度経度の取得に成功した際のリスナー
     */
    override fun onFetchSucceed(place: Place) {
        place.latLng?.let{
            viewModel.updateTargetLocation(it)
            viewModel.moveTo(it)
        }
        autoCompleteRecyclerView.visibility = View.INVISIBLE
        hideKeyboard()
        searchViewAutoFill = true
        fragmentSearchingBinding.searchLocationView.apply {
            setQuery(place.name, false)
        }
        // BottomSheetを隠す
        //viewModel.bottomSheetStateChange(BottomSheetBehavior.STATE_COLLAPSED)
    }

    /*
     * 検索ボタンが押された際のリスナー
     * 各種設定項目の値を取得し、クエリパラメータMap<Key,Value>に変換後、グルメApiServiceを呼び出す
     */
    private fun onSearchButtonClicked(){
       if (isReadyForSearch.not()) return

        onSearchProgress(true)

        val queryParameter: MutableMap<String, String> = mutableMapOf()

        // 検索範囲パラメータの追加
        queryParameter["range"] = getSearchRadius().toString()
        // ジャンルパラメータの追加
        for (code in getSelectedGenres()){
            queryParameter["genre"] = code
        }
        // 最大検索件数の設定
        queryParameter["count"] = MAX_OUTPUT_COUNT.toString()
        // リクエスト送信
        viewModel.getSearchResults(this, queryParameter,
            requireActivity(), parentFragmentManager)
    }

    /**
     * 検索範囲のRadioButtonから選択されている要素を取得
     * apiの検索範囲パラメータは300m, 500m, 1000m, 2000m, 3000m の順に、(int) 1, 2, 3, 4, 5 が対応するので、
     *   戻り値は index + 1 の値となる
     * ExpandableListViewにヘッダーとして後から追加しているため、
     *   viewBindingは使用できない (layout/expandable_list_view_header.xml)
     */
    private fun getSearchRadius(): Int{
        var buttonIndex = 0 // 初期値
        view?.findViewById<ViewGroup>(R.id.param1_options)?.let { group ->
            for (radioButton in group.children){
                (radioButton as? RadioButton)?.takeIf {radio ->
                    radio.isChecked
                }?.let{
                    buttonIndex = group.indexOfChild(it)
                }
            }
        }
        return buttonIndex + 1
    }

    /**
     * [ExpandableListAdapter]から現在選択されているジャンル名を取得
     * ジャンルマスターapiの結果を格納した[gourmetRequestQueryParams]を参照し、
     *   選択されているジャンル名を基にクエリパラメータ用のコードを取得する
     * 戻り値[params]には、お店ジャンルコード が文字列で格納されている
     */
    private fun getSelectedGenres():List<String>{
        val params: MutableList<String> = mutableListOf()
        searchParameterExpandableAdapter.returnSelectedGenres()?.let{ indexes ->
            for (index in indexes){
                gourmetRequestQueryParams[index.value]?.let { params.add(it) }
            }
        }
        return params
    }

    /**
     * hotpepperApiのSuccessListener
     */
    override fun onGourmetRequestSucceed(type: GourmetRequestType, genreData: GourmetGenre?)
    {
        when(type){
            GourmetRequestType.GENRE_MASTER_API ->
                onGenreRequestSucceed(genreData)
            GourmetRequestType.GOURMET_SEARCH_API ->
                onSearchRequestSucceed()
        }
    }

    /**
     * hotpepperApiのFailureListener
     */
    override fun onGourmetRequestFailed(
        type: GourmetRequestType,
        errorType: ErrorType,
        error: String
    ) {
        when(type){
            GourmetRequestType.GENRE_MASTER_API ->
                onGenreRequestFailed(errorType, error)
            GourmetRequestType.GOURMET_SEARCH_API ->
                onSearchRequestFailed(errorType, error)
        }
    }

    /**
     * ジャンルマスタapiへのリクエストに成功した際のリスナー
     * ジャンルの名前とコードをMapに変換し、UIを更新
     * [genreData] ジャンルの名称と、リクエストで指定する際のコードが含まれる
     */
    private fun onGenreRequestSucceed(genreData: GourmetGenre?){
        genreData?.let{data ->
            if (data.results.resultsAvailable==0){
                // 該当なし
                noGenreParameterFound()
                return
            }
            // キー:name, 値:code　としたMapを作成
            gourmetRequestQueryParams = data.results.genre?.let { genre ->
                genre.associateBy({ it.name }, { it.code })
            }!!
            displayGenreParameters(gourmetRequestQueryParams.keys.toList())
        }
    }

    /**
     * グルメサーチapiへのリクエストに成功した際のリスナー
     * NavigationでSearchingResultsFragmentへの遷移を行う
     */
    private fun onSearchRequestSucceed() {
        searchViewAutoFill = true
        //onSearchProgress(false)
        navigateToSearchResults()
    }

    /**
     * ジャンルマスタapiへのリクエストに失敗した際のリスナー
     * ダイアログの入力を確認後、ジャンルマスタapiへのリクエストを再試行
     */
    private fun onGenreRequestFailed(errorType: ErrorType, error: String) {
        Log.e(TAG, error)
        noGenreParameterFound()
        CustomDialog.create(true,
            getString(R.string.gourmet_genre_error_title),
            getString(R.string.gourmet_search_error_body),
            getString(R.string.dialog_close),
            getString(R.string.dialog_close),
            object: CustomDialog.CustomDialogListener{
                override fun onPositiveClicked(dialog: CustomDialog) {
                    viewModel.getGenreParams(this@SearchScreenFragment,
                        requireActivity(), parentFragmentManager)
                    super.onPositiveClicked(dialog)
                }
            })
            .show(parentFragmentManager, SearchScreenFragment::class.simpleName)
    }

    /**
     * グルメサーチapiへのリクエストに失敗した際のリスナー
     */
    private fun onSearchRequestFailed(errorType: ErrorType, error: String) {
        Log.e(TAG, error)
        onSearchProgress(false)
        CustomDialog.create(true,
            getString(R.string.gourmet_search_error_title),
            getString(R.string.gourmet_search_error_body),
            getString(R.string.dialog_close),
            getString(R.string.dialog_close))
            .show(parentFragmentManager, SearchScreenFragment::class.simpleName)
    }

    /*
     * ジャンルマスタapiから取得したジャンル名をExpandableListViewに表示
     * ヘッダーとして、検索範囲設定のレイアウトも追加する
     */
    private fun displayGenreParameters(genreKeys: List<String>){
        // <'Group名', List<'ジャンル名', 'ジャンルコード'>>
        val genre: Map<String,List<String>>
            = mapOf(resources.getString(R.string.param2_title1) to genreKeys)
        searchParameterExpandableAdapter = ExpandableListAdapter(requireContext(), genre)
        fragmentSearchingBinding.parameterSettings.apply{
            // ヘッダーの追加
            addHeaderView(layoutInflater.inflate(R.layout.expandable_list_view_header, null))
            setAdapter(searchParameterExpandableAdapter)
            // デフォルトで展開
            expandGroup(0)
        }
        delayedAddListener.run()

        /**
         * 検索の準備完了
         */
        isReadyForSearch = true
    }

    // ジャンルマスタapiからジャンルの取得に失敗した場合
    private fun noGenreParameterFound(){
        if (_fragmentSearchingBinding!=null)
            fragmentSearchingBinding.searchFunctionUnavailable.visibility = View.VISIBLE
    }

    // 検索結果画面へ遷移
    private fun navigateToSearchResults(){
        Navigation.findNavController(requireActivity(), R.id.nav_host_fragment)
            .navigate(R.id.action_searchScreenFragment_to_searchResultsFragment)
    }

    // キーボードを隠す
    private fun hideKeyboard(){
        val inputMethodManager = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        view?.let {
            inputMethodManager.hideSoftInputFromWindow(it.windowToken,InputMethodManager.HIDE_NOT_ALWAYS)
        }
    }

    // 検索Buttonが押されたタイミングで、progressバーに切り替える
    private fun onSearchProgress(on: Boolean){
        if (_fragmentSearchingBinding==null) return

        val progressVisibility: Int
        val searchButtonVisibility: Int
        if (on){
            progressVisibility = View.VISIBLE
            searchButtonVisibility = View.GONE
        }
        else{
            progressVisibility = View.GONE
            searchButtonVisibility = View.VISIBLE
        }
        fragmentSearchingBinding.searching.visibility = progressVisibility
        fragmentSearchingBinding.gourmetSearchButton.visibility = searchButtonVisibility
        fragmentSearchingBinding.gourmetSearchButtonIcon.visibility = searchButtonVisibility
    }
}