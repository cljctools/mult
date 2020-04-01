#!/bin/bash

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

dc(){

    docker-compose --compatibility \
        -f docker-compose.yml \
        "$@"
}

"$@"