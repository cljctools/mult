#!/bin/bash

clean(){
    rm -rf resources/public/js-out out .shadow-cljs .cpcache 
}

repl(){
  clj -A:repl
}

shadow(){
    ./node_modules/.bin/shadow-cljs -A:core:shadow-cljs:mult-vscode:mult-ui-vscode "$@"
}

dev(){

    npm i
    shadow watch :mult-vscode :mult-ui-vscode

}

compile(){
    npm i
    shadow compile :mult-vscode :mult-ui-vscode
}

build(){
    rm -rf resources/out
    npm i
    shadow release :mult-vscode :mult-ui-vscode
}


cljs_compile(){
    clj -m cljs.main -co cljs-build.edn -c
    #  clj -A:dev -m cljs.main -co cljs-build.edn -v -c # -r
}

vsix(){
  npx vsce package
}

server(){
    shadow server
}



"$@"