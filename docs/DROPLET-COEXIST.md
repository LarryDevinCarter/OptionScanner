# Droplet coexistence: OptionScanner + nexstep

Run both Spring Boot services on the same DigitalOcean **$6 / 1 GiB** droplet without stepping on each other.

## Layout

| Service | Path | Port | systemd unit |
|---------|------|------|--------------|
| nexstep mail API | `/var/www/nexstep` | (existing; typically 8080) | `nexstep.service` |
| OptionScanner | `/var/www/optionscanner` | **8081** | `optionscanner.service` |
| Postgres | local | 5432 | `postgresql` (shared) |

OptionScanner install/deploy scripts **must never** stop or rewrite `nexstep.service`.

## RAM budget (1 GiB)

Rough steady-state target:

| Component | Heap / limit | Notes |
|-----------|--------------|-------|
| nexstep | ~**64–128m** heap, `MemoryMax≈256M` | Dialed back so OptionScanner fits |
| OptionScanner | **`-Xms128m -Xmx256m`**, `MemoryMax=400M` | Hourly refresh is the spike risk |
| Postgres | shared OS + shared_buffers | Keep modest; one instance for both DBs |
| OS / SSH / misc | ~200–300M | Kernel, sshd, journald |

Leave headroom. On 1 GiB, **two JVMs + Postgres** is tight.

## Secrets

- Droplet file: `/var/www/optionscanner/application.properties` (gitignored / not in repo)
- Or `SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/var/www/optionscanner/`
- Never commit Alpaca / Alpha Vantage / DB passwords

nexstep keeps its own config under `/var/www/nexstep` (or packed historically — rotate if it was ever committed).

## Monitoring

```bash
free -h
systemctl status nexstep.service optionscanner.service postgresql --no-pager
journalctl -u optionscanner.service -n 100 --no-pager
journalctl -u nexstep.service -n 50 --no-pager
```

Watch for OOM killer:

```bash
sudo dmesg -T | grep -i -E 'oom|killed process' | tail
```

## Hourly refresh / OOM risk

OptionScanner’s scheduled refresh (`optionscanner.refresh.cron`, batch ~230 symbols) can spike CPU and RSS when loading chains/fundamentals. On a 1 GiB box that can push the host into **OOM**, which may kill OptionScanner **or** nexstep (or Postgres).

Mitigations:

1. Keep OptionScanner heap at **256m** max and `MemoryMax=400M`.
2. Keep nexstep at **128m** max (see nexstep `docs/DROPLET-SIZING.md`).
3. Lower `optionscanner.refresh.batch-size` if you see swap thrash or OOM.
4. Prefer off-peak cron if mail API latency matters during refresh.

## Rollback

OptionScanner CI keeps `backup.jar` beside the live jar:

```bash
sudo systemctl stop optionscanner.service
sudo cp /var/www/optionscanner/backup.jar /var/www/optionscanner/option-scanner-0.0.1-SNAPSHOT.jar
sudo systemctl start optionscanner.service
```

To remove OptionScanner without touching nexstep:

```bash
sudo systemctl disable --now optionscanner.service
# optional: rm unit + /var/www/optionscanner (keep DB if you want)
```

## First-time install

```bash
# from a checkout that has deploy/
sudo bash deploy/install.sh
# place jar + application.properties, then:
sudo systemctl start optionscanner.service
```

GitHub Actions (`deploy/ci-deploy.yml` (promote to `.github/workflows/deploy.yml` when the GitHub token has `workflow` scope)) builds JDK 21, SCPs the jar, and restarts **only** `optionscanner.service`. Required secrets: `SSH_HOST`, `SSH_USERNAME`, `SSH_PRIVATE_KEY`.
