# 実装プラン

CROSS_FLICK と CIRCULAR_FLICK を統合し、キー種別を `CROSS_FLICK` に一本化する。
CIRCULAR 由来の挙動（角度カスタム・5方向・ウィンドウ倍率）は維持しつつ、実装構造は STANDARD 寄せで整理する。

## 統合方針（採用）

- 旧 `KeyType.CIRCULAR_FLICK` は互換レイヤーに寄せ、実運用は `CROSS_FLICK` へ統一する。
- 見た目/操作差分は keyType ではなく `popupStyle` で表現する。
- `sumire` スタイルは `CROSS_FLICK + popupStyle=DONUT(仮称)` として扱う。
- イベント通知は `FlickAction` ベースに寄せ、`String` ベース経路を縮小する。

## Phase 1: データモデルと互換レイヤー

**`custom_keyboard/data/KeyModels.kt`**
- `PopupStyle` を `GRID / CIRCLE / DONUT` へ拡張。
- `KeyType.CIRCULAR_FLICK` は即削除せず、`@Deprecated` 化して読み取り互換に残す。
- `resolvePopupStyle` 系の互換ルールに「CIRCULAR => DONUT」を追加。

**`app/.../repository/KeyboardRepository.kt`**
- DB 読み込み時: `CIRCULAR_FLICK` を `CROSS_FLICK` に正規化。
- popupStyle 解決時: CIRCULAR 由来キーを `DONUT` として復元。
- 保存時: `CIRCULAR_FLICK` を書き込まない（常に `CROSS_FLICK`）。

## Phase 2: `FlickKeyboardView` の統合（中核）

**`custom_keyboard/view/FlickKeyboardView.kt`**
- `KeyType.CIRCULAR_FLICK` 専用分岐を廃止し、`CROSS_FLICK` 分岐へ合流。
- `popupStyle` でコントローラーを選択:
  - `GRID` -> `GridFlickInputController`
  - `CIRCLE` -> `StandardFlickInputController`
  - `DONUT` -> `CustomAngleFlickController`
- `customAngleAndRange` / `circularViewScale` は DONUT 経路でのみ適用。
- 既存の `flickControllers`, `standardFlickControllers`, `gridFlickControllers` の管理を統合し、破棄漏れを防ぐ。

## Phase 3: CIRCULAR 実装の STANDARD 寄せリファクタリング

**`custom_keyboard/controller/StandardFlickInputController.kt`**
**`custom_keyboard/controller/CustomAngleFlickController.kt`**
- ACTION_DOWN/MOVE/UP のライフサイクル処理を共通化（基底クラスまたは共通関数へ抽出）。
- DONUT 固有差分（角度レンジ、5方向、リング UI、マップ切替）だけを分離。
- `BadTokenException` 回避など既存の表示ガードは共通経路へ移植。
- 最終的に CIRCULAR も `FlickAction` ベースの通知に揃える。

## Phase 4: レイアウト定義の一括置換

**`custom_keyboard/layout/KeyboardDefaultLayouts.kt`**
- すべての `"sumire" -> KeyType.CIRCULAR_FLICK` を `CROSS_FLICK` へ置換。
- `popupStyle` を `inputStyle` から一元決定するヘルパーを追加:
  - `circle` -> `CIRCLE`
  - `sumire` -> `DONUT`
  - それ以外 -> `GRID`
- 100+ 箇所ある `when(inputStyle)` の重複をヘルパー化して置換漏れを防止。

## Phase 5: 設定/UI 導線の整合

**`app/res/xml/pref_sumire.xml`**
**`core/res/values*/arrays.xml`**
**`app/.../SumirePreferenceFragment.kt`**
- スタイル値 `"sumire"` は後方互換のため維持（内部表現のみ統合）。
- 表示文言を「内部は CROSS 統合済み」と矛盾しない内容へ調整。
- 角度設定画面の表示条件は引き続き `"sumire"` 基準で維持。

**`app/.../CircularFlickSettingsFragment.kt` / `CircularFlickPreviewView.kt` / `IMEService.kt` / `AppPreference.kt`**
- 既存の角度/5方向/倍率設定キーは継続利用。
- 適用先判定を `KeyType.CIRCULAR_FLICK` 依存から `popupStyle=DONUT` 依存へ寄せる。

## Phase 6: 永続化マイグレーション

**`app/.../database/AppDatabase.kt`**
**`app/.../ime_service/di/AppModule.kt`**
- DB version を `21 -> 22` へ更新。
- `MIGRATION_21_22` を追加:
  - `keyType='CIRCULAR_FLICK'` を `CROSS_FLICK` に更新
  - 対象キーの `popupStyle` を `DONUT` へ補正（条件は既存データ形に合わせて定義）
- 新 migration を Room ビルダーに登録。

## Phase 7: エディタ/プレビュー追従

**`app/.../custom_keyboard/ui/EditableFlickKeyboardView.kt`**
- `KeyType.STANDARD_FLICK` 依存の見た目分岐を `popupStyle` ベースへ移行。

**`app/.../custom_keyboard/ui/KeyEditorFragment.kt`**
- popupStyle の保持ロジックを `STANDARD_FLICK` 特例から `popupStyle` 基準に変更。
- 既存キー編集時に DONUT スタイルが意図せず `GRID` へ戻らないようにする。

## Phase 8: クリーンアップ

- `CIRCULAR_FLICK` 参照ゼロを確認後、次段で enum 本体を削除。
- 用語（circle / sumire / donuts）のコメント・命名を整理。
- 方針確定後に `plan_CROSS_STANDARD.md` と同じ粒度で実装 PR を段階投入する。

## 注意点（実装前に固定しておくべき仕様）

- DONUT（現 sumire）の見た目を残すか、CIRCLE 見た目へ寄せるか。
  ※ 本計画は「見た目は残す・実装構造は STANDARD 寄せ」で作成。
- `sumire` という設定値を将来も残すか（UI 表示名のみ変更か、値ごと統合か）。
