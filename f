#!/bin/bash

clean(){
    rm -rf .shadow-cljs node_modules .cpcache out
}

shadow(){
    ./node_modules/.bin/shadow-cljs "$@"
}

server(){
    shadow -A:core:dev server
    # yarn server
}

dev(){
    npm i
    shadow -A:core:dev watch :app
}

compile(){
    npm i
    shadow -A:core:dev compile app
}

origins(){
  git remote add bb https://bitbucket.org/fo4ram/mult
  git remote add gl https://gitlab.com/fo4ram/mult.git
  git remote -v
}

push(){
  git push origin master
  git push bb master
  git push gl master
}

"$@"