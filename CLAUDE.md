# JapaneseKeyboard フォーク運用メモ

このリポジトリは [KazumaProject/JapaneseKeyboard](https://github.com/KazumaProject/JapaneseKeyboard) のフォーク。
本家へのPR送付を目的としている。

## ブランチ構成

- `main` — 本家（upstream/main）と完全同期
- `personal` — CLAUDE.md等、本家PRに含めない個人設定を管理
- PR用ブランチ — `main` から切って本家へPRを送る

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

## PRを出すとき

```bash
git checkout main
git checkout -b fix/something
# 変更してコミット
git push origin fix/something
# GitHub上でKazumaProject/JapaneseKeyboardへPRを作成
```
