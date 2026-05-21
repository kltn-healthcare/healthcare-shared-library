def call(String baseCommit, String currentCommit) {
  def allServices = ['frontend', 'auth', 'backend', 'admin']
  def services = [] as LinkedHashSet

  def resolvedBase = baseCommit ?: env.GIT_PREVIOUS_SUCCESSFUL_COMMIT ?: env.GIT_PREVIOUS_COMMIT
  def resolvedCurrent = currentCommit ?: env.GIT_COMMIT

  if (!resolvedBase) {
    try {
      resolvedBase = sh(script: 'git rev-parse HEAD~1', returnStdout: true).trim()
    } catch (ignored) {
      resolvedBase = null
    }
  }

  if (!resolvedBase || !resolvedCurrent) {
    services.addAll(allServices)
    return services.join(',')
  }

  def diffOutput = sh(
    script: "git diff --name-only ${resolvedBase} ${resolvedCurrent}",
    returnStdout: true
  ).trim()
  def changedFiles = diffOutput ? diffOutput.split('\n') as List : []

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
