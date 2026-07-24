# ClusterPulse — Cluster Health Monitoring & Self-Healing

A monitoring-and-remediation platform for a virtualized cluster. It watches node
health in real time, fires alerts when a node degrades, and **automatically runs
remediation runbooks** to recover — the core loop behind keeping a hyperconverged
cluster healthy with minimal human intervention.

> Built around the pattern Nutanix describes as *"cluster health monitoring and
> quick response times."* The remediation daemon is a **Spring Boot** service;
> the observability stack is Prometheus + Alertmanager + Grafana; nodes run on
> **KVM/QEMU via libvirt** (a Docker Compose path is included so you can run the
> whole thing on one machine in minutes).

---

## The self-healing loop

```
        ┌──────────────┐   metrics    ┌────────────┐   rules fire   ┌───────────────┐
        │  Node 1..N   │ ───────────▶ │ Prometheus │ ─────────────▶ │ Alertmanager  │
        │ node_exporter│  (scrape)    │            │                │               │
        └──────────────┘              └────────────┘                └───────┬───────┘
              ▲                              │                               │ webhook
              │ runbook acts on node         │ dashboards                    ▼
              │                              ▼                     ┌───────────────────────┐
        ┌─────┴────────┐              ┌────────────┐   scrape      │  Remediation Daemon    │
        │  Runbooks    │ ◀─────────── │  Grafana   │ ◀──────────── │  (Spring Boot)         │
        │ (bash)       │   executes   └────────────┘  daemon       │  match alert → runbook │
        └──────────────┘                               metrics     └───────────────────────┘
```

1. **node_exporter** on each node exposes CPU / memory / disk / network metrics.
2. **Prometheus** scrapes them and evaluates alert rules (`NodeDown`, `HighCPU`, `DiskPressure`).
3. **Alertmanager** routes a firing alert to the daemon as a webhook.
4. The **Spring Boot daemon** maps the alert to a runbook, runs it (with cooldown +
   safety checks), and publishes its own metrics.
5. **Grafana** visualizes both cluster health and the remediation activity — so you
   can literally watch a node fail and the system respond.

---

## Tech stack

| Layer                 | Technology                                              |
|-----------------------|---------------------------------------------------------|
| Virtualization        | KVM / QEMU, libvirt, cloud-init                          |
| Metrics               | Prometheus, node_exporter                               |
| Alerting              | Alertmanager                                            |
| Dashboards            | Grafana (provisioned datasource + dashboard)            |
| Remediation daemon    | Java 17, Spring Boot 3, Spring Web, Actuator, Micrometer|
| Runbooks              | Bash                                                    |
| Packaging / demo      | Docker, Docker Compose                                  |

---

## Repository layout

```
ClusterPulse/
├── docker-compose.yml         # one-command demo of the whole loop
├── prometheus/                # scrape config + alert rules
├── alertmanager/              # routes alerts to the daemon webhook
├── grafana/                   # provisioned datasource + cluster dashboard
├── remediation-daemon/        # Spring Boot service (the brain)
│   └── src/main/java/com/clusterpulse/daemon/
│       ├── web/               # webhook controller + Alertmanager DTOs
│       ├── service/           # routing, cooldown, safe runbook execution
│       ├── metrics/           # Micrometer counters/timers
│       └── config/            # ConfigurationProperties (alert → runbook map)
├── runbooks/                  # cordon-node / restart-service / disk-cleanup
├── faultinjection/            # scripts to trigger failures for demos
└── infra/                     # KVM/libvirt provisioning + cloud-init
```

---

## Quick start (Docker — no KVM needed)

Requires Docker + Docker Compose.

```bash
docker compose up --build
```

Then open:

- Grafana dashboard: <http://localhost:3000>  (anonymous view enabled; admin/admin to edit)
- Prometheus + alerts: <http://localhost:9090/alerts>
- Alertmanager: <http://localhost:9093>
- Daemon metrics: <http://localhost:8088/actuator/prometheus>

### Watch the self-healing loop

Take a node offline and watch the system detect and react:

```bash
./faultinjection/kill-node.sh node2
docker compose logs -f remediation-daemon
```

You'll see, within ~15–25s:

- Prometheus flips `NodeDown` to **firing** for `node2`
- Alertmanager POSTs the webhook to the daemon
- The daemon logs `FIRING … → executing runbook 'cordon-node.sh'` then `REMEDIATED …`
- The Grafana panels **Nodes Up**, **Alerts Received**, and **Remediations Executed** update

Bring the node back and the alert resolves:

```bash
docker compose start node2
```

The daemon logs `RESOLVED … node recovered` and clears the cooldown.

> **CPU / disk alerts:** node_exporter inside a container can't be stressed
> meaningfully, so `HighCPU` and `DiskPressure` are best demonstrated on the KVM
> nodes (or any monitored Linux host) using `faultinjection/stress-cpu.sh` and
> `faultinjection/fill-disk.sh`. The `NodeDown` demo above works everywhere.

---

## Production path (real KVM cluster)

On a Linux host with virtualization tooling installed:

```bash
sudo ./infra/provision-cluster.sh
virsh net-dhcp-leases default          # get the node IPs
# add IP:9100 targets to prometheus/prometheus.yml, then run the stack
```

cloud-init installs `node_exporter` on each VM automatically. This is the setup
the resume line refers to — a genuine 3-node KVM/QEMU cluster under libvirt.

---

## How remediation stays safe

The daemon executes shell scripts, so it's deliberately locked down (all worth
explaining in an interview):

- **Whitelist:** only scripts in the configured `alertname → runbook` map can run.
  Even an explicit `runbook` annotation on an alert is honoured only if it's already whitelisted.
- **No shell string:** runbooks run via `ProcessBuilder` with an argument *list*,
  so alert label values are never interpreted by a shell — no command injection.
- **Argument validation:** node/instance args must match a strict regex or they're rejected.
- **Path-traversal guard:** the resolved script path must stay inside the runbooks directory.
- **Cooldown / dedup:** the same runbook won't re-fire for the same node within a
  configurable window, preventing remediation storms / flapping.
- **Hard timeout:** every runbook is force-killed if it overruns.

Runbooks themselves are conservative — `disk-cleanup.sh` is dry-run by default and
only ever touches well-known safe paths, never a blanket delete.

---

## Metrics the daemon exposes

Scraped by Prometheus at `/actuator/prometheus`:

- `clusterpulse_alerts_received_total{status}`
- `clusterpulse_remediations_total{runbook,outcome}` — outcome ∈ success / failure / cooldown / no_runbook
- `clusterpulse_runbook_duration_seconds{runbook}`

### Measuring MTTR (for the resume bullet)

The daemon logs a timestamp when it receives a firing alert and when the runbook
completes. Compare that to the manual baseline (time for a human to notice the
alert, SSH in, and run the fix). Use **your own measured numbers** in the resume
bullet rather than inventing them — e.g. "reduced simulated MTTR from ~4 min
(manual) to ~8 s (automated)".

---

## How this maps to the Nutanix SRE role

| JD requirement                              | Where it shows up here                                    |
|---------------------------------------------|-----------------------------------------------------------|
| Cluster health monitoring, quick response   | The whole detect → alert → auto-remediate loop            |
| Troubleshooting / root-cause / resolution   | Runbooks + structured remediation logs                    |
| Virtualization                              | KVM/QEMU cluster provisioned via libvirt + cloud-init     |
| Linux, Networking, Operating Systems        | node_exporter metrics, systemd runbooks, TCP scrape targets |
| Auto-support / serviceability tooling       | The daemon + fault-injection harness                      |

**Likely interview questions this prepares you for:** How does node_exporter
expose metrics? What's the difference between Prometheus scraping and pushing?
How does Alertmanager decide when to fire? How do you prevent an auto-remediation
loop from making an outage worse? (Answer: cooldown, idempotent runbooks, and
cordon-before-restart.) What is hyperconverged infrastructure? (Compute + storage
+ networking collapsed into one software layer on commodity x86 — Nutanix's core.)

---

## Roadmap ideas

- Add a `blackbox_exporter` for `ServiceDown` HTTP checks
- Persist remediation history to Postgres for an audit trail
- Add an Ansible-based runbook for multi-step recovery
- Chaos schedule that randomly injects faults to validate the loop continuously
