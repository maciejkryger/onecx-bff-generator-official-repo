FROM ghcr.io/onecx/docker-quarkus-native:${dockerNativeVersion}

COPY --chown=1001:root target/*-runner /work/application
