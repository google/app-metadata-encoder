package com.android.tools.metadata;

import com.google.common.io.Resources;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import org.junit.rules.TemporaryFolder;

/** Helper methods for accessing test resources. */
public final class TestData {

  public static File getTestFile(String testFileName, TemporaryFolder tempFolder) throws Exception {
    Path outputFile = tempFolder.newFolder().toPath().resolve(testFileName);

    try (FileOutputStream fileOutputStream = new FileOutputStream(outputFile.toFile())) {
      Resources.copy(
          Resources.getResource(TestData.class, "testdata/" + testFileName), fileOutputStream);
    }
    return outputFile.toFile();
  }

  private TestData() {}
}
