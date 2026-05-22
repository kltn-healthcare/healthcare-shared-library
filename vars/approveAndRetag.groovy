def call(Map config) {
    def requiredKeys = [
        'services', 'adminEmail', 'commitTag', 'credsId', 
        'giteaApiUrl', 'giteaOwner', 'giteaRepo', 'giteaRegistry', 'gitCommit'
    ]

    def missing = requiredKeys.findAll { key -> !config[key] }
    if (missing) {
        error "Missing required config: ${missing.join(', ')}"
    }

    // 1. Tách chuỗi services
    def servicesList = config.services.toString().tokenize(',').collect { it.trim() }.findAll { it }
    if (!servicesList) {
        echo "Không có service nào cần phê duyệt."
        return [mainReleaseTag: '', serviceTags: '']
    }

    def servicesLabel = servicesList.join(',')
    def approvalUrl = config.runDisplayUrl ? "${config.runDisplayUrl}/input" : 'N/A'

    if (config.adminEmail) {
        try {
            mail(
                to: config.adminEmail,
                subject: "Jenkins Approval Required: ${servicesLabel}",
                body: "Vui lòng phê duyệt đưa các service [${servicesLabel}] lên Production.\nMã Commit hiện tại: ${config.commitTag}\n\n👉 Bấm vào đây để duyệt: ${approvalUrl}"
            )
            echo "📧 Đã gửi email thông báo phê duyệt đến: ${config.adminEmail}"
        } catch (Exception e) {
            // Bắt lỗi không làm sập pipeline nếu Jenkins chưa cấu hình SMTP
            echo "⚠️ Cảnh báo: Không thể gửi email thông báo (Có thể Jenkins chưa cấu hình SMTP). Lỗi chi tiết: ${e.message}"
            echo "👉 Quản trị viên vui lòng truy cập trực tiếp link sau để duyệt: ${approvalUrl}"
        }
    } else {
        echo "ℹ️ Không có adminEmail được cung cấp, bỏ qua bước gửi email."
        echo "👉 Quản trị viên vui lòng truy cập trực tiếp link sau để duyệt: ${approvalUrl}"
    }

    // 3. TẠO Ô NHẬP ĐỘNG
    def inputParams = []
    
    // Đã sửa mặc định thành rỗng và cập nhật mô tả
    inputParams.add(string(
        name: "MAIN_RELEASE_TAG",
        defaultValue: "", 
        description: "🔥 Nhập Release Tag chung (ĐỂ TRỐNG nếu KHÔNG muốn tạo Gitea Release)"
    ))

    for (service in servicesList) {
        inputParams.add(string(
            name: "${service}_TAG",
            defaultValue: 'v1.0.0',
            description: "Nhập Tag cho service: ${service}"
        ))
    }

    // 4. Mở bảng hỏi
    def userInput = input(
        id: 'production-approval',
        message: "Phê duyệt đưa lên Production cho: ${servicesLabel}?",
        ok: 'Approve & Retag',
        parameters: inputParams
    )

    // 5. Xử lý kết quả trả về
    def tagMapping = [:]
    def mainReleaseTag = userInput["MAIN_RELEASE_TAG"].toString().trim()

    for (service in servicesList) {
        tagMapping[service] = userInput["${service}_TAG"].toString().trim()
    }

    // 6. Chạy vòng lặp Retag
    tagMapping.each { svc, tag ->
        def imagePath = "${config.giteaRegistry}/${config.giteaOwner}/${config.giteaRepo}-${svc}"
        echo "📦 Xử lý Retag cho ${svc} -> Phiên bản: ${tag}"
        sh """
            set -e
            echo "⬇️ Pulling: ${imagePath}:${config.commitTag}"
            docker pull '${imagePath}:${config.commitTag}'
            echo "🏷️ Retagging: ${config.commitTag} ➡️ ${tag}"
            docker tag '${imagePath}:${config.commitTag}' '${imagePath}:${tag}'
            echo "⬆️ Pushing: ${imagePath}:${tag}"
            docker push '${imagePath}:${tag}'
        """
    }

    def resultString = tagMapping.collect { "${it.key}=${it.value}" }.join(',')

    // 7. Tạo Release trên Gitea (CHỈ TẠO KHI CÓ MAIN_RELEASE_TAG)
    if (mainReleaseTag) {
        echo "🎉 Đang tạo Gitea Release cho phiên bản: ${mainReleaseTag}..."
        def releaseName = config.releaseName ?: "Release ${mainReleaseTag}"
        def releaseBody = config.releaseBody ?: "Cập nhật các services:\n${resultString}"

        def payloadMap = [
            tag_name: mainReleaseTag,
            name: releaseName,
            body: releaseBody
        ]

        def targetCommitish = config.targetCommitish ?: config.gitCommit
        if (targetCommitish?.toString()?.trim()) {
            payloadMap.target_commitish = targetCommitish.toString().trim()
        }

        writeFile(file: 'gitea-release.json', text: groovy.json.JsonOutput.toJson(payloadMap))

        withCredentials([string(credentialsId: config.credsId, variable: 'SECRET_TOKEN')]) {
            sh """
                set -e
                curl --silent --show-error --fail-with-body \\
                  -X POST '${config.giteaApiUrl}/repos/${config.giteaOwner}/${config.giteaRepo}/releases' \\
                  -H 'accept: application/json' \\
                  -H "Authorization: token \$SECRET_TOKEN" \\
                  -H 'Content-Type: application/json' \\
                  --data @gitea-release.json
            """
        }
    } else {
        echo "⏭️ Bỏ qua bước tạo Gitea Release vì ô MAIN_RELEASE_TAG bị bỏ trống."
    }

    return [mainReleaseTag: mainReleaseTag, serviceTags: resultString]
}