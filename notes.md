
- core.async
  - https://github.com/clojure/core.async/blob/master/src/main/clojure/clojure/core/async.clj#L881

- vscode
  - api 
    - https://code.visualstudio.com/api/references/vscode-api#TextEditor
  - https://github.com/microsoft/vscode-extension-samples/tree/master/helloworld-sample
  - api
    - https://code.visualstudio.com/api/extension-guides/command#registering-a-command
  - launch.json
    - https://code.visualstudio.com/api/working-with-extensions/testing-extension#debugging-the-tests
  - sample extentions in cljs
    - https://github.com/Saikyun/cljs-vscode-extension-hello-world
  - sample websocket
    - https://github.com/microsoft/vscode-extension-samples/blob/master/lsp-log-streaming-sample/client/src/extension.ts
  - webview
    - https://code.visualstudio.com/api/extension-guides/webview

- shadow-cljs
  - https://shadow-cljs.github.io/docs/UsersGuide.html#target-node-library
  - https://shadow-cljs.github.io/docs/UsersGuide.html#_calva_vs_code
  - https://shadow-cljs.github.io/docs/UsersGuide.html#cljs-repl-anatomy
  - https://shadow-cljs.github.io/docs/UsersGuide.html#missing-js-runtime
  - required js libs in node won't be processed
    - https://shadow-cljs.github.io/docs/UsersGuide.html#js-provider

- cljs repl
  - https://clojurescript.org/reference/repl
  - cljs repls evaluations envs 
    - https://github.com/clojure/clojurescript/tree/master/src/main/clojure/cljs/repl
    - browser 
      - https://github.com/clojure/clojurescript/blob/master/src/main/clojure/cljs/repl/browser.clj#L389
    - node 
      - https://github.com/clojure/clojurescript/blob/master/src/main/clojure/cljs/repl/node.clj#L206
    - repl eval
      - https://github.com/clojure/clojurescript/blob/master/src/main/clojure/cljs/repl.cljc#L1162
  - browser connect & xpc-connection 
    - https://github.com/clojure/clojurescript/blob/master/src/main/cljs/clojure/browser/repl.cljs#L226
    - https://github.com/clojure/clojurescript/blob/master/src/main/cljs/clojure/browser/net.cljs#L117

- nrepl
  - https://github.com/nrepl/nrepl
    - https://nrepl.org/nrepl/ops.html
    - https://nrepl.org/nrepl/0.7.0/usage/clients.html#_talking_to_an_nrepl_endpoint_programmatically
    - https://github.com/nrepl/nrepl/blob/master/src/spec/nrepl/spec.clj
  - https://github.com/nrepl/piggieback
  - https://github.com/nrepl/weasel
  - examples of connecting from not-java
    - https://nrepl.org/nrepl/0.7.0/usage/clients.html
      - https://github.com/BetterThanTomorrow/calva/tree/master/src/nrepl
      - https://github.com/rksm/node-nrepl-client/blob/master/src/nrepl-client.js
  - https://docs.cider.mx/cider-nrepl/internals.html#_clojurescript_support
  - api
    - nrepl.server/start-server
      - https://github.com/nrepl/nrepl/blob/master/src/clojure/nrepl/server.clj#L109
    - nrepl.core/message
      - https://github.com/nrepl/nrepl/blob/master/src/clojure/nrepl/core.clj#L80
    - nrepl.transport/bencode (is a transport-fn)
      - https://github.com/nrepl/nrepl/blob/master/src/clojure/nrepl/transport.clj#L93

- nodejs
  - https://nodejs.org/api/net.html#net_class_net_socket
  - https://nodejs.org/api/net.html#net_socket_connect_options_connectlistener
  - https://nodejs.org/api/net.html#net_net_createconnection_options_connectlistener

- paredit
  - https://www.emacswiki.org/emacs/ParEdit
  - http://mumble.net/~campbell/emacs/paredit.html
  - https://github.com/MarcoPolo/atom-paredit/blob/master/cljs/paredit/core.cljs
  - https://github.com/BetterThanTomorrow/calva/tree/master/src/paredit
  - https://github.com/clj-commons/rewrite-cljs/blob/master/src/rewrite_clj/paredit.cljs
  - https://github.com/laurentpetit/paredit.clj
    - https://github.com/ccw-ide/ccw/tree/master/paredit.clj
  - https://github.com/rksm/paredit.js

- github
  - linguist
    - https://github.com/github/linguist/issues/137
    - https://github.com/github/linguist#overrides

- protocols
  - https://www.infoq.com/interviews/hickey-clojure-protocols/
    - "...it's not like you're putting it on string; you're saying there is a fn whose behavior I'd like to be different when it's passed strings... "
  - https://github.com/clojure/clojurescript/blob/c057c92bd20bb4bea61970fb87247582ae2f5423/src/main/cljs/cljs/core.cljs
  - https://github.com/clojure/spec.alpha/blob/master/src/main/clojure/clojure/spec/alpha.clj
  - https://github.com/clojure/core.async/blob/1b8b972372e570d47959887c00a8726472236e74/src/main/clojure/clojure/core/async/impl/protocols.clj