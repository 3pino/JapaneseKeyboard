## 実装プラン

CROSS_FLICK と STANDARD_FLICK を統合し、保守性を高める。
旧STANDARD_FLICK特有のポップアップは、enum class PopupStyle { CIRCLE } という別の概念で生かす
KeyType.STANDARD_FLICK は最終的にレイアウト定義から全廃し、CROSS_FLICK + popupStyle=CIRCLE に完全移行する

### Phase 1: データモデル追加

**`KeyModels.kt`**
- `PopupStyle` enum を追加
  ```kotlin
  enum class PopupStyle { GRID, CIRCLE }
  ```
- `KeyData` に `popupStyle: PopupStyle = PopupStyle.GRID` フィールド追加
なお、 CROSS_FLICK が GRID に、 STANDARD_FLICK が CIRCLE に相当する。

### Phase 2: `GridFlickInputController` の拡張

- `SegmentedBackgroundDrawable` をオプションで受け取るように `attach()` を変更
- `ACTION_MOVE` / `ACTION_UP` 時に `highlightDirection` を更新
- `flickSensitivity` は既にコンストラクタ引数にあるのでそのまま
- リスナーの `onPress` / `onFlick` はすでに `FlickAction` ベースなので変更不要
- `calculateDirection` の実装を、 `StandardFlickInputController` で使われているものに置き換える

### Phase 3: `StandardFlickInputController` のリファクタリング

- `GridFlickInputController` を継承する形に書き換え
- ポップアップ部分だけ `StandardFlickPopupView` を使うようにオーバーライド
- `String` ベースのリスナーを削除、`FlickAction` ベースに統一
- `flickThreshold = 65f` を削除、`flickSensitivity` を使用

### Phase 4: `FlickKeyboardView` の変更

- `KeyType.STANDARD_FLICK` ブランチを `CROSS_FLICK` に合流
- `keyData.popupStyle` を見てコントローラーを選択（`GridFlickInputController` or `StandardFlickInputController`）

### Phase 5: レイアウト定義の更新

- `KeyboardDefaultLayouts.kt` で `STANDARD_FLICK` だったキーに `popupStyle = PopupStyle.CIRCLE` を付与
- `keyType` は `CROSS_FLICK` に変更
