/*
* File name: JsonUtils.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-03-03 19:36:24
// Date modified: 2026-03-05 12:25:09
* ------
*/

package nil.lazzy07.planner.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import nil.lazzy07.common.datetime.DateTimeGenerator;

public class JsonUtils {
  public static void saveToJson(String fileName, Path outputDirectory, String json) {
    Path outputFile = outputDirectory.resolve(fileName);

    try {
      // 1️⃣ Ensure directory exists
      Files.createDirectories(outputDirectory);

      Files.writeString(
          outputFile,
          json,
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE_NEW);
    } catch (IOException e) {
      throw new RuntimeException("Failed to save the file: " + fileName, e);
    }
  }

  public static void saveToJson(Path outputDirectory, String json) {
    String timestamp = DateTimeGenerator.GetTimeStamp();
    String fileName = timestamp + ".json";
    saveToJson(fileName, outputDirectory, json);
  }
}
