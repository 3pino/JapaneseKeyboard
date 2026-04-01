ブランチの管理

main：作業ベース。personal設定のコミットを先頭に積む。
本家PRは main から一時ブランチを切って cherry-pick する。

開発

git checkout main
# 作業して commit
git push

PR作成

git checkout -b fix/xxx
git cherry-pick <commit>  # personal設定以外のコミットのみ
git push

upstream同期

git fetch upstream
git rebase upstream/main
git push -f

CI

pushでビルド
タグでリリース