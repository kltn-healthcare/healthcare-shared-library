def call(String changedServices, String imageTag) {
  if (!changedServices?.trim()) {
    return
  }

  withCredentials([string(credentialsId: "${env.GITEA_CREDS_ID}", variable: 'TOKEN')]) {
    sh """
      echo "\$TOKEN" | docker login ${env.GITEA_REGISTRY} \
        --username admin --password-stdin
    """
  }

  changedServices.split(',').each { rawService ->
    def serviceName = rawService.trim()
    if (!serviceName) {
      return
    }

    def imageName = "${env.GITEA_REGISTRY}/${env.GITEA_OWNER}/healthcare-${serviceName}"
    if (serviceName == 'frontend') {
      sh "docker build -f frontend/Dockerfile -t ${imageName}:${imageTag} frontend"
    } else {
      sh "docker build -f backend/Dockerfile --build-arg APP_NAME=${serviceName} -t ${imageName}:${imageTag} backend"
    }
    sh "docker push ${imageName}:${imageTag}"
  }
}
