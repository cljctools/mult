# setup

## install some tools

- VSCode
- git
- nodejs/npm
- OpenJDK (11 or 14)
  - ubuntu example
  ```bash 
    sudo apt-get update && sudo apt-get install -y openjdk-11-jdk
   
  ```
- clojure https://clojure.org/guides/getting_started#_installation_on_linux
- leiningen https://leiningen.org/
  - ubuntu example
  ```bash
    curl -O https://raw.githubusercontent.com/technomancy/leiningen/2.9.3/bin/lein && \
    sudo mv lein /usr/local/bin/ && \
    sudo chmod a+x /usr/local/bin/lein && \
    lein version
  ```

## clone repos

- naviagate to the code dir (e.g. `/home/user/code` or `~/code`)
- clone (repos are empty at this point, README only)

```bash
git clone https://github.com/cljctools/mult
git clone https://github.com/cljctools/cljctools
```

## open VSCode workspace

```bash
code mult/.vscode/mult.code-workspace
```