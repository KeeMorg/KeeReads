#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
javac -d qa qa/SyntaxCheck.java
python3 qa/check_project.py
