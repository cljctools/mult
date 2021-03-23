# mult

clojure(script) extension for vscode

## content

- [goal](./docs/design.md#goal)
- [development](#install-from-source)

## goal

- multiple apps, multiple repls: seamlessly switch connections for evaluation as we navigate files in source tree
- config driven: no connection sequence combos, jack-ins, jack-ass - define repls in mult.edn file, extension lazy-connects when needed
- a namespace and repls: tab is clear, shows current namespace and repls we can evalutate in
- tabs like browser pages: it's one tab app, but we can open multiple, just like in the browser
- simple: indent-only code formatting, colors and seamsless config driven evaluation
- github releases: download/install .vsix file from github, notify about updates; no store 
- listen to Jesus and move the mountain: drive development of http-repl - remote evaluation should be an HTTP server https://github.com/cljctools/http-repl

## development

- requires `git`, `nodejs`, `java`

```bash
git clone https://github.com/cljctools/mult
cd mult
bash f dev # will run shadow-cljs :mult and :mult-ui builds

```
- press `F5`, it will open VSCode extension debug window with mult running
- to compile .vsix: `bash f vsix`
  - vsix can be installed: in VSCode open Extensions  ->  `...` -> `Install from VSIX...` and select the `mult.vsix` file
  - or via command line https://code.visualstudio.com/api/working-with-extensions/publishing-extension#packaging-extensions