def call(String scriptPath, String envName, String changedServices, String imageTag, String commitMessage) {
    if (!changedServices?.trim()) {
        echo "Bỏ qua Deploy do không có thay đổi mã nguồn."
        return
    }

    echo "🚀 Bắt đầu Deploy Kustomize Manifest cho môi trường: [${envName.toUpperCase()}]"
    
    withEnv([
        "ENVIRONMENT=${envName}",
        "SERVICES=${changedServices}",
        "IMAGE_TAG=${imageTag}",
        "COMMIT_MESSAGE=${commitMessage}" // <-- BƠM BIẾN NÀY XUỐNG BASH
    ]) {
        sh """
            export GIT_AUTH_URL=\$(echo \${MANIFEST_REPO_URL} | sed "s|https://|https://\${TOKEN}@|")
            chmod +x ${scriptPath}
            ${scriptPath}
        """
    }
}