def call(String envName, String changedServices, String imageTag, String commitMessage) {
  sh "./scripts/update-k8s-manifest.sh ${envName} ${changedServices} ${imageTag} '${commitMessage}'"
}
