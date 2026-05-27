def call(String baseCommit, String currentCommit) {
  def allServices = ['frontend', 'auth', 'backend', 'admin']
  def services = [] as LinkedHashSet

  // 1. Kiểm tra đầu vào. Nếu Jenkinsfile không truyền được commit (rỗng), 
  // mặc định build lại toàn bộ để đảm bảo an toàn (không sót service).
  if (!currentCommit) {
    error "Lỗi hệ thống: currentCommit bị rỗng. Dừng pipeline."
  }

  if (!baseCommit) {
    echo "Cảnh báo: Không tìm thấy baseCommit để so sánh git diff."
    echo "Áp dụng cơ chế Fail-safe: Trigger build TOÀN BỘ services."
    services.addAll(allServices)
    return services.join(',')
  }

  // 2. Chạy git diff dựa trên tham số truyền vào
  def diffOutput = sh(
    script: "git diff --name-only ${baseCommit} ${currentCommit}",
    returnStdout: true
  ).trim()
  
  def changedFiles = diffOutput ? diffOutput.split('\n') as List : []

  // 3. Phân tích các file thay đổi
  changedFiles.each { filePath ->
    if (filePath.startsWith('frontend/')) {
      services.add('frontend')
    }

    if (filePath.startsWith('backend/apps/auth/')) {
      services.add('auth')
    }

    if (filePath.startsWith('backend/apps/backend/')) {
      services.add('backend')
    }

    if (filePath.startsWith('backend/apps/admin/')) {
      services.add('admin')
    }

    // Các thay đổi ở core/shared/config của backend sẽ trigger build cả 3 microservices
    if (
      filePath.startsWith('backend/src/') ||
      filePath.startsWith('backend/libs/') ||
      filePath.startsWith('backend/prisma/') ||
      filePath == 'backend/Dockerfile' ||
      filePath == 'backend/package.json' ||
      filePath == 'backend/pnpm-lock.yaml' ||
      filePath == 'backend/tsconfig.json' ||
      filePath == 'backend/tsconfig.build.json' ||
      filePath == 'backend/nest-cli.json'
    ) {
      services.addAll(['auth', 'backend', 'admin'])
    }
  }

  if (services.isEmpty()) {
    echo 'No deployable service changes detected.'
  }

  return services.join(',')
}