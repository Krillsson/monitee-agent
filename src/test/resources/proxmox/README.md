Hand-written from the Proxmox VE 9.2 API viewer (and the 8.4 one for `node-status-pve8.json`),
plus the fields the server sets that the viewer leaves out. Each holds the whole response body,
`{"data": ...}`. Replace them with `pvesh get <path> --output-format json` captures from a real
node, wrapped the same way, when one is at hand.
