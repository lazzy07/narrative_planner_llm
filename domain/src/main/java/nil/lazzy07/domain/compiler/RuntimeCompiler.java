package nil.lazzy07.domain.compiler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class RuntimeCompiler {
  private static final Logger log = LoggerFactory.getLogger(RuntimeCompiler.class);

  public static void Compile(Path javaFile, Path outputDir, List<Path> classPath){
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

    if(compiler == null){
      log.error("JDK is required to run this program (not JRE)");
    }

    log.trace("JDK Found to compile the converter class");

    String cp = classPath.stream()
            .map(Path::toString)
            .reduce((a, b) -> a + File.pathSeparator + b)
            .orElse("");

    List<String> options = List.of(
            "-classpath", cp,
            "-d", outputDir.toString()
    );

    StandardJavaFileManager fm =
            compiler.getStandardFileManager(null, null, null);

    Iterable<? extends JavaFileObject> files =
            fm.getJavaFileObjects(javaFile.toFile());

    boolean success = compiler
            .getTask(null, fm, null, options, null, files)
            .call();

    try {
      fm.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    if (!success) {
      throw new RuntimeException("Compilation failed: " + javaFile);
    }

  }

  public static void Compile(String javaFile, String outputDir){
    Path javaPath = Path.of(javaFile);
    Path outputPath = Path.of(outputDir);

    List<Path> libs = List.of(
            Path.of("planner.jar")
    );

    Compile(javaPath, outputPath, libs);
  }
}
