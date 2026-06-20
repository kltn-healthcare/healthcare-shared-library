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

  def failedServices = []
  def imageNameMap = [
    'frontend': 'frontend',
    'auth'    : 'identity',
    'backend' : 'appointment',
    'admin'   : 'admin'
  ]

  changedServices.split(',').each { rawService ->
    def serviceName = rawService.trim()
    if (!serviceName) {
      return
    }

    try {
      def imageServiceName = imageNameMap[serviceName] ?: serviceName
      def imageName = "${env.GITEA_REGISTRY}/${env.GITEA_OWNER}/healthcare-${imageServiceName}"
      if (serviceName == 'frontend') {
        sh "docker build -f frontend/Dockerfile -t ${imageName}:${imageTag} frontend"
      } else {
        sh "docker build -f backend/Dockerfile --build-arg APP_NAME=${serviceName} -t ${imageName}:${imageTag} backend"
      }
      sh "docker push ${imageName}:${imageTag}"
    } catch (Exception e) {
      echo "Build or push failed for ${serviceName}: ${e.message}"
      failedServices.add(serviceName)
    }
  }

  if (failedServices) {
    error "Build failed for: ${failedServices.join(', ')}"
  }
}
