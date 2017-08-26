import org.slf4j.Logger

logger.info("pre-hook triggered by ${user.username} for ${repository.name}: checking ${commands.size} commands")

return true
