package jp.gourtto.layouts

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import jp.gourtto.R


/**
 * SearchScreenFragmentで検索パラメータの入力画面を格納する、ExpandableListViewのAdapter
 *
 * [dataList] 設定項目(検索範囲,ジャンル...)のタイトルと、その選択肢が格納されたMap
 *   例: Map<'ジャンルを選択', List<'中華', 'イタリアン', '和食'...>>
 * [groupPosition] -> ExpandableListViewにおける、該当設定項目GroupのIndex(0,1,2...)
 * [childPosition] -> 該当設定項目の親Groupにおける、子としてのIndex(0,1,2...)
 */
class ExpandableListAdapter(
    private val context: Context,
    private var dataList: Map<String, List<String>>
): BaseExpandableListAdapter() {
    /**
     * 選択されている設定項目の値を記録
     * Map<groupPosition, Map<childPosition, '選択肢の文字列(中華,イタリアン...)'>>
     * ユーザーに選択されるとMapに新規追加され、選択解除されるとMapから削除される
     * つまり、Group毎に現在選択されている要素の <childPosition, '選択肢の名称'> のみ格納される
     */
    private var currentState: MutableMap<Int,MutableMap<Int,String>> = mutableMapOf()


    override fun getGroupCount(): Int {
        return dataList.keys.size
    }

    // それぞれのタイトルの要素数を返す
    override fun getChildrenCount(groupPosition: Int): Int {
        val list = dataList[dataList.keys.elementAt(groupPosition)]
        return list?.size ?: 0
    }

    // groupPosition(Index)のキーを返す
    override fun getGroup(groupPosition: Int): Any {
        return dataList.keys.elementAt(groupPosition)
    }

    // groupPosition(Index)の値を返す
    override fun getChild(groupPosition: Int, childPosition: Int): Any {
        val list = dataList[dataList.keys.elementAt(groupPosition)]
        return list?.get(childPosition) ?: ""
    }

    override fun getGroupId(groupPosition: Int): Long {
        return groupPosition.toLong()
    }

    override fun getChildId(groupPosition: Int, childPosition: Int): Long {
        return childPosition.toLong()
    }

    override fun hasStableIds(): Boolean {
        return false
    }

    // 設定項目のGroupの親にあたるViewを作成
    override fun getGroupView(groupPosition: Int, isExpanded: Boolean,
                              convertview: View?, parentView: ViewGroup?): View {
        var convertView = convertview
        // Groupの追加
        if (currentState.containsKey(groupPosition).not()){
            currentState[groupPosition] = mutableMapOf()
        }

        val title = dataList.keys.elementAt(groupPosition)
        if (convertView == null) {
            val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            convertView = layoutInflater.inflate(R.layout.expandable_view_title, null)
        }
        // 設定項目のタイトル
        convertView!!.findViewById<TextView>(R.id.param_title).apply {
            text = title
        }
        // タイトル右の矢印アイコンの設定
        convertView.findViewById<ImageView>(R.id.expandable_list_view_indicator).apply {
            if (isExpanded){
                setImageResource(R.drawable.expand_less_fill0_wght400_grad0_opsz24)
            }
            else{
                setImageResource(R.drawable.expand_more_fill0_wght400_grad0_opsz24)
            }
        }
        return convertView
    }

    // 設定項目のGroupの子となるViewを作成
    override fun getChildView(groupPosition: Int, childPosition: Int, isLastChild: Boolean,
                              convertview: View?, parentView: ViewGroup?): View {
        var convertView = convertview

        val title = dataList[dataList.keys.elementAt(groupPosition)]?.get(childPosition) ?: ""

        if (convertView == null) {
            val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            convertView = layoutInflater.inflate(R.layout.expandable_view_item, null)
        }
        convertView!!.findViewById<Button>(R.id.param_toggle)
            .apply {
                // 選択状態の更新
                background = currentState[groupPosition]?.get(childPosition)?.let {
                    switchGenreSelector(true, context)
                } ?: switchGenreSelector(false, context)

                text = title
                setOnClickListener(null)
                setOnClickListener {
                    // 要素が見つかれば削除. 見つからなければ追加を行う
                    if (currentState[groupPosition]?.containsKey(childPosition) == true){
                        currentState[groupPosition]?.remove(childPosition)
                    }
                    else{
                        currentState[groupPosition]?.set(childPosition, title)
                    }
                    background = currentState[groupPosition]?.get(childPosition)?.let {
                        switchGenreSelector(true, context)
                    } ?: switchGenreSelector(false, context)
                }
            }
        return convertView
    }

    // 設定項目のGroupの子がタップ可能であればtrueを返す
    override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean {
        return true
    }

    // onであれば選択状態、on.not()であれば選択解除状態のButton背景に切り替える
    private fun switchGenreSelector(on: Boolean, context: Context): Drawable? {
        return if(on){
            AppCompatResources.getDrawable(context, R.drawable.genre_selector_positive)
        }
        else{
            AppCompatResources.getDrawable(context, R.drawable.genre_selector_negative)
        }
    }

    // 現在選択されているジャンル名を返す
    fun returnSelectedGenres(): MutableMap<Int, String>? {
        return currentState[0]
    }
}