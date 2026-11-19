# Contributing

This is a feasibility-first research project: every change should either add
measured evidence or make an existing gate stricter. Read
[docs/experiment-protocol.md](docs/experiment-protocol.md) and
[docs/decisions.md](docs/decisions.md) first.

## Setup

- Python 3 (verified with 3.14) with `pytest` (`python -m pip install pytest`).
- JDK 17+ for the standalone JVM tests.
- For APK builds only: Android SDK API 37, Build Tools 36.0.0, JDK 21+
  (see [android/mobile-agent/README.md](android/mobile-agent/README.md)).
- For device experiments only: a Samsung Galaxy S26 Ultra with USB ADB and
  Shizuku/ShizukuPlus. Device scripts never run in CI.

## Running tests

```sh
python -m pytest tests/ scripts/ -q -p no:cacheprovider   # Python tests
python scripts/run-jvm-tests.py                             # pure-Java agent core
python scripts/validate-s26-model-lab.py --self-test        # Model Lab catalog
```

All three must pass before a pull request is opened. Tests that need a phone,
the Hub or network access must stay opt-in scripts under `scripts/`.

## Branches and commits

- Branch from `main` using `feat/...`, `fix/...`, `docs/...` or `chore/...`.
- Use [Conventional Commits](https://www.conventionalcommits.org/)
  (`feat(android): ...`, `fix: ...`, `docs: ...`, `test: ...`).
- Open a pull request into `main`; keep one logical change per PR.

## Documentation conventions

- Living documents use lowercase kebab-case names in `docs/`.
- Point-in-time experiment records are named `YYYY-MM-DD-topic.md` inside the
  matching topic folder and are indexed in [docs/README.md](docs/README.md).
- Raw evidence goes to `benchmarks/<milestone>/`; summaries to
  `benchmarks/results/`.
- Never commit secrets, device identifiers or personal content
  (see [SECURITY.md](SECURITY.md)).
