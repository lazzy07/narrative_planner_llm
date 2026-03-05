/*
* File name: DateTimeGenerator.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-03-05 12:16:59
// Date modified: 2026-03-05 12:21:11
* ------
*/

package nil.lazzy07.common.datetime;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DateTimeGenerator {
  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS");
  private static String timestamp;

  public static void InitTime() {
    DateTimeGenerator.timestamp = LocalDateTime.now().format(FORMATTER);
  }

  public static String GetTimeStamp() {
    return DateTimeGenerator.timestamp;
  }
}
