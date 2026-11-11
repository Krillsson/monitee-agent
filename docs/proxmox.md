The agent can list the nodes, VMs, containers and storage of a Proxmox VE host or cluster, with each guest's CPU, memory and traffic. It reads them from the Proxmox API with an API token, so it works the same whether the agent runs on the node itself or anywhere else on the network.

## Create a token

On any node of the cluster, create a user with a token that may read everything:

```sh
pveum user add monitee@pve --comment "Monitee agent"
pveum user token add monitee@pve agent --privsep 1
pveum acl modify / --users monitee@pve --roles PVEAuditor
pveum acl modify / --tokens 'monitee@pve!agent' --roles PVEAuditor
```

The second command prints the token's secret once, so copy it. A token with privilege separation only gets what both it and its user are granted, which is why the role is given to both.

On Proxmox VE 8 the guest agent, and with it a VM's IP addresses, needs `VM.Monitor`, which `PVEAuditor` does not have. Everything else works without it.

To start, shut down and reboot guests from the app as well, give the token `VM.PowerMgmt` on the guests:

```sh
pveum role add MoniteePower --privs "VM.PowerMgmt"
pveum acl modify /vms --users monitee@pve --roles MoniteePower
pveum acl modify /vms --tokens 'monitee@pve!agent' --roles MoniteePower
```

## Configure the agent

```yaml
proxmox:
  enabled: true
  host: "pve"
  port: 8006
  tokenId: "monitee@pve!agent"
  secret: "<the secret>"
  caCertificatePath: "/config/pve-root-ca.pem"
```

Proxmox signs its own certificate. Copy `/etc/pve/pve-root-ca.pem` from a node next to `configuration.yml` and point `caCertificatePath` at it, and use the node's name as `host` since that is the name the certificate is issued for. `verifyTls: false` accepts any certificate instead.

See the `proxmox` block in [configuration.yml](/config/configuration.yml) for the rest of the options.
