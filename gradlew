#!/usr/bin/env sh

# Standard wrapper redirector or custom executor
if [ -x "$(command -v gradle)" ]; then
    exec gradle "$@"
else
    echo "Erro: Gradle local não encontrado no PATH. Instale o Gradle ou configure o Android Studio."
    exit 1
fi
