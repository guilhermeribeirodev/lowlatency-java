#!/usr/bin/env bash
# Build the fat jar, copy it to the VM and (re)start the service.
# Usage: scripts/deploy-azure.sh azureuser@<public-ip> [bench]
#   bench -> also run the JMH suite on the VM and print results (numbers on the target hardware matter)
set -euo pipefail
cd "$(dirname "$0")/.."
TARGET="${1:?usage: deploy-azure.sh user@host [bench]}"

mvn -q -B package -DskipTests
# benchmarks.jar is the shaded jar: all dependencies + JMH main. It also contains io.example.ll.Main,
# so the same artifact serves as the app jar (-cp app.jar io.example.ll.Main).
scp target/benchmarks.jar "$TARGET:/tmp/app.jar"
ssh "$TARGET" 'sudo install -o "$USER" -m 0644 /tmp/app.jar /opt/lowlatency/app.jar && sudo systemctl restart lowlatency && sleep 3 && systemctl --no-pager status lowlatency | head -15'

if [[ "${2:-}" == "bench" ]]; then
  ssh "$TARGET" 'sudo systemctl stop lowlatency; java -jar /opt/lowlatency/app.jar -prof gc -f 1 -wi 3 -i 5'
fi
