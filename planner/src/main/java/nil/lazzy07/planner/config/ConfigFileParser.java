/*
* File name: ParseConfigFile.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-01-30 00:57:30
// Date modified: 2026-02-02 21:57:03
* ------
*/

package nil.lazzy07.planner.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ConfigFileParser {

  private static final Logger log = LoggerFactory.getLogger(ConfigFileParser.class);
  private static Path configFilePath;
  private static ObjectMapper mapper;
  private static ConfigFile configFile;

  @SuppressWarnings("deprecation")
  public static void Init(String configFilePath) {
    ConfigFileParser.configFilePath = Path.of(configFilePath);
    log.trace("Config File Path is set to: " + ConfigFileParser.configFilePath);

    ConfigFileParser.mapper = new ObjectMapper().configure(JsonParser.Feature.ALLOW_COMMENTS, true)
        .configure(JsonParser.Feature.ALLOW_YAML_COMMENTS, true)
        .configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true);
  }

  public static ConfigFile GetConfigFile() {
    if (ConfigFileParser.configFile != null) {
      log.trace("Config file is already cached, serving the file");
      return ConfigFileParser.configFile;
    }

    log.trace("Config file is not read yet, reading the file");

    try {
      return mapper.readValue(Files.readString(ConfigFileParser.GetConfigFilePath()), ConfigFile.class);
    } catch (JsonMappingException e) {
      log.error("Config file mapping failed");
      log.error(e.getMessage());
    } catch (JsonProcessingException e) {
      log.error("Json file processing failed");
      log.error(e.getMessage());
    } catch (IOException e) {
      log.error("Config file read error: does the file exists?");
      log.error(e.getMessage());
    }

    return null;
  }

  public static Path GetConfigFilePath() {
    return ConfigFileParser.configFilePath;
  }
}
