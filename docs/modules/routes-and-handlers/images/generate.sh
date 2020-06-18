#!/usr/bin/env bash
set -euo pipefail

dot -T png decision-graph.gv > decision-graph.png
