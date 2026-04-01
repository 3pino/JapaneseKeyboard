package com.kazumaproject.custom_keyboard.data

import android.content.Context
import com.kazumaproject.custom_keyboard.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class DisplayAction(
    val action: KeyAction,
    val displayName: String,
    val iconResId: Int? = null // アイコンがない場合はnull
)

object KeyActionMapper {

    private val gson = Gson()

    /**
     * Generates a list of DisplayAction objects using localized strings.
     * @param context The context needed to access string resources.
     * @return A list of DisplayAction objects.
     */
    fun getDisplayActions(context: Context): List<DisplayAction> {
        return getDisplayActionsWithCustom(context, emptyList())
    }

    /**
     * Extended version that can include dynamic switch actions for custom keyboards.
     * @param customKeyboards List of Pair(id, name)
     */
    fun getDisplayActionsWithCustom(
        context: Context,
        customKeyboards: List<Pair<String, String>>
    ): List<DisplayAction> {
        val actions = mutableListOf(
            DisplayAction(
                KeyAction.Delete,
                context.getString(R.string.action_delete),
                com.kazumaproject.core.R.drawable.backspace_24px
            ),
            DisplayAction(
                KeyAction.DeleteUntilSymbol,
                context.getString(R.string.action_delete_until_symbol),
                com.kazumaproject.core.R.drawable.backspace_24px_until_symbol
            ),
            DisplayAction(
                KeyAction.Space,
                context.getString(R.string.action_space),
                com.kazumaproject.core.R.drawable.baseline_space_bar_24
            ),
            DisplayAction(
                KeyAction.Convert,
                context.getString(R.string.action_convert),
                com.kazumaproject.core.R.drawable.henkan
            ),
            DisplayAction(
                KeyAction.Enter,
                context.getString(R.string.action_enter),
                com.kazumaproject.core.R.drawable.baseline_keyboard_return_24
            ),
            DisplayAction(KeyAction.NewLine, context.getString(R.string.action_new_line)),
            DisplayAction(
                KeyAction.Paste,
                context.getString(R.string.action_paste),
                com.kazumaproject.core.R.drawable.content_paste_24px
            ),
            DisplayAction(
                KeyAction.Copy,
                context.getString(R.string.action_copy),
                com.kazumaproject.core.R.drawable.content_copy_24dp
            ),
            DisplayAction(
                KeyAction.SwitchAction("next_ime"),
                context.getString(R.string.action_switch_to_next_ime),
                com.kazumaproject.core.R.drawable.language_24dp
            ),
            DisplayAction(
                KeyAction.SwitchAction("emoji"),
                context.getString(R.string.action_show_emoji_keyboard),
                com.kazumaproject.core.R.drawable.baseline_emoji_emotions_24
            ),
            DisplayAction(
                KeyAction.ToggleDakuten,
                context.getString(R.string.action_toggle_dakuten),
                com.kazumaproject.core.R.drawable.kana_small
            ),
            DisplayAction(
                KeyAction.ToggleCase,
                context.getString(R.string.action_toggle_case),
                com.kazumaproject.core.R.drawable.english_small
            ),
            DisplayAction(
                KeyAction.ShiftKey,
                context.getString(R.string.action_shift_key),
                com.kazumaproject.core.R.drawable.shift_24px
            ),
            DisplayAction(
                KeyAction.SwitchAction("next_custom"),
                context.getString(R.string.action_move_custom_keyboard_tab),
                com.kazumaproject.core.R.drawable.keyboard_command_key_24px
            ),
            DisplayAction(
                KeyAction.MoveCursorLeft,
                context.getString(R.string.action_move_cursor_left),
                com.kazumaproject.core.R.drawable.baseline_arrow_left_24
            ),
            DisplayAction(
                KeyAction.MoveCursorRight,
                context.getString(R.string.action_move_cursor_right),
                com.kazumaproject.core.R.drawable.baseline_arrow_right_24
            ),
            DisplayAction(
                KeyAction.SelectAll,
                context.getString(R.string.action_select_all),
                com.kazumaproject.core.R.drawable.text_select_start_24dp
            ),
            DisplayAction(
                KeyAction.SwitchAction("qwerty"),
                context.getString(R.string.switch_qwerty),
                com.kazumaproject.core.R.drawable.input_mode_english_custom
            ),
            DisplayAction(
                KeyAction.SwitchAction("number"),
                context.getString(R.string.switch_number),
                com.kazumaproject.core.R.drawable.input_mode_number_select_custom
            ),
            DisplayAction(
                KeyAction.ToggleKatakana,
                "カタカナ",
                com.kazumaproject.core.R.drawable.katakana
            ),
            DisplayAction(
                KeyAction.VoiceInput,
                context.getString(R.string.voice_input),
                com.kazumaproject.core.R.drawable.settings_voice_24px
            )
        )

        // Add dynamic custom keyboard switch actions
        customKeyboards.forEach { (id, name) ->
            actions.add(
                DisplayAction(
                    KeyAction.SwitchAction("custom", mapOf("target_id" to id)),
                    "Switch to: $name",
                    com.kazumaproject.core.R.drawable.keyboard_command_key_24px
                )
            )
        }

        return actions
    }

    // KeyActionオブジェクトをDB保存用の文字列に変換
    fun fromKeyAction(keyAction: KeyAction?): String? {
        if (keyAction is KeyAction.SwitchAction) {
            val map = mutableMapOf<String, Any>()
            map["action"] = "switch"
            map["type"] = keyAction.actionType
            map["params"] = keyAction.params
            return gson.toJson(map)
        }
        return when (keyAction) {
            is KeyAction.Delete -> "Delete"
            is KeyAction.Backspace -> "Backspace"
            is KeyAction.Space -> "Space"
            is KeyAction.NewLine -> "NewLine"
            is KeyAction.Enter -> "Enter"
            is KeyAction.Convert -> "Convert"
            is KeyAction.Confirm -> "Confirm"
            is KeyAction.MoveCursorLeft -> "MoveCursorLeft"
            is KeyAction.MoveCursorRight -> "MoveCursorRight"
            is KeyAction.SelectLeft -> "SelectLeft"
            is KeyAction.SelectRight -> "SelectRight"
            is KeyAction.SelectAll -> "SelectAll"
            is KeyAction.Paste -> "Paste"
            is KeyAction.Copy -> "Copy"
            is KeyAction.ChangeInputMode -> "ChangeInputMode"
            is KeyAction.ShowEmojiKeyboard -> "^_^"
            is KeyAction.SwitchToNextIme -> "SwitchToNextIme"
            is KeyAction.ToggleDakuten -> "小゛゜"
            is KeyAction.ToggleCase -> "a/A"
            is KeyAction.SwitchToKanaLayout -> "SwitchToKana"
            is KeyAction.SwitchToEnglishLayout -> "SwitchToEnglish"
            is KeyAction.SwitchToNumberLayout -> "SwitchToNumber"
            is KeyAction.ShiftKey -> "ShiftKeyPressed"
            is KeyAction.MoveCustomKeyboardTab -> "MoveCustomKeyboardTab"
            is KeyAction.DeleteUntilSymbol -> "DeleteUntilSymbol"
            is KeyAction.ToggleKatakana -> "SwitchKatakana"
            is KeyAction.VoiceInput -> "VoiceInput"
            else -> null
        }
    }

    // DBから読み込んだ文字列をKeyActionオブジェクトに変換
    fun toKeyAction(actionString: String?): KeyAction? {
        if (actionString == null) return null
        if (actionString.startsWith("{")) {
            return try {
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val map: Map<String, Any> = gson.fromJson(actionString, type)
                if (map["action"] == "switch") {
                    val actionType = map["type"] as String
                    @Suppress("UNCHECKED_CAST")
                    val params = map["params"] as? Map<String, String> ?: emptyMap()
                    KeyAction.SwitchAction(actionType, params)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
        return when (actionString) {
            "Delete" -> KeyAction.Delete
            "Backspace" -> KeyAction.Backspace
            "Space" -> KeyAction.Space
            "NewLine" -> KeyAction.NewLine
            "Enter" -> KeyAction.Enter
            "Convert" -> KeyAction.Convert
            "Confirm" -> KeyAction.Confirm
            "MoveCursorLeft" -> KeyAction.MoveCursorLeft
            "MoveCursorRight" -> KeyAction.MoveCursorRight
            "SelectLeft" -> KeyAction.SelectLeft
            "SelectRight" -> KeyAction.SelectRight
            "SelectAll" -> KeyAction.SelectAll
            "Paste" -> KeyAction.Paste
            "Copy" -> KeyAction.Copy
            "ChangeInputMode" -> KeyAction.ChangeInputMode
            "^_^" -> KeyAction.ShowEmojiKeyboard
            "SwitchToNextIme" -> KeyAction.SwitchToNextIme
            "小゛゜" -> KeyAction.ToggleDakuten
            "a/A" -> KeyAction.ToggleCase
            "SwitchToKana" -> KeyAction.SwitchToKanaLayout
            "SwitchToEnglish" -> KeyAction.SwitchToEnglishLayout
            "SwitchToNumber" -> KeyAction.SwitchToNumberLayout
            "ShiftKeyPressed" -> KeyAction.ShiftKey
            "MoveCustomKeyboardTab" -> KeyAction.MoveCustomKeyboardTab
            "DeleteUntilSymbol" -> KeyAction.DeleteUntilSymbol
            "SwitchKatakana" -> KeyAction.ToggleKatakana
            "VoiceInput" -> KeyAction.VoiceInput
            else -> null
        }
    }

}
