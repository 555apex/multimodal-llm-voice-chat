#!/usr/bin/env bash
set -euo pipefail
case "$(uname -m)" in
 aarch64) platform=linux/arm64 ;;
 x86_64) platform=linux/amd64 ;;
 *) echo 'Supported native hosts: aarch64 or x86_64' >&2;exit 2 ;;
esac
here=$(cd "$(dirname "$0")" && pwd)
package=$(cd "$here/../.." && pwd)
work=${1:?Usage: build_cpu_images.sh NEW_WORK_DIRECTORY}
test ! -e "$work" || { echo 'Work directory must be new' >&2;exit 2; }
mkdir -p "$work/source" "$work/bge"
if [ -n "${2:-}" ]; then
 source_root=$(cd "$2" && pwd)
elif [ -f "$package/resources/source/roadagent-current-source.tar.gz" ]; then
 tar -xzf "$package/resources/source/roadagent-current-source.tar.gz" -C "$work/source"
 source_root="$work/source"
else
 source_root=$(cd "$package/../.." && pwd)
fi
test -f "$source_root/pom.xml" && test -d "$source_root/speech-service/app" || { echo 'Valid source root required' >&2;exit 2; }
docker build --platform "$platform" -t roadagent-site-speech:cpu-reviewed \
 -f "$package/resources/configs/Dockerfile.speech-cpu" "$source_root"
cp "$package/resources/configs/bge_cpu_service.py" "$work/bge/"
docker build --platform "$platform" -t roadagent-site-bge:cpu-reviewed \
 -f "$package/resources/configs/Dockerfile.bge-cpu" "$work/bge"
echo 'Build complete; model loading and API acceptance are still required before export.'
