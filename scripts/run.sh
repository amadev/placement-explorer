#!/usr/bin/env bash

set -o pipefail
set -o errexit
set -o xtrace

flask run --host 0.0.0.0
