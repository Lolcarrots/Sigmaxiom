# Sigmaxiom v0.1.0
Notebook IDE aimed at scientific computing with [LaTeX2Futhark](https://github.com/Lolcarrots/LaTeX2Futhark) transpiler integration, a built-in math editor, and pseudo-multi-language support.

This is the "official" version of the Sigmaxiom IDE, no other repo is currently supported by me (Lolcarrots) (💀).

# Dependencies
sympy

antlr4-python3-runtime

LaTeXML + latexmlmath

futhark-ffi

ipykernel

Maven

# Installation
```bash
pip3 install sympy antlr4-python3-runtime futhark-ffi ipykernel
```
```bash
sudo apt install latexml
```
```bash
sudo apt install maven
```

Navigate to your Sigmaxiom-main directory (wherever you have it):
```bash
cd "~/Sigmaxiom-main"
```
```bash
mvn clean package
```
```bash
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
```

# Usage
```bash
java -cp "target/classes:$(cat cp.txt)" com.sigmaxiom.JupyterNotebookIDE
```

Select kernel on startup (try restarting the application and/or kernel(s) if cell execution is not behaving correctly). Different languages will write files to the current working directory. The math editor and transpiler buttons can be found on the right side of the interface, and these LaTeX "cells" can also be saved/loaded into notebooks using JSON format. Notebooks have independent kernels by default, but new notebooks can also be opened with shared kernels. Hotkeys can be set using the top bar's menu, and notebooks can be exported with their respective wrappers under the File menu.

# TODO
- Improve consistency of kernel behavior on startup and opening new folders/files
- Fix kernel browser window's kernel selection list (and saving of default/added kernels)
- Improve error checking logic and/or add some form of multi/cross-language LSP functionality
- Improve syntax highlighting for certain languages (Nim and CUDA)
- Add more hotkey options
- Improve the visualization window
- Continue to improve LaTeX2Futhark's LaTeX support
- Clean up terminal window inputs and outputs
- Actually write real documentation 🗿
- MAYBE add some form of simple AI support
