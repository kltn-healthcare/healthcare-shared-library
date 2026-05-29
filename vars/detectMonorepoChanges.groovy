def call(String baseCommit, String currentCommit, String rawChangedFiles = null) {
  def allServices = ['frontend', 'auth', 'backend', 'admin']
  def services = [] as LinkedHashSet

  List<String> changedFiles = []

  if (rawChangedFiles != null) {
    // Mode 1: file list provided directly (used for shallow clone + diff-tree).
    // Jenkinsfile.release already computed the file list, no git diff needed.
    if (!rawChangedFiles.trim()) {
      echo "rawChangedFiles is empty -> no changes."
      return ""
    }
    changedFiles = rawChangedFiles.split('\n').collect { it.trim() }.findAll { it }
    echo "Mode: rawChangedFiles (${changedFiles.size()} files)"

  } else {
    // Mode 2: compute git diff between two commits (used for standard Jenkinsfile CI).
    if (!currentCommit) {
      error "currentCommit is empty. Stopping pipeline."
    }

    if (!baseCommit) {
      echo "No baseCommit -> fail-safe: build all services."
      services.addAll(allServices)
      return services.join(',')
    }

    def diffOutput = sh(
      script: "git diff --name-only ${baseCommit} ${currentCommit}",
      returnStdout: true
    ).trim()

    changedFiles = diffOutput ? diffOutput.split('\n').collect { it.trim() }.findAll { it } : []
    echo "Mode: git diff (${changedFiles.size()} files changed)"
  }

  // Parse file list into service list (shared logic for both modes).
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

    // Backend shared/core changes -> trigger build for all three services.
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