# Bridge the Node-based memory MCP server (stdio-only) to SSE + StreamableHTTP
# for n8n's MCP Client Tool node.
#
# The @modelcontextprotocol/server-memory package speaks stdio only, so a proxy
# must LAUNCH it and expose a network transport. mcp-proxy (Python) does that,
# but its image has no Node runtime, so we add nodejs/npm here.
#
# mcp-proxy in server mode exposes BOTH transports:
#   - SSE:            /sse
#   - StreamableHTTP: /mcp
FROM ghcr.io/sparfenyuk/mcp-proxy:latest

USER root
RUN apk add --no-cache nodejs npm

# Persist the knowledge graph into a mounted volume (default is an ephemeral path).
ENV MEMORY_FILE_PATH=/data/memory.jsonl
EXPOSE 8000

# Bind 0.0.0.0 so it's reachable across the docker network. --pass-environment
# forwards MEMORY_FILE_PATH to the spawned server. The `--` terminates mcp-proxy's
# own option parsing so `-y` and later flags are handed to npx, not eaten by argparse.
ENTRYPOINT ["mcp-proxy", "--host", "0.0.0.0", "--port", "8000", "--pass-environment", \
            "--", "npx", "-y", "@modelcontextprotocol/server-memory"]
