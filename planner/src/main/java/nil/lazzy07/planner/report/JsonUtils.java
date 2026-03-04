/*
* File name: JsonUtils.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-03-03 19:36:24
// Date modified: 2026-03-03 19:36:34
* ------
*/

package nil.lazzy07.planner.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class JsonUtils {

  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS");

  public static void saveToJson(Path outputDirectory, String json) {
    try {
      // 1️⃣ Ensure directory exists
      Files.createDirectories(outputDirectory);

      // 2️⃣ Generate timestamp filename
      String timestamp = LocalDateTime.now().format(FORMATTER);
      String fileName = timestamp + ".json";

      Path outputFile = outputDirectory.resolve(fileName);

      // 3️⃣ Write file (fail if somehow already exists — extremely unlikely)
      Files.writeString(
          outputFile,
          json,
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE_NEW);

    } catch (IOException e) {
      throw new RuntimeException(
          "Failed to save JSON to directory: " + outputDirectory,
          e);
    }
  }
}
