def call(Map config) {
    def requiredKeys = [
        'environment', 'changedServices', 'manifestRepoUrl',
        'manifestRepoBranch', 'gitUserName', 'gitUserEmail',
        'giteaRegistry', 'giteaOwner', 'giteaRepo', 'token'
    ]

    def missing = requiredKeys.findAll { key -> !config[key] }
    if (missing) {
        error "Missing required config: ${missing.join(', ')}"
    }

    // 1. Normalize the service list.
    def servicesList = config.changedServices.toString().tokenize(',').collect { it.trim() }.findAll { it }
    if (!servicesList) {
        echo "Skipping deploy because no source changes were detected."
        return
    }

    // 2. Set up the work directory and Git authentication.
    def workdir = config.workdir ?: 'manifests-workspace'
    def manifestRepoUrl = config.manifestRepoUrl.toString()
    def gitAuthUrl = config.gitAuthUrl ?: manifestRepoUrl.replaceFirst(/^https?:\/\//, "https://${config.token}@")

    echo "Starting Kustomize manifest deploy for environment: [${config.environment.toString().toUpperCase()}]"

    // 3. Clone the GitOps repository.
    sh "rm -rf '${workdir}'"
    sh "git clone '${gitAuthUrl}' '${workdir}'"
    sh "git -C '${workdir}' checkout '${config.manifestRepoBranch}'"

    // 4. Handle tag input (supports "tag123" or "frontend=v1,backend=v2").
    def isTagMap = config.imageTag?.contains('=')
    def parsedTagMap = [:]
    if (isTagMap) {
        config.imageTag.split(',').each { pair ->
            def parts = pair.split('=')
            if (parts.size() == 2) {
                parsedTagMap[parts[0].trim()] = parts[1].trim()
            }
        }
    }

    // 5. Update the Kustomize manifest for each service.
    servicesList.each { service ->
        // Flexible path: prefer serviceFolderMap if provided; otherwise use the service name.
        def folderName = config.serviceFolderMap ? (config.serviceFolderMap[service] ?: service) : service
        def overlayPath = "apps/overlays/${config.environment}/${folderName}/kustomization.yaml"
        
        def fullPath = "${workdir}/${overlayPath}"
        if (!fileExists(fullPath)) {
            echo "Warning: Kustomize file not found at ${overlayPath}. Skipping service [${service}]."
            return // Skip and continue with the next service.
        }

        // Flexible repo name (no hardcoded 'healthcare-').
        def imageName = "${config.giteaRegistry}/${config.giteaOwner}/${config.giteaRepo}-${service}"
        
        // Select the correct tag for the service.
        def tagToUpdate = isTagMap ? (parsedTagMap[service] ?: 'latest') : config.imageTag
        
        echo "Updating Kustomize: ${service} -> Image: ${imageName} | Tag: ${tagToUpdate}"

        // Read and update the YAML content.
        def content = readFile(fullPath)
        def updated = content
            .replaceAll(/(?m)^(\s*newName:\s*).*$/, "\$1${imageName}")
            .replaceAll(/(?m)^(\s*newTag:\s*).*$/, "\$1\"${tagToUpdate}\"")

        // Only write when there are changes.
        if (updated != content) {
            writeFile(file: fullPath, text: updated)
        }
    }

    // 6. Check whether there is anything to commit.
    sh "git -C '${workdir}' add ."
    def diffStatus = sh(script: "git -C '${workdir}' diff --cached --quiet", returnStatus: true)
    
    if (diffStatus == 0) {
        echo "No manifest changes detected (image/tag already current)."
        return
    }

    // 7. Commit and push to the GitOps repository.
    def safeCommitMessage = (config.commitMessage ?: "Deploy ${config.environment}: update [${config.changedServices}]").replace("'", "'\"'\"'")

    sh """
        set -e
        git -C '${workdir}' -c user.name='${config.gitUserName}' -c user.email='${config.gitUserEmail}' \
        commit -m '${safeCommitMessage}'
        git -C '${workdir}' -c http.sslVerify=false push origin '${config.manifestRepoBranch}'
    """
    
    echo "Manifests pushed to the GitOps repository successfully."
}