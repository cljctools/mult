# contributing

## git workflow

- fork
- commit to fork's master as you please
- once done (you are still in your fork's master)
  - git remote add source https://github.com/clojuretools/mult
  - git fetch source
  - [or see git pull --rebase] git merge --no-ff [-X theirs] source/master, resolve conflicts
    - that's the part where thinking is required, may use -X theirs
    - it's important to merge before rebase to squash the merge commit as well
      - alternatively, rebase, merge, rebase again
      - the goal is: to have in the end one clean commit to the source repository
  - git rebase -i source/master , squash commits into 1
    - all commits, including the merge one, are squashed into one, commit date becomes now
  - git commit --amend --no-edit --date=now 
    - sets the date to now (otherwise it's the date of the picked commit)
  - git pull --rebase `<remote-name>` `<branch-name>`
    - to pull from source directly without creating merge commits
    - https://stackoverflow.com/questions/30052104/how-to-avoid-merge-commits-from-git-pull-when-pushing-to-remote
  - if during this process, another commit happened and there are conflicts, do merge, rebase steps again
  - git push -f
    - this will replace your master branch history with the result of rebase
    - if you want to keep your raw commit history, checkout a branch for that first 
  - once done, create pull request from fork/master to source/master

#### additional notes

- to rebase -i comfortably, using vscode instead of terminal, add to ~/.gitconfig
  ```bash
  [diff]
      tool = default-difftool
  [difftool "default-difftool"]
      cmd = code --wait --diff $LOCAL $REMOTE
  [core]
    editor = code --wait
  ```
- to filter branch
  - git filter-branch --env-filter 'export GIT_COMMITTER_DATE="$GIT_AUTHOR_DATE"' SHA1..HEAD
  - SHA1 will not be included it seems
- to see committer date
  - git show -s --format=%ci SHA1
- hot to avoid merge commits
  - https://stackoverflow.com/questions/30052104/how-to-avoid-merge-commits-from-git-pull-when-pushing-to-remote
  - git pull --rebase <remote-name> <branch-name>