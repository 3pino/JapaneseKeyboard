# JapaneseKeyboard フォーク運用メモ

このリポジトリは [KazumaProject/JapaneseKeyboard](https://github.com/KazumaProject/JapaneseKeyboard) のフォーク。
本家へのPR送付を目的としている。

## ブランチ構成

- `main` — 本家（upstream/main）と完全同期。直接触らない。
- `personal` — 開発ベース。CI設定・CLAUDE.md等、本家PRに含めない個人設定を含む。普段はここから作業する。
- PR用ブランチ（`fix/**`, `feature/**`）— `personal` から切って開発・テスト。本家へPRするときは `main` から切り直してきれいなコミットだけを載せる。

## GitHub Actions

- `personal` または `fix/**`, `feature/**` へのpushで自動的にデバッグビルドが走る（`.github/workflows/build-test.yml`）
- ビルド成果物（APK）はActionsのArtifactsから7日間ダウンロード可能
- リリースビルドはタグ（`v*`）のpushで動く（`.github/workflows/android.yml`）

## 普段の開発フロー

```bash
# personalから作業ブランチを切る
git checkout personal
git checkout -b fix/something

# 変更してコミット・push → Actionsでビルドテスト
git push origin fix/something
```

## 本家へPRを出すとき

```bash
# mainから改めてきれいなブランチを切る
git checkout main
git checkout -b fix/something-for-upstream

# cherry-pickまたは手動で変更を再現してコミット
git cherry-pick <commit-hash>
git push origin fix/something-for-upstream
# GitHub上でKazumaProject/JapaneseKeyboardへPRを作成
```

## 本家の更新を取り込む

```bash
git fetch upstream
git checkout main
git reset --hard upstream/main
git push origin main --force
git checkout personal
git rebase main
git push origin personal --force
```
