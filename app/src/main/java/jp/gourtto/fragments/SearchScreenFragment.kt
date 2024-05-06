package jp.gourtto.fragments

import android.os.Bundle
import android.transition.TransitionInflater
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import jp.gourtto.R
import jp.gourtto.databinding.FragmentSearchScreenBinding
import jp.gourtto.gourmet_api.GourmetApiRequestListener
import jp.gourtto.gourmet_api.Gourmet
import jp.gourtto.gourmet_api.GourmetGenre
import jp.gourtto.gourmet_api.DataShareViewModel
import jp.gourtto.layouts.CustomDialog
import jp.gourtto.layouts.ExpandableListAdapter


/**
 * 検索画面のViewを作成するFragment
 */
class SearchScreenFragment : Fragment(), GourmetApiRequestListener {

    companion object {
        // グルメサーチApiからの最大レスポンス件数
        private const val MAX_OUTPUT_COUNT = 100

        private val TAG = SearchScreenFragment::class.java.simpleName
    }

    private var _fragmentSearchingBinding: FragmentSearchScreenBinding? = null
    private val fragmentSearchingBinding
        get() = _fragmentSearchingBinding!!

    // 検索条件を指定する画面のExpandableListViewAdapter
    private lateinit var myAdapter: ExpandableListAdapter

    private val viewModel: DataShareViewModel by lazy {
        ViewModelProvider(requireActivity())[DataShareViewModel::class.java]
    }

    /*
     * グルメサーチApiリクエストのクエリパラメータとして、retrofitに渡す値
     * 検索条件を <Key, Value> の形で格納
     */
    private lateinit var genreMap: Map<String,String>


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
        // UIのリスナーを設定
        addUiListeners()
        // ジャンルマスタapiへリクエストを実行し、ジャンル一覧を取得
        viewModel.getGenreParams(this)
    }

    override fun onDestroyView(){
        super.onDestroyView()
        // UIのリスナーを破棄
        detachUiListeners()
        _fragmentSearchingBinding = null
    }

    // Uiのリスナーを追加
    private fun addUiListeners(){
        fragmentSearchingBinding.searchingButton.setOnClickListener {
            onSeachingButtonClicked() }
    }

    // Uiのリスナーを破棄
    private fun detachUiListeners(){
        if (_fragmentSearchingBinding==null) return
        fragmentSearchingBinding.searchingButton.setOnClickListener(null)
    }

    /*
     * 検索ボタンが押された際のリスナー
     * 各種設定項目の値を取得し、クエリパラメータMap<Key,Value>に変換後、グルメApiServiceを呼び出す
     */
    private fun onSeachingButtonClicked(){

        val queryParameter: MutableMap<String, String> = mutableMapOf()

        // 検索範囲パラメータの追加
        queryParameter["range"] = getSearchingRadius().toString()
        // ジャンルパラメータの追加
        for (code in getSelectedGenres()){
            queryParameter["genre"] = code
        }
        // 最大検索件数の設定
        queryParameter["count"] = MAX_OUTPUT_COUNT.toString()
        // リクエスト送信
        viewModel.getSearchResults(this, queryParameter, null)
    }

    /**
     * 検索範囲のRadioButtonから選択されている要素を取得
     * apiの検索範囲パラメータは300m, 500m, 1000m, 2000m, 3000m の順に、(int) 1, 2, 3, 4, 5 が対応するので、
     *   戻り値は index + 1 の値となる
     * ExpandableListViewにヘッダーとして後から追加しているため、
     *   viewBindingは使用できない (layout/expandable_list_view_header.xml)
     */
    private fun getSearchingRadius(): Int{
        var buttonIndex: Int = 0
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
     * ジャンルマスターapiの結果を格納した[genreMap]を参照し、クエリパラメータ用のコードを取得する
     * 戻り値[params]には、お店ジャンルコード が文字列で格納されている
     */
    private fun getSelectedGenres():List<String>{
        val params: MutableList<String> = mutableListOf()
        myAdapter.returnSelectedGenres()?.let{ indexes ->
            for (index in indexes){
                genreMap[index.value]?.let { params.add(it) }
            }
        }
        return params
    }

    /**
     * ジャンルマスタapiへのリクエストに成功し、ジャンル名一覧を取得した際のリスナー
     */
    override fun onRequestSucceed(data: GourmetGenre) {
        if (data.results.resultsAvailable==0){
            // 該当なし
            displayNoGenreParameter()
            return
        }
        // キー:name, 値:code　としたMapを作成
        genreMap = data.results.genre?.let { genre ->
            genre.associateBy({ it.name }, { it.code })
        }!!
        displayGenreParameters(genreMap.keys.toList())
    }

    /**
     * ジャンルマスタapiへのリクエストに失敗した際のリスナー
     */
    override fun onRequestFailed(error: String, data: GourmetGenre?) {
        Log.e(TAG, error)
        displayNoGenreParameter()
    }

    /**
     * グルメサーチapiへのリクエストに成功した際のリスナー
     * レスポンス結果はviewModelが保持
     * ここでは、NavigationでSearchingResultsFragmentへの遷移を行うだけなので、
     *   [data]は null を受け取る
     */
    override fun onRequestSucceed(data: Gourmet?) {
        navigateToSearchResults()
    }

    /**
     * グルメサーチapiへのリクエストに失敗した際のリスナー
     */
    override fun onRequestFailed(error: String, data: Gourmet?) {
        Log.e(TAG, error)
        CustomDialog.create(true,
            "検索に失敗しました",
            "時間をおいて再度お試しください",
            "閉じる",
            "閉じる")
    }

    /*
     * ジャンルマスタapiから取得したジャンル名をExpandableListViewに表示
     * ヘッダーとして、検索範囲設定のレイアウトも追加する
     */
    private fun displayGenreParameters(genreKeys: List<String>){
        // <'Group名', List<'ジャンル名', 'ジャンルコード'>>
        val genre: Map<String,List<String>>
            = mapOf(resources.getString(R.string.param2_title1) to genreKeys)
        myAdapter = ExpandableListAdapter(requireContext(), genre)
        fragmentSearchingBinding.expandableview.apply{
            // ヘッダーの追加
            addHeaderView(layoutInflater.inflate(R.layout.expandable_list_view_header, null))
            setAdapter(myAdapter)
        }
    }

    // ジャンルマスタapiからジャンルの取得に失敗した場合
    private fun displayNoGenreParameter(){
        CustomDialog.create(true,
            "ジャンル情報の取得に失敗しました",
            "",
            "閉じる",
            "閉じる")
    }

    // 検索結果画面へ遷移
    private fun navigateToSearchResults(){
        Navigation.findNavController(requireActivity(), R.id.nav_host_fragment)
            .navigate(R.id.action_searchScreenFragment_to_searchResultsFragment)
    }
}