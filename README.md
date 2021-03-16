# mult

clojure(script) extension for vscode

## content

- [rationale](./docs/design.md#rationale)
- [install](#install)
- [install-from-source](#install-from-source)
- [development](#install-from-source)

## install

- download `mult.vsix` file from the [latest release](https://github.com/cljctools/mult/releases)
- in VSCode open Extensions  ->  `...` -> `Install from VSIX...` and select the `mult.vsix` file

or install via command line https://code.visualstudio.com/api/working-with-extensions/publishing-extension#packaging-extensions

## install from source

- requires `git`, `nodejs`, `java`

```bash
git clone https://github.com/cljctools/mult
cd mult
bash f release
bash f vsix  # outputs mult.vsix file

```
- install the `mult.vsix` file as in [install section](#install)


## development

- requires `git`, `nodejs`, `java`

```bash
git clone https://github.com/cljctools/mult
cd mult
bash f dev # will run shadow-cljs :mult and :mult-ui builds

```
- press `F5`, it will open VSCode extension debug window with mult running