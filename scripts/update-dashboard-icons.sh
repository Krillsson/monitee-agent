#!/bin/sh
set -e
out="$(dirname "$0")/../src/main/resources/dashboard-icons.txt"
curl -sfL https://raw.githubusercontent.com/homarr-labs/dashboard-icons/main/tree.json \
  | python3 -c '
import json, sys
tree = json.load(sys.stdin)
names = {}
for fmt in ("svg", "png", "webp"):
    for file in tree[fmt]:
        names.setdefault(file.rsplit(".", 1)[0], []).append(fmt)
for name in sorted(names):
    print(name + "\t" + ",".join(names[name]))
' > "$out"
