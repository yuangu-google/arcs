#!/bin/sh
# target
mkdir dist
# sources
cp -f ../canonical.manifest dist/
cp -f source/index.html dist/
# particles
mkdir dist/particles
cp -fR ../../../../particles/* dist/particles/
mkdir -p dist/bazel-bin/particles/Native/Wasm
cp -fR ../../../../bazel-bin/particles/Native/Wasm/*.wasm dist/bazel-bin/particles/Native/Wasm/

# worker build
cp -fR ../../../lib/build/worker.js dist/
# collate sources
echo packing...
npx webpack --verbose
echo done.
