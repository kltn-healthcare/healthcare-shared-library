def call(String changedServices, String jobBaseName) {
  if (!changedServices?.trim()) {
    return
  }

  def scannerHome = tool env.SONAR_SCANNER

  changedServices.split(',').each { rawService ->
    def serviceName = rawService.trim()
    if (!serviceName) {
      return
    }

    def sourceDir = serviceName == 'frontend' ? './frontend' : './backend'
    def dockerfileDir = serviceName == 'frontend' ? './frontend/Dockerfile' : './backend/Dockerfile'

    withSonarQubeEnv(env.SONAR_SERVER) {
      sh """
        ${scannerHome}/bin/sonar-scanner \
          -Dsonar.projectKey=${jobBaseName}-${serviceName} \
          -Dsonar.sources=${sourceDir}
      """
    }

    // Require Quality Gate to pass, abort pipeline if it fails
    timeout(time: 5, unit: 'MINUTES') {
      def qg = waitForQualityGate()
      if (qg.status != 'OK') {
        error "Pipeline aborted due to SonarQube Quality Gate failure: ${qg.status}"
      }
    }

    sh """
      docker run --rm -i hadolint/hadolint:v2.14.0 hadolint \
        --failure-threshold style \
        - < ${dockerfileDir} 
    """

    sh """
      docker run --rm \
        -v /var/run/docker.sock:/var/run/docker.sock \
        -v \${HOME}/.cache/trivy:/root/.cache/trivy \
        -v \$(pwd):/workspace \
        -w /workspace \
        aquasec/trivy:0.69.3 fs \
          --scanners vuln,secret \
          --severity HIGH,CRITICAL \
          --exit-code 1 \
          --no-progress \
          ${sourceDir}
    """
  }
}
