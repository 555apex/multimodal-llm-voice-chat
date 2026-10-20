#!/usr/bin/env bash
set -euo pipefail
here=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
package=$(cd "$here/../.." && pwd)
envfile=${SITE_ENV:-/opt/roadagent-migration/site.env}
test -f "$envfile" || { echo 'Create site.env first' >&2; exit 2; }
site=$(dirname -- "$envfile")
accelerator_compose="$site/compose.npu.yaml"
if python3 -c 'import json,sys; sys.exit(0 if json.load(open(sys.argv[1])).get("accelerator_type","ascend")=="nvidia" else 1)' "$site/target-profile.json"; then accelerator_compose="$site/compose.nvidia.yaml"; fi
python3 "$here/local_guard.py" "$envfile"
dc=(docker compose --project-name roadagent-fz-pilot --env-file "$envfile" -f "$package/resources/configs/compose.site.yaml")
action=${1:-status};shift || true
case "$action" in
 config) "${dc[@]}" config --quiet ;;
 db-up) "${dc[@]}" up -d --no-build --pull never mysql postgres redis ;;
 rag-up) "${dc[@]}" up -d --no-build --pull never docreader rag-model weknora-app weknora-ui ;;
 npu-up|gpu-up|accelerator-up|app-up|all-status)
   python3 "$here/04_runtime_gate.py" --env-file "$envfile"
   dc+=(-f "$accelerator_compose")
   case "$action" in
    npu-up|gpu-up|accelerator-up) "${dc[@]}" up -d --no-build --pull never qwen tts ;;
    app-up) "${dc[@]}" up -d --no-build --pull never speech-service backend frontend ;;
    all-status) "${dc[@]}" ps ;;
   esac ;;
 status) "${dc[@]}" ps ;;
 stop)
   if [ -f "$accelerator_compose" ]; then dc+=(-f "$accelerator_compose"); fi
   "${dc[@]}" stop ;;
 logs)
   if [ -f "$accelerator_compose" ]; then dc+=(-f "$accelerator_compose"); fi
   "${dc[@]}" logs --tail 100 "$@" ;;
 exec)
   if [ -f "$accelerator_compose" ]; then dc+=(-f "$accelerator_compose"); fi
   "${dc[@]}" exec -T "$@" ;;
 *) echo 'Actions: config db-up rag-up accelerator-up npu-up gpu-up app-up status all-status stop logs exec' >&2;exit 2 ;;
esac
