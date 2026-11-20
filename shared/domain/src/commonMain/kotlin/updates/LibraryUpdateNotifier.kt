package app.koyura.domain.updates

/** Platform delivery boundary for completed library checks. */
fun interface LibraryUpdateNotifier {
    suspend fun notify(check: ScheduledUpdateCheck)
}
