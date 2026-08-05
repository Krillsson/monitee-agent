Monitors that cross their threshold, containers that fall behind their registry and new agent releases all raise a notification. The agent sends every one of them to ntfy and to each configured webhook, so you can use one, both or several at the same time.

Everything here lives under `notifications` in `configuration.yml`.

## ntfy

```yaml
notifications:
  serverName: MyServer
  ntfy:
    enabled: true
    url: "https://ntfy.sh"
    topic: my-custom-globally-unique-topic
```

Leaving `topic` out generates one from the server id. On a public ntfy instance anyone who knows the topic name can read your notifications, so pick something unguessable or use your own instance.

A protected topic needs credentials. Either an access token:

```yaml
    token: tk_AgQdq7mVBoFD37zQVN29RhuMzNIz2
```

or a username and password:

```yaml
    username: user
    password: password
```

`token` wins if both are given. Tokens are made under *Account → Access tokens* in the ntfy web app.

Notifications are sent with ntfy tags for what happened and what it is about — `rotating_light` and
`computer` for a cpu alert, `white_check_mark` when it is back to normal — which ntfy turns into
emoji in front of the title. `emoji: false` sends no tags.

## Webhooks

`webhooks` is a list, and each entry is one HTTP request the agent makes when something happens:

```yaml
notifications:
  webhooks:
    - name: My receiver
      enabled: true
      url: "https://example.com/hook"
      method: POST
      contentType: application/json
      headers:
        X-Api-Key: secret
      username: user
      password: password
      emoji: true
      timeoutSeconds: 10
      body: '{"text": "{{title}}"}'
```

`url` is the only field without a useful default. `username` and `password` send an `Authorization` header with basic auth, and anything under `headers` is sent as given — use that for the token headers most services want. Requests are made in the background, so a receiver that is slow or down never holds up monitoring.

### The default body

Without a `body` the agent posts this JSON, which is enough for anything that can read arbitrary fields (n8n, Node-RED, Home Assistant webhooks, your own script):

```json
{
  "title": "🚨 CPU load too high on MyServer",
  "message": "🖥️ Load went above 80% to 94%",
  "priority": 4,
  "clickUrl": "https://monitee.app/server/<server id>/monitor/<monitor id>",
  "eventType": "ONGOING_EVENT",
  "monitorType": "CPU_LOAD",
  "timestamp": "2026-08-05T12:34:56Z",
  "serverName": "MyServer",
  "serverId": "<server id>"
}
```

`eventType` is one of `ONGOING_EVENT`, `RESOLVED_EVENT`, `UPDATE_AVAILABLE`, `MONITORED_ITEM_MISSING`, `CONTAINER_IMAGE_UPDATE_AVAILABLE` or `CONTAINER_IMAGE_UPDATE_DIGEST`. `monitorType` is null for the events that do not come from a monitor.

### Emoji

The title is prefixed with an emoji for what happened, and the message with one for what it is
about, so an alert is recognisable before reading it:

| | |
|---|---|
| 🚨 | a monitor went outside its threshold |
| ✅ | a monitor came back inside it |
| 🆕 | a new monitee-agent release |
| 🐳 | container image updates |
| ❓ | a monitored item is gone |

Messages from a monitor carry the metric instead: 🖥️ cpu, 🧠 memory, 💾 file system, 💽 disk,
🌡️ temperature, 🩺 SMART health, 📈 load average, 🌐 network, 🌍 web server, 📡 connectivity,
📍 external ip, 🐳 container, ⚙️ process, 🎮 gpu, 🔋 UPS.

This is on by default and applies to templates too, since it is part of `{{title}}` and
`{{message}}`. Set `emoji: false` on a webhook whose receiver would rather have plain text.

ntfy gets the same emoji as tags rather than as text, since that is how ntfy does it.

### Body templates

Services that expect their own shape get a `body` template instead. These placeholders are substituted:

`{{title}}` `{{message}}` `{{priority}}` `{{clickUrl}}` `{{eventType}}` `{{monitorType}}` `{{timestamp}}` `{{serverName}}` `{{serverId}}`

Values are escaped for the `contentType`, so a message containing quotes or newlines will not break a JSON body. Placeholders in `url` are escaped for use in a url. Anything the agent does not recognise is left alone.

### Examples

**Gotify** — the token goes in the url, or in a `X-Gotify-Key` header:

```yaml
    - name: Gotify
      url: "https://gotify.example.com/message?token=APP_TOKEN"
      body: '{"title": "{{title}}", "message": "{{message}}", "priority": {{priority}}}'
```

**Discord**:

```yaml
    - name: Discord
      url: "https://discord.com/api/webhooks/ID/TOKEN"
      body: '{"content": "**{{title}}**\n{{message}}\n{{clickUrl}}"}'
```

**Slack** (and Mattermost, which takes the same shape):

```yaml
    - name: Slack
      url: "https://hooks.slack.com/services/T000/B000/XXXX"
      body: '{"text": "*{{title}}*\n{{message}}"}'
```

**Telegram** — `chat_id` is your own chat, from `@userinfobot`:

```yaml
    - name: Telegram
      url: "https://api.telegram.org/botTOKEN/sendMessage"
      body: '{"chat_id": "123456789", "text": "{{title}}\n{{message}}"}'
```

**Apprise**, running as an API server, which reaches the ~100 services it supports from one webhook:

```yaml
    - name: Apprise
      url: "https://apprise.example.com/notify/my-config-key"
      body: '{"title": "{{title}}", "body": "{{message}}"}'
```

**Nextcloud notify_push** takes the message as plain text rather than JSON:

```yaml
    - name: Nextcloud
      url: "https://cloud.example.com/index.php/apps/uppush/push/DEVICE_KEY"
      contentType: text/plain
      body: '{{title}}: {{message}}'
```

**Home Assistant** webhook trigger, where the default body is already what the automation gets as `trigger.json`:

```yaml
    - name: Home Assistant
      url: "https://ha.example.com/api/webhook/WEBHOOK_ID"
```

## Troubleshooting

The agent logs every failed delivery with the webhook's name and the answer it got. A webhook whose url is not a valid http or https url is reported at startup and skipped — the rest keep working. `notificationServices` in the GraphQL API lists what the agent ended up with.
