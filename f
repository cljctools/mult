#!/bin/bash

clean(){
    rm -rf resources/public/js-out out
}

purge(){
    clean
    rm -rf  .shadow-cljs .cpcache 
}

shadow(){
    ./node_modules/.bin/shadow-cljs "$@"
}

server(){
    shadow -A:dev server
    # yarn server
}

dev(){

    # npm i
    shadow -A:dev:cljs-self-hosting-shadow-nodejs watch :mult :mult-ui :mult-bootstrap

}

compile(){
    npm i
    shadow -A:dev:cljs-self-hosting-shadow-nodejs compile  :mult :mult-ui :mult-bootstrap 
}

release(){
    npm i
    shadow -A:dev release :mult-ui
}

tree(){
    clojure -A:dev -Stree
}



cljs_compile(){
    clj -A:dev:self-hosted-cljs -m cljs.main -co cljs-build.edn -c
    #  clj -A:dev -m cljs.main -co cljs-build.edn -v -c # -r
}

repl(){

  # lein repl :start :host 0.0.0.0 :port 7788
  # lein repl :headless :host 0.0.0.0 :port 7788
  # lein repl :connect 0.0.0.0:7878
  lein repl :headless

}


dev_clj(){
  clojure -A:core:dev:local -m cljctools.mult.test.nrepl
}

run_dev(){
  lein run dev
}

run_uberjar(){
  java -jar target/app.standalone.jar
}

uberjar(){
  lein with-profiles +prod uberjar
  # java -jar target/app.uber.jar 
}

native(){
  # rm -rf resources/public
  # cp -r /ctx/ipfs-cube/bin/ui2/resources/public ./resources
  lein native-image
}

native_deps(){
  clojure -A:native-image 
}

native_cli(){
  native-image \
  --no-server \
  --report-unsupported-elements-at-runtime \
  --allow-incomplete-classpath \
  --initialize-at-build-time \
  --enable-url-protocols=http \
  -H:IncludeResources=.*public.* \
  -H:Log=registerResource: \
  --verbose \
  --no-fallback \
  -jar ./target/app.standalone.jar
}

upx_compress(){
  # https://github.com/upx/upx
  rm ./target/app.upx.native
  upx -9 -o ./target/app.upx.native ./target/app.native
}

docker_run_release(){
  docker build -t ipfs-cube.daemon-release -f release.Dockerfile .
  docker run -it --rm -v /var/run/docker.sock:/var/run/docker.sock ipfs-cube.daemon-release
}


"$@"