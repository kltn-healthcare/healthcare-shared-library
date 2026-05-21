def call() {
  sh '''
    echo "=== CPU ===" && lscpu | grep -E "Model|Socket|Core|Thread" || true
    echo "=== RAM ===" && free -mh
    echo "=== Disk ===" && df -h
    echo "=== Docker ===" && docker version --format "Client: {{.Client.Version}}  Server: {{.Server.Version}}"
    printenv
  '''
}
