#!/usr/bin/env python3
"""
Hands spec/refunds.md to a coding agent and lets it work until every visible gate
passes. One run, kept whatever it produces: no retries, no hand edits.

    python3 experiments/run-agent.py

What the agent gets: a copy of the repository at the `baseline` tag WITHOUT hidden/,
the prompt in experiments/agent-run/prompt.md, and file, build and gate tools only.
What it never gets: the hidden acceptance tests, the internet, any plugin, hook or
MCP server from the machine it runs on.

Writes experiments/agent-run/: transcript.jsonl (every event, timestamped),
result.json (model, duration, turns), and agent.patch (its complete change).
"""
import json, os, shutil, subprocess, sys, tempfile, time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "experiments" / "agent-run"
MODEL = "claude-opus-5-5"
TIMEOUT_S = 2 * 60 * 60
TOOLS = "Bash,Read,Edit,Write,Glob,Grep"
ALLOWED = [
    "Read", "Edit", "Write", "Glob", "Grep",
    "Bash(scripts/gates.sh:*)", "Bash(./scripts/gates.sh:*)", "Bash(./mvnw:*)", "Bash(cd:*)",
    "Bash(ls:*)", "Bash(cat:*)", "Bash(head:*)", "Bash(tail:*)", "Bash(grep:*)",
    "Bash(find:*)", "Bash(mkdir:*)", "Bash(git status:*)", "Bash(git diff:*)",
]
SETTINGS = {"disableAllHooks": True, "includeCoAuthoredBy": False, "enabledPlugins": {}}


def workspace() -> Path:
    work = Path(tempfile.mkdtemp(prefix="refunds-agent-"))
    archive = subprocess.run(["git", "archive", "baseline"], cwd=ROOT, capture_output=True, check=True).stdout
    subprocess.run(["tar", "-x", "-C", str(work)], input=archive, check=True)
    shutil.rmtree(work / "hidden")
    shutil.rmtree(work / "experiments", ignore_errors=True)
    (work / "CLAUDE.md").write_text("A working copy for one coding task. Work only inside this directory.\n")
    git = ["git", "-c", "user.name=agent", "-c", "user.email=agent@example.com"]
    subprocess.run(["git", "init", "-q"], cwd=work, check=True)
    subprocess.run(git + ["add", "-A"], cwd=work, check=True)
    subprocess.run(git + ["commit", "-qm", "baseline"], cwd=work, check=True)
    return work


def main() -> int:
    if (OUT / "result.json").exists():
        print("A run already exists. The first run is the one we keep.", file=sys.stderr)
        return 1
    OUT.mkdir(parents=True, exist_ok=True)
    prompt = (OUT / "prompt.md").read_text()
    work = workspace()
    settings = work.parent / (work.name + "-settings.json")
    settings.write_text(json.dumps(SETTINGS))
    mcp = work.parent / (work.name + "-mcp.json")
    mcp.write_text(json.dumps({"mcpServers": {}}))
    cmd = ["claude", "-p", prompt, "--model", MODEL,
           "--tools", TOOLS, "--allowedTools", *ALLOWED,
           "--settings", str(settings), "--setting-sources", "project",
           "--mcp-config", str(mcp), "--strict-mcp-config",
           "--disable-slash-commands", "--no-chrome",
           "--output-format", "stream-json", "--verbose"]
    start = time.monotonic()
    result = None
    with (OUT / "transcript.jsonl").open("w") as log:
        proc = subprocess.Popen(cmd, cwd=work, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, text=True)
        for line in proc.stdout:
            now = round(time.monotonic() - start, 3)
            log.write(json.dumps({"t": now, "line": line.rstrip("\n")}) + "\n")
            log.flush()
            try:
                event = json.loads(line)
            except json.JSONDecodeError:
                continue
            if event.get("type") == "result":
                result = event
            if now > TIMEOUT_S:
                proc.kill()
        proc.wait()
    elapsed = round(time.monotonic() - start, 1)
    patch = subprocess.run(["git", "add", "-A"], cwd=work) and \
        subprocess.run(["git", "diff", "--cached", "HEAD", "--", ".", ":!CLAUDE.md", ":!target-gates"],
                       cwd=work, capture_output=True, text=True).stdout
    (OUT / "agent.patch").write_text(patch)
    summary = {"model": MODEL, "claude_code": subprocess.run(["claude", "--version"], capture_output=True,
               text=True).stdout.strip(), "seconds": elapsed, "workspace": str(work),
               "turns": (result or {}).get("num_turns"), "stop": (result or {}).get("subtype")}
    (OUT / "result.json").write_text(json.dumps(summary, indent=1) + "\n")
    print(json.dumps(summary, indent=1))
    return 0


if __name__ == "__main__":
    sys.exit(main())
