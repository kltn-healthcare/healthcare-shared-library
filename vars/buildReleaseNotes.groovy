def call(Map config) {
    def requiredKeys = [
        'environment', 'manifestRepoUrl', 'manifestRepoBranch', 'token'
    ]

    def missing = requiredKeys.findAll { key -> !config[key] }
    if (missing) {
        error "Missing required config: ${missing.join(', ')}"
    }

    def serviceFolderMap = config.serviceFolderMap ?: [:]
    def servicesList = []

    if (config.services) {
        servicesList = config.services.toString().tokenize(',').collect { it.trim() }.findAll { it }
    } else if (serviceFolderMap) {
        servicesList = serviceFolderMap.keySet().toList().sort()
    } else {
        servicesList = ['frontend', 'auth', 'backend', 'admin']
    }

    def workdir = config.workdir ?: 'manifests-release-notes'
    def manifestRepoUrl = config.manifestRepoUrl.toString()
    def gitAuthUrl = config.gitAuthUrl ?: manifestRepoUrl.replaceFirst(/^https?:\/\//, "https://${config.token}@")

    sh "rm -rf '${workdir}'"
    sh "git clone '${gitAuthUrl}' '${workdir}'"
    sh "git -C '${workdir}' checkout '${config.manifestRepoBranch}'"

    def tags = [:]

    servicesList.each { service ->
        def folderName = serviceFolderMap ? (serviceFolderMap[service] ?: service) : service
        def overlayPath = "apps/overlays/${config.environment}/${folderName}/kustomization.yaml"
        def fullPath = "${workdir}/${overlayPath}"

        if (!fileExists(fullPath)) {
            tags[service] = 'missing'
            return
        }

        def content = readFile(fullPath)
        def match = (content =~ /(?m)^\s*newTag:\s*"?([^"\n]+)"?\s*$/)
        def tagValue = match ? match[0][1].trim() : ''
        tags[service] = tagValue ?: 'unknown'
    }

    def notes = servicesList.collect { service ->
        def tagValue = tags[service] ?: 'unknown'
        "- healthcare-${service}: ${tagValue}"
    }.join('\n')

    return [tags: tags, notes: notes]
}
