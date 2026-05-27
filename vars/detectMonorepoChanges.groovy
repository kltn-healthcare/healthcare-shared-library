def call(String baseCommit, String currentCommit, String rawChangedFiles = null) {
  def allServices = ['frontend', 'auth', 'backend', 'admin']
  def services = [] as LinkedHashSet

  List<String> changedFiles = []

  if (rawChangedFiles != null) {
    // ── Chế độ 1: File list truyền thẳng vào (dùng cho shallow clone + diff-tree) ──
    // Jenkinsfile.release đã tính sẵn danh sách file, không cần git diff
    if (!rawChangedFiles.trim()) {
      echo "rawChangedFiles rỗng → không có thay đổi."
      return ""
    }
    changedFiles = rawChangedFiles.split('\n').collect { it.trim() }.findAll { it }
    echo "Chế độ: rawChangedFiles (${changedFiles.size()} files)"

  } else {
    // ── Chế độ 2: Tính git diff từ 2 commit (dùng cho Jenkinsfile CI thông thường) ──
    if (!currentCommit) {
      error "currentCommit bị rỗng. Dừng pipeline."
    }

    if (!baseCommit) {
      echo "Không có baseCommit → Fail-safe: build toàn bộ services."
      services.addAll(allServices)
      return services.join(',')
    }

    def diffOutput = sh(
      script: "git diff --name-only ${baseCommit} ${currentCommit}",
      returnStdout: true
    ).trim()

    changedFiles = diffOutput ? diffOutput.split('\n').collect { it.trim() }.findAll { it } : []
    echo "Chế độ: git diff (${changedFiles.size()} files thay đổi)"
  }

  // ── Parse file list → service list (logic dùng chung cho cả 2 chế độ) ──
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

    // Thay đổi shared/core của backend → trigger build cả 3 service
    if (
      filePath.startsWith('backend/src/')        ||
      filePath.startsWith('backend/libs/')       ||
      filePath.startsWith('backend/prisma/')     ||
      filePath == 'backend/Dockerfile'           ||
      filePath == 'backend/package.json'         ||
      filePath == 'backend/pnpm-lock.yaml'       ||
      filePath == 'backend/tsconfig.json'        ||
      filePath == 'backend/tsconfig.build.json'  ||
      filePath == 'backend/nest-cli.json'
    ) {
      services.addAll(['auth', 'backend', 'admin'])
    }
  }

  if (services.isEmpty()) {
    echo "No deployable service changes detected."
  }

  return services.join(',')
}