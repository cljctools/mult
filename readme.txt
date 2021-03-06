mult

clojure(script) extension for vscode

goal

- multiple apps, multiple repls: connections are seamlessly selected for evaluation as we navigate files in source tree
- config driven: no connection sequences, jack-ins, jack-ass - define repls in mult.edn file, extension lazy-connects when needed
- a namespace and repls: tab is clear, shows current namespace and repls we can evalutate in
- tabs like browser pages: it's one tab app, but we can open multiple, just like in the browser
- simple: indent-only code formatting, colors
- no code linting, static analysis and other crotches: shouldn't be part of programming - writing code it's a creative process
- no store and no binaries released with repo: only source code and build from source
- releases - no, commit hashses as points
- updates - no update notifications
- language - clojure
- runtime - NodeJS, Browser
- build - is done with one cli command, build tool - shadow-cljs, output - .vsix file
- NodeJS, JVM - if missing is downloaded into repo's dir for use by build
- listen to Jesus and move the mountain: drive development of http-repl - remote evaluation should be an HTTP server https://github.com/cljctools/http-repl

rationale

- clojure(script) IDE experience is no minor issue - it's the thing between you and programs
- the editor and the extension 
  - should be long-term satisfactory, enjoyable and even inspiring
  - should be open source
  - should be written in clojure, or at least the extension should be written in clojure(script)
    - for simplicity
    - for asynchrony done via processes (core.async)
- the extension should
  - support multiple repl connections from one editor window
  - have a file configuration (for user and projects), where connections and repls can be specified (to not depend on key-combo connection sequences)
  - be simpler, code-wise and feature-wise 
- making an editor in clojure is, no doubt, a goal, but the extension for an existing editor is a logical first step
  - the work of making an extension is trasferrable even into an editor written in clojure, so the work won't be lost
- existing editor + extension combos
  - Emacs + Cider
    - perfect, if you're into it
  - IntelliJ + Cursive
    - Cursive comes with conditions
  - VSCode + Calva
    - can be considered the current best option
    - VSCode is the undeniable best open source editor
    - Calva works perfectly, but is written in typescript
    - NodeJS runtime is undesirable, but not a problem
- what exactly mult's design and value is ? what's the trigger to bother at all?
  - sometimes (rarely, but it happens), you run multiple apps that form a system
  - it's not an every-day thing, but when it happens, pain follows
  - so what you want is this
    - open one editor window
      - which (via workspaces for exmaple) already support multiple directories(repos) per workspace
    - add to that workspace all those apps(repos), start the system, so now multiple apps expose multiple nREPL connections
      - some nREPL connections - like shadow-cljs provides - expose 2 logcal repls (clj and cljs) by deafult, so it's one-connection:multiple-repls already by nREPL design
    - navigate between src code of those app and your REPL tab should follow you, switching connections and logical repls as you specified them in one config file
      - example config [mult.edn](../examples/fruits/.vscode/mult.edn)
    - that config says: hey, here are connections (separately), here are repls (that use those connections by key, but are separate) and here are tabs (every tab has some repls)
    - plus, that config has eval-able functions that help you easily (no need for sub-language) using clojure write a regexp for how to tell which repl to use for which namespaces
    - what you get, is an extension, where connections, repls, tabs are separated and a new repl, tab can be added/removed as needed
    - additionally, extension should handle reconnections by default, when you start/stop apps - if repl is within config, extension always attempts to connect; no key-combos on every restart
  - is such extension features somehting that is NEEDed ? Kind of yes, but not every day, that's true
  - but
    - there is **no reason** why extensions should not by design be 'zero, one or more'!
    - why is there a limitation of one window, one app (process) ? or hard coded workarounds? 
    - it should be by design: 0,1 or more connections, repls, tabs, configurable via file
- mult itself (as a VSCode extension) is an example of a project, that needs mult
  - mult is developed using shadow-cljs, which thankfully supports mutiple build (apps)
  - and mult consists of 2 apps
    - [mult/src/mult/extension.cljs](../mult/src/mult/extension.cljs) - extension itself
    - [mult/src/mult/impl/tabapp.cljs](../mult/src/mult/impl/tabapp.cljs) - react app that runs in the tab (VSCode is built with electron and tabs are actual browser tabs, isolated runtimes)
  - existing extensions support this already - you can manually select(switch) shadow-cljs build (from :extesnion to :tabapp for example)
  - but what you would prefer is to define in config file, that tabapp.cljs corresponds to :tabapp build and extension should do the switch automatically (over nrepl, as an :op) when the active file changes 
  - again, an eval-able predicate in mult.edn is used to determine which file corresponds to which repl(s)
  - for shared files (used in both apps) the preference can be set in the config file, still allowing to manually select(pin) :tabapp or :extension (this is how existing extensions approach it)