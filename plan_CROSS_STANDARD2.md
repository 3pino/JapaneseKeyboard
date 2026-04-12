## 実装プラン

CROSS_FLICK / STANDARD_FLICK 統合の本来目的（Standardコントローラーの縮小）を達成する。
StandardFlickInputController は「CIRCLEポップアップ差分のみ」を担当し、入力判定・イベント発火・方向正規化は GridFlickInputController に一本化する。
重複ロジックと互換分岐の散在を減らし、保守コストを下げる。

### Phase 1: `GridFlickInputController` の共通基盤化

**`custom_keyboard/controller/GridFlickInputController.kt`**
- タッチ処理の中核（ACTION_DOWN / MOVE / UP の判定、listener 通知、highlight 更新）を `protected` な共通フローへ整理
- 子クラス差分をフックメソッド化
  - 例: `showPopup(direction)` / `dismissPopup()` / `isLongPressEnabled`
- `calculateDirection` を共通実装として1箇所に固定し、子クラスで再実装しない構造にする
- 方向マップ補完（`UP_LEFT/UP_RIGHT` → `*_FAR`）と `hasContent` 判定を共通ヘルパー化する

### Phase 2: `StandardFlickInputController` の薄型化

**`custom_keyboard/controller/StandardFlickInputController.kt`**
- `handleTouchEvent` / `calculateDirection` / `normalizeActionMap` / `hasContent` の重複実装を削除
- `StandardFlickPopupView` 固有の表示処理のみを実装（CIRCLE 表示・テキスト整形）
- 長押しグリッド挙動は無効化フラグで明示し、Grid 側ロジックに依存して制御する
- `cancel()` は Standard ポップアップの後始末のみ追加し、共通後始末は `super.cancel()` に委譲する

### Phase 3: `FlickKeyboardView` のコントローラー管理整理

**`custom_keyboard/view/FlickKeyboardView.kt`**
- `standardFlickControllers` と `gridFlickControllers` の二重管理を整理
  - `StandardFlickInputController` は `GridFlickInputController` として一元管理できる構造へ移行
- `detach` / `onDetachedFromWindow` / cursor mode 終了時の popup dismiss 経路を共通化
- CROSS_FLICK 分岐内の設定コード重複（listener / theme / attach）を共通関数へ抽出する

### Phase 4: 方向正規化と互換処理の責務統一

**`custom_keyboard/view/FlickKeyboardView.kt`**
**`custom_keyboard/controller/GridFlickInputController.kt`**
- `normalizeDirectionsForCrossFlick` と Standard 側補完ロジックの重複を1箇所へ統合
- 互換レイヤ（`KeyType.STANDARD_FLICK` を CIRCLE 扱い）は入口で解決し、描画・制御側での特例分岐を減らす

### Phase 5: 互換性確認と副作用チェック

**対象: `custom_keyboard` / `app`**
- GRID / CIRCLE の両ポップアップで `onPress` / `onFlick` / `isFlick` が従来どおり動くことを確認
- 長押し系イベント（`onLongPress` / `onUpAfterLongPress`）が GRID のみで発火することを確認
- 既存DB由来の `STANDARD_FLICK` データが `CROSS_FLICK + popupStyle=CIRCLE` として問題なく表示・編集できることを確認
