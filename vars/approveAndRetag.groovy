def call(String commitHashTag, String adminEmail) {
  mail(
    to: adminEmail,
    subject: 'Jenkins approval required',
    body: "Approve production promotion: ${env.RUN_DISPLAY_URL}/input"
  )

  def TAG_NAME = input(
    id: 'production-approval',
    message: 'Approve promotion to production?',
    ok: 'Approve',
    parameters: [
      string(
        name: 'TAG_NAME',
        defaultValue: 'v1.0.0',
        description: 'Release tag for production'
      )
    ]
  ).toString().trim()

  sh "./scripts/retag-and-release.sh ${commitHashTag} ${TAG_NAME}"

  return TAG_NAME
}
