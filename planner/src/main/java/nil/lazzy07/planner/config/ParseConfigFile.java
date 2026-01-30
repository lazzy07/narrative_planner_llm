/*
* File name: ParseConfigFile.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-01-30 00:57:30
// Date modified: 2026-01-30 01:01:18
* ------
*/

package nil.lazzy07.planner.config;

import java.nio.file.Path;

public class ParseConfigFile {
  private Path configFilePath;

  public ParseConfigFile(String configFilePath) {
    this.configFilePath = Path.of(configFilePath);
  }

  public Path getConfigFilePath() {
    return this.configFilePath;
  }
}
