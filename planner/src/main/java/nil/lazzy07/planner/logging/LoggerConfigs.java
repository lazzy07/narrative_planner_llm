/*
* File name: LoggerConfigs.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-01-27 01:47:27
// Date modified: 2026-01-27 01:50:36
* ------
*/

package nil.lazzy07.planner.logging;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;

public class LoggerConfigs {
  public static void configureLoggingLevel(String level) {
    LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
    ctx.getLogger("nil.lazzy07").setLevel(Level.valueOf(level));
  }
}
