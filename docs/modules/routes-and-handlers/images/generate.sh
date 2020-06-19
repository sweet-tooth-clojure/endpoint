#!/usr/bin/env bash
set -euo pipefail

dot -T png decision-and-handler-graph.gv > decision-and-handler-graph.png
dot -T png decision-graph.gv > decision-graph.png
