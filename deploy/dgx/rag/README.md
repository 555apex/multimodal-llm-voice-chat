# DGX RAG candidate build notes

For day-to-day WeKnora access, model/knowledge-base maintenance, and RoadAgent
user examples, see [OPERATIONS.md](OPERATIONS.md).

The candidate uses WeKnora 0.6.3 at commit
`7ddac0385fc7ec78b30da0a6f9bc13da7d672517`. The app, UI, and
docreader images are tagged `0.6.3-7ddac038`; production deployment should
pin their resulting image digests.

DGX needs these build-only adjustments to the fixed WeKnora source archive:

1. `docker/Dockerfile.app`: use a reachable PyPI mirror plus pip retry/timeout
   options for the runtime pip upgrade. The application source is unchanged.
2. `docker/Dockerfile.docreader`: install `uv` through the same mirror; run
   `uv sync --frozen --no-dev` with `UV_CONCURRENT_DOWNLOADS=4` and
   `UV_HTTP_TIMEOUT=600`. Keep the lockfile's package versions and hashes.
3. The source lockfile embeds `files.pythonhosted.org` wheel URLs. In the DGX
   deployment copy, map `https://files.pythonhosted.org/packages/` to
   `https://mirrors.aliyun.com/pypi/packages/` and
   `https://pypi.org/simple` to
   `https://mirrors.aliyun.com/pypi/simple`. Do not change versions or hashes.
4. The migrated corpus contains 49 PDF, 5 DOC, and 3 DOCX files. The
   docreader image was built without its optional Playwright WebKit browser
   install because the WebKit CDN repeatedly reset downloads. File parsing is
   in scope; web-page parsing needs a separate browser-enabled image later.
5. The upstream `grpc_health_probe` GitHub download was removed from the
   docreader image. `compose.rag.yaml` checks that the gRPC port accepts a
   TCP connection instead. This is a startup check, not a full gRPC readiness
   probe.

The WeKnora app image already contains built-in skills. Do not bind-mount
`skills/preloaded` read-only: its startup script changes ownership of that
directory and otherwise exits before the app starts.

Both UIs need a non-internal ingress bridge in addition to their private
Docker network for DGX Docker 29 to publish loopback ports. WeKnora uses
`127.0.0.1:18082`; RoadAgent candidate uses `127.0.0.1:18083`.
