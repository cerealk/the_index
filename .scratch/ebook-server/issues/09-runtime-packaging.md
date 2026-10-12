# Docker container or plain JVM process?

Type: grilling
Status: resolved
Labels: wayfinder:grilling

## Question

How does the server run on the user's LAN machine: Docker container (image build, volumes for storage, inbox and database) or a plain JVM process (service manager, install layout)? Which machine/OS is the host?

Note: the stack choice adds a PostgreSQL server to run alongside the app (see [Which server stack?](04-stack-choice.md)), so this also covers how Postgres is run (e.g. Docker Compose, or a host-installed Postgres).

## Answer

**Docker Compose runs both the app and PostgreSQL** (user's decision, 2026-10-12).

- Two services: the Ktor app (built into a Docker image) and Postgres (the official image).
- Storage, the inbox and Postgres data live on volumes mounted into the containers, so they outlive the containers.
- The host machine was left open on purpose: any machine that runs Docker will do.
- Left to implementation, not decided here: how the image is built (Dockerfile or Jib), bind mounts vs. named volumes, and the port mapping.
