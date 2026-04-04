# Sumire Codebase Map

このファイルは、プロジェクトの主要な機能がどこに実装されているかをまとめたガイドです。

## 核心部 (Core Logic)

- **IMEサービスのエントリポイント**: [IMEService.kt](app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt)
  - Androidの `InputMethodService` を継承した、キーボードのメインクラスです。
- **かな漢字変換エンジン**: [KanaKanjiEngine.kt](app/src/main/java/com/kazumaproject/markdownhelperkeyboard/converter/engine/KanaKanjiEngine.kt)
  - Mozcベースの変換処理のコアロジックです。
- **AIエンジン (Zenz)**: [zenz/ module](zenz/src/main/java/com/kazumaproject/zenz/ZenzEngine.kt)
  - LLMや外部ライブラリを使用した、高度な生成・予測機能（実装中または実験的機能の可能性）。
- **共通ドメイン・ロジック**: [core/ module](core/src/main/java/com/kazumaproject/core/)
  - フリック操作の判定、キー定義、拡張関数などの共通部品が含まれています。
- **レイアウトユーティリティ**: [flexbox/ module](flexbox/src/main/java/com/kazumaproject/android/flexbox/)
  - FlexboxLayoutのカスタム実装。

## キーボードレイアウト (Keyboard Layouts)

- **12キー (テンキー)**: [tenkey/ module](tenkey/src/main/java/com/kazumaproject/tenkey/TenKey.kt)
  - 一般的なスマホのフリック入力UI。
- **QWERTYキーボード**: [qwerty_keyboard/ module](qwerty_keyboard/src/main/java/com/kazumaproject/qwerty_keyboard/ui/QWERTYKeyboardView.kt)
  - PCライクな配列のUI。
- **記号キーボード**: [symbol_keyboard/ module](symbol_keyboard/src/main/java/com/kazumaproject/symbol_keyboard/)
- **カスタムキーボード**: [custom_keyboard/ module](custom_keyboard/src/main/java/com/kazumaproject/custom_keyboard/view/FlickKeyboardView.kt)
  - ユーザーが定義可能なキー配列のUI。

## 主要機能 (Features)

- **ユーザー辞書**: [app/.../user_dictionary/](app/src/main/java/com/kazumaproject/markdownhelperkeyboard/user_dictionary/)
- **定型文 (スニペット)**: [app/.../user_template/](app/src/main/java/com/kazumaproject/markdownhelperkeyboard/user_template/)
- **クリップボード履歴**: [app/.../clipboard_history/](app/src/main/java/com/kazumaproject/markdownhelperkeyboard/clipboard_history/)
- **学習機能**: [app/.../learning/](app/src/main/java/com/kazumaproject/markdownhelperkeyboard/learning/)
- **設定画面**: [app/.../setting_activity/MainActivity.kt](app/src/main/java/com/kazumaproject/markdownhelperkeyboard/setting_activity/MainActivity.kt)

## リソース (Resources)

- **レイアウトXML**: `app/src/main/res/layout/`
- **辞書データ (Raw)**: `app/src/main/res/raw/` (mozcの辞書データなど)
