The files at the top level are hand-written from the Proxmox VE 9.2 API viewer (and the 8.4 one for
`node-status-pve8.json`), to cover what a single test node cannot show: a cluster, an offline node,
lost quorum, HA and locks. Each holds the whole response body, `{"data": ...}`.

`pve-9.2/` holds real responses from a standalone Proxmox VE 9.2.2 node running one VM with the
guest agent, one unprivileged container and a template, captured through the API with curl.
