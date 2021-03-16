# mult

clojure(script) extension for vscode

## content

- [rationale](./docs/design.md#rationale)
- [development](#install-from-source)

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