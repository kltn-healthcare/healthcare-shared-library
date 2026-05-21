def call(String changedServices, String imageTag, String registry, String owner) {
  if (!changedServices?.trim()) {
    return
  }

  withCredentials([string(credentialsId: "${GITEA_CREDS_ID}", variable: 'TOKEN')]) {
    sh """
      echo "\$TOKEN" | docker login ${registry} \
        --username admin --password-stdin
    """
  }

  changedServices.split(',').each { rawService ->
    def serviceName = rawService.trim()
    if (!serviceName) {
      return
    }

    def imageName = "${registry}/${owner}/healthcare-${serviceName}"
    if (serviceName == 'frontend') {
      sh "docker build -f frontend/Dockerfile -t ${imageName}:${imageTag} frontend"
    } else {
      sh "docker build -f backend/Dockerfile --build-arg APP_NAME=${serviceName} -t ${imageName}:${imageTag} backend"
    }
    sh "docker push ${imageName}:${imageTag}"
  }
}
