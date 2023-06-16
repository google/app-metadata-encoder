/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
