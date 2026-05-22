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

    // 1. Chuẩn hóa danh sách service
    def servicesList = config.changedServices.toString().tokenize(',').collect { it.trim() }.findAll { it }
    if (!servicesList) {
        echo "⏭️ Bỏ qua Deploy do không có thay đổi mã nguồn."
        return
    }

    // 2. Thiết lập đường dẫn thư mục và xác thực Git
    def workdir = config.workdir ?: 'manifests-workspace'
    def manifestRepoUrl = config.manifestRepoUrl.toString()
    def gitAuthUrl = config.gitAuthUrl ?: manifestRepoUrl.replaceFirst(/^https?:\/\//, "https://${config.token}@")

    echo "🚀 Bắt đầu Deploy Kustomize Manifest cho môi trường: [${config.environment.toString().toUpperCase()}]"

    // 3. Clone GitOps Repository
    sh "rm -rf '${workdir}'"
    sh "git clone '${gitAuthUrl}' '${workdir}'"
    sh "git -C '${workdir}' checkout '${config.manifestRepoBranch}'"

    // 4. Xử lý chuỗi Tag (Hỗ trợ cả String "tag123" hoặc Map "frontend=v1,backend=v2")
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

    // 5. Cập nhật Kustomize Manifest cho từng service
    servicesList.each { service ->
        // Linh hoạt đường dẫn: Ưu tiên lấy từ serviceFolderMap truyền vào, nếu không có thì lấy tên service làm tên thư mục
        def folderName = config.serviceFolderMap ? (config.serviceFolderMap[service] ?: service) : service
        def overlayPath = "apps/overlays/${config.environment}/${folderName}/kustomization.yaml"
        
        def fullPath = "${workdir}/${overlayPath}"
        if (!fileExists(fullPath)) {
            echo "⚠️ Cảnh báo: Không tìm thấy file Kustomize tại ${overlayPath}. Bỏ qua service [${service}]."
            return // Bỏ qua và chạy tiếp vòng lặp cho service khác
        }

        // Linh hoạt tên Repo (Không còn dính hardcode 'healthcare-')
        def imageName = "${config.giteaRegistry}/${config.giteaOwner}/${config.giteaRepo}-${service}"
        
        // Lấy đúng Tag tương ứng cho service
        def tagToUpdate = isTagMap ? (parsedTagMap[service] ?: 'latest') : config.imageTag
        
        echo "📝 Cập nhật Kustomize: ${service} -> Image: ${imageName} | Tag: ${tagToUpdate}"

        // Đọc và thay thế chuỗi yaml bằng Groovy thuần
        def content = readFile(fullPath)
        def updated = content
            .replaceAll(/(?m)^(\s*newName:\s*).*$/, "\$1${imageName}")
            .replaceAll(/(?m)^(\s*newTag:\s*).*$/, "\$1\"${tagToUpdate}\"")

        // Chỉ ghi lại nếu có sự thay đổi
        if (updated != content) {
            writeFile(file: fullPath, text: updated)
        }
    }

    // 6. Kiểm tra xem có gì mới để commit không
    sh "git -C '${workdir}' add ."
    def diffStatus = sh(script: "git -C '${workdir}' diff --cached --quiet", returnStatus: true)
    
    if (diffStatus == 0) {
        echo "👍 Không có sự thay đổi nào trong Manifest (Image/Tag đã là mới nhất)."
        return
    }

    // 7. Commit và Push lên GitOps Repo
    def safeCommitMessage = (config.commitMessage ?: "Deploy ${config.environment}: Cập nhật [${config.changedServices}]").replace("'", "'\"'\"'")

    sh """
        set -e
        git -C '${workdir}' -c user.name='${config.gitUserName}' -c user.email='${config.gitUserEmail}' \
        commit -m '${safeCommitMessage}'
        git -C '${workdir}' -c http.sslVerify=false push origin '${config.manifestRepoBranch}'
    """
    
    echo "✅ Đã đẩy cấu hình lên GitOps Repository thành công!"
}