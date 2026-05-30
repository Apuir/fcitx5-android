package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.ViewGroup
import androidx.core.text.isDigitsOnly
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.keyboard.ColumnKeyView.VH
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Border
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Variant
import org.fcitx.fcitx5.android.utils.T9PinYin
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.leftOfParent
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import timber.log.Timber
import kotlin.collections.ifEmpty
import kotlin.collections.sortedBy

@SuppressLint("ViewConstructor")
open class ColumnKeyboard(
    context: Context,
    theme: Theme,
    private val sideLayoutKeyColumnNum: Int,
    private val sideLayoutKeyColumnShowNum: Int,
    sideLayoutKey: KeyDef,
    private val layout: List<List<KeyDef>>
) : BaseKeyboard(context, theme, layout) {

    protected val sideLayoutKeyView: ColumnKeyView = createKeyView(sideLayoutKey) as ColumnKeyView

    protected val sideLayoutKeyAppearance = sideLayoutKey.appearance as KeyDef.Appearance.Column

    private val fcitx = FcitxDaemon.connect(javaClass.name)

    //已选择的拼音队列
    private val selectedQueue = ArrayDeque<KeyAction.SelectPinYinAction>()

    //输入内容的队列
    private val inputQueue = ArrayDeque<String>()

    //键盘核心行为队列
    private val behaviorQueue = ArrayDeque<KeyboardBehavior>()

    // variables
    private var columnAdapter: ColumnAdapter? = null

    //当前输入的preedit
    private var composingPreedit: String = ""

    private var sideColumnItems: List<KeyDef>  = emptyList()

    companion object {
        //分隔符（选择拼音的时候用的分隔符）
        const val segmentKeyChar = '\''

        //分隔符别称（选择拼音的时候用的分隔符）
        const val segmentKeyCharAlias = '1'

        val backspaceKeySym = KeySym(FcitxKeyMapping.FcitxKey_BackSpace)

        val returnKeySym = KeySym(FcitxKeyMapping.FcitxKey_Return)
    }

    init {
        fcitx.lifecycleScope.launch {
            fcitx.runImmediately { eventFlow }.collect { handleFcitxEvent(it) }
        }
        post {

            columnAdapter = ColumnAdapter(
                context, theme, sideColumnItems
            ) { action -> this@ColumnKeyboard.onAction(action) }

            sideLayoutKeyView.adapter = columnAdapter
            sideLayoutKeyView.id = generateViewId()

            add(sideLayoutKeyView, lParams {
                id = sideLayoutKeyView.id
                topOfParent()
                leftOfParent()
                matchConstraintPercentWidth = sideLayoutKeyAppearance.percentWidth
            })

            updateSideLayoutHeight()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateSideLayoutHeight() {
        val rowCount = layout.size
        if (rowCount == 0 || height == 0) return
        val rowHeight = (height / rowCount)
        val finalHeight = rowHeight * sideLayoutKeyColumnNum
        sideLayoutKeyView.layoutParams = sideLayoutKeyView.layoutParams.apply {
            height = finalHeight
        }
        val rate = (sideLayoutKeyColumnNum.toFloat() / sideLayoutKeyColumnShowNum.toFloat())
        sideLayoutKeyView.updateItemHeight(
            (rowHeight.toFloat() * rate).toInt()
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post {
            updateSideLayoutHeight()
        }
    }

    protected fun handleFcitxEvent(event: FcitxEvent<*>) {
        when (event) {
            is FcitxEvent.SelectCandidateEvent -> {
                behaviorQueue.add(KeyboardBehavior.SELECT_CANDIDATE)
            }
            is FcitxEvent.InputPanelEvent -> {
                composingPreedit = event.data.preedit.toString()
                fcitx.lifecycleScope.launch {
                    fcitx.runIfReady {
                        val input = getRimeInput()
                        if (input.isEmpty()) {
                            behaviorQueue.clear()
                            inputQueue.clear()
                            selectedQueue.clear()
                        }
                        val position = getRimeInputConfirmPosition()
                        val keys = buildPossibleCombinations(input, position)
                        withContext(Dispatchers.Main) {
                            sideLayoutKeyView.adapter?.updateItems(keys.ifEmpty {
                                sideColumnItems
                            })
                            sideLayoutKeyView.resetPosition()
                        }
                    }
                }
            }
            else -> {}
        }
    }

    fun updateSideBarItems(newItems: List<KeyDef>) {
        sideColumnItems = newItems
        sideLayoutKeyView.adapter?.updateItems(newItems)
        sideLayoutKeyView.resetPosition()
    }

    override fun onAction(action: KeyAction, source: KeyActionListener.Source) {
        var behavior = KeyboardBehavior.NONE
        when (action) {
            is KeyAction.SelectPinYinAction -> {
                behavior = KeyboardBehavior.SELECT_PINYIN
                selectedQueue.add(action)
                updateRimeInput()
            }
            is KeyAction.FcitxKeyAction -> {
                var act = action.act
                behavior = KeyboardBehavior.NORMAL
                if (act == segmentKeyChar.toString() || act == segmentKeyCharAlias.toString()) {
                    behavior = KeyboardBehavior.SEGMENT
                    act = segmentKeyChar.toString()
                    if (recontrolSegment()) {
                        return
                    }
                }
                inputQueue.add(act)
            }
            is KeyAction.SymAction -> {
                if (action.sym == backspaceKeySym && recontrolBackspace()) {
                    updateRimeInput()
                    return
                }
                if (action.sym == returnKeySym && composingPreedit.isNotEmpty()) {
                    super.onAction(KeyAction.DirectCommitAction(composingPreedit), source)
                    return
                }
            }
            else -> {}
        }
        if (behavior != KeyboardBehavior.NONE) {
            behaviorQueue.add(behavior)
        }
        super.onAction(action, source)
    }

    fun recontrolBackspace(): Boolean {
        if (behaviorQueue.isEmpty()) {
            return false
        }
        when (behaviorQueue.removeLast()) {
            KeyboardBehavior.SELECT_PINYIN -> {
                if (selectedQueue.isNotEmpty()) {
                    selectedQueue.removeLast()
                    return true
                }
            }
            KeyboardBehavior.SELECT_CANDIDATE -> {
                return false
            }
            else -> {
                inputQueue.removeLast()
            }
        }
        return false
    }

    fun recontrolSegment(): Boolean {
        if (inputQueue.isEmpty()) {
            return true
        }
        if (inputQueue.last() == segmentKeyChar.toString()) {
            return true
        }
        var selectedSize = 0
        selectedQueue.forEach { selectedSize += it.pinYin.length }
        return selectedSize == inputQueue.size
    }

    fun updateRimeInput() {
        fcitx.lifecycleScope.launch {
            fcitx.runIfReady {
                setRimeInput(buildRimeInput())
            }
        }
    }

    fun buildPossibleCombinations(currentInput: String, confirmedLen: Int): List<KeyDef> {
        if (inputQueue.isNotEmpty()) {
            val keys = mutableListOf<KeyDef>()
            //因为手动选择拼音插入分割符的缘故，此处需要先修正已确认的内容长度
            var len = confirmedLen
            var index = 0
            if (len > 0) {
                currentInput.forEachIndexed { i, char ->
                    if (i >= confirmedLen) {
                        return@forEachIndexed
                    }
                    val raw = inputQueue.getOrNull(index).toString()
                    if (char == segmentKeyChar && segmentKeyChar.toString() != raw) {
                        len -= 1
                        index += 1
                    }
                    index += 1
                }
            }
            val position = nextSequencePosition(len)
            if (position < 0) {
                return emptyList()
            }
            val sequence = inputQueue.joinToString("").substring(position)
            T9PinYin.possibleCombinations(sequence).forEach { pinYin ->
                var raw = sequence.substring(0, pinYin.length)
                //如果候选拼音以分词标记结束必须
                if (segmentKeyChar == sequence.getOrNull(pinYin.length)) {
                    raw += segmentKeyChar.toString()
                }
                keys.add(PinYinCandidateKey(position, raw, pinYin))
            }
            return keys
        }
        return emptyList()
    }

    fun nextSequencePosition(
        confirmedLen: Int,
    ): Int {
        val inputSize = inputQueue.size
        val ranges =
            selectedQueue.map { it.pos until (it.pos + it.raw.length) }.sortedBy { it.first }
        // ❗1. 合法性检查：confirmedLen 不能在任何区间内部
        for (r in ranges) {
            for (r in ranges) {
                if (confirmedLen > r.first && confirmedLen < r.last + 1) {
                    return -1
                }
            }
        }
        // ❗2. 从 confirmedLen 开始找 next free position
        var pos = confirmedLen.coerceIn(0, inputSize)
        while (pos < inputSize) {
            var jumped = false
            for (r in ranges) {
                if (pos in r) {
                    // 如果 confirmedLen 或当前位置落在已 token 区间，
                    // 直接跳到区间末尾
                    pos = r.last + 1
                    jumped = true
                    break
                }
            }
            if (!jumped) return pos
        }
        return inputSize
    }

    fun buildRimeInput(): String {
        val input = inputQueue.joinToString("")
        if (selectedQueue.isEmpty()) return input
        val first = selectedQueue.first()
        val last = selectedQueue.last()
        val start = first.pos
        val end = last.pos + last.raw.length
        if (start < 0 || end > input.length) return input
        val result = StringBuilder().append(input.substring(0, start))
        var cursor = start
        for (action in selectedQueue) {
            if (action.pos > cursor) {
                result.append(input.substring(cursor, action.pos))
            }
            val rawEnd = action.pos + action.raw.length
            if (rawEnd <= input.length && input.regionMatches(
                    action.pos, action.raw, 0, action.raw.length
                )
            ) {
                result.append(action.pinYin)
                result.append(segmentKeyChar)
            } else {
                result.append(input.substring(action.pos, rawEnd))
            }
            cursor = rawEnd
        }
        return result.append(input.substring(end)).toString()
    }


    class ColumnAdapter(
        private val ctx: Context,
        private val theme: Theme,
        items: List<KeyDef>,
        private val onActionCallback: (KeyAction) -> Unit
    ) : RecyclerView.Adapter<VH>() {

        private val items = mutableListOf<KeyDef>().apply { addAll(items) }
        var itemHeight: Int = 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val keyDef = items[viewType]
            val view = when (keyDef.appearance) {
                is KeyDef.Appearance.Text -> {
                    keyDef.appearance.apply { border = Border.Off }
                    TextKeyView(ctx, theme, keyDef.appearance)
                }
                else -> error("Unsupported Column child")
            }
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val keyDef = items[position]
            val view = holder.itemView
            view.layoutParams = RecyclerView.LayoutParams(
                LayoutParams.MATCH_PARENT,
                itemHeight,
            )
            when {
                view is TextKeyView && keyDef.appearance is KeyDef.Appearance.Text -> {
                    val appearance = keyDef.appearance
                    view.mainText.apply {
                        background = null
                        text = appearance.displayText
                        setTextSize(TypedValue.COMPLEX_UNIT_DIP, appearance.textSize)
                        textDirection = TEXT_DIRECTION_FIRST_STRONG_LTR
                        setTypeface(typeface, appearance.textStyle)
                        setTextColor(
                            when (appearance.variant) {
                                Variant.Normal -> theme.keyTextColor
                                Variant.AltForeground, Variant.Alternative -> theme.altKeyTextColor
                                Variant.Accent -> theme.accentKeyTextColor
                            }
                        )
                    }
                }
                else -> error("Unsupported Column")
            }
            holder.itemView.setOnClickListener(null)
            keyDef.behaviors.forEach { behavior ->
                if (behavior is KeyDef.Behavior.Press) {
                    holder.itemView.setOnClickListener {
                        onActionCallback(behavior.action)
                    }
                }
            }
        }

        override fun getItemCount(): Int = items.size
        override fun getItemViewType(position: Int): Int = position

        @SuppressLint("NotifyDataSetChanged")
        fun updateItems(newItems: List<KeyDef>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }
    }

    enum class KeyboardBehavior() {
        //没有任何行为
        NONE,

        //普通的输入
        NORMAL,

        //分词
        SEGMENT,

        //选择候选拼音
        SELECT_PINYIN,

        //选择了候选词
        SELECT_CANDIDATE,
    }
}