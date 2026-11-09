#!/usr/bin/env bash
# Builds libllnative.{so,dylib} into native/build. Requires a JDK and a C compiler.
set -euo pipefail
cd "$(dirname "$0")"
: "${JAVA_HOME:=$(dirname "$(dirname "$(readlink -f "$(which javac)")")")}"
mkdir -p build
case "$(uname -s)" in
  Linux)  cc -O2 -shared -fPIC -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux"  ll_native.c -o build/libllnative.so ;;
  Darwin) cc -O2 -shared -fPIC -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/darwin" ll_native.c -o build/libllnative.dylib ;;
  *) echo "unsupported OS" >&2; exit 1 ;;
esac
echo "built native/build"
