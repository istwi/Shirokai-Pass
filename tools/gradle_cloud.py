#!/usr/bin/env python3
"""Run Gradle with inherited cloud HTTP proxy; never print proxy credentials."""
import os
from pathlib import Path
import subprocess
import sys
from urllib.parse import urlsplit

root = Path(__file__).resolve().parent.parent
env = os.environ.copy()
local_jdk = Path("/workspace/toolchains/jdk-21.0.13+9")
if "JAVA_HOME" not in env and (local_jdk / "bin/javac").is_file():
    env["JAVA_HOME"] = str(local_jdk)
env.setdefault("GRADLE_USER_HOME", "/workspace/.gradle-shirokai")
args = ["bash", str(root / "gradlew"), "--no-daemon"]
for scheme in ("http", "https"):
    raw = env.get(scheme.upper() + "_PROXY") or env.get(scheme + "_proxy")
    if raw:
        proxy = urlsplit(raw)
        if proxy.username or proxy.password:
            raise SystemExit("Authenticated proxy requires supported JVM credential configuration; refusing to expose credentials in arguments.")
        if proxy.hostname:
            args += [f"-D{scheme}.proxyHost={proxy.hostname}", f"-D{scheme}.proxyPort={proxy.port or 80}"]
# Debian truststore includes the cloud CA; retain full certificate validation.
truststore = Path("/etc/ssl/certs/java/cacerts")
if truststore.is_file():
    args += [f"-Djavax.net.ssl.trustStore={truststore}"]
args += ["-Dhttp.nonProxyHosts=localhost|127.*|[::1]"]
args += sys.argv[1:] or ["build"]
raise SystemExit(subprocess.call(args, cwd=root, env=env))
