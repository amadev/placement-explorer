#!/usr/bin/env bash

set -o pipefail
set -o errexit
set -o xtrace

root=${1:-$(realpath $(dirname $0)/../)}

cd $root/placement_explorer_frontend

lein uberjar

mkdir -p $root/placement_explorer/static/

cp $root/placement_explorer_frontend/resources/public/css/site.min.css $root/placement_explorer/static/
cp $root/placement_explorer_frontend/target/cljsbuild/public/js/app.js $root/placement_explorer/static/
cp $root/placement_explorer_frontend/target/cljsbuild/public/js/app.js.map $root/placement_explorer/static/
