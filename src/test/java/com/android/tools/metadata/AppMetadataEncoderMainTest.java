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

import static com.android.tools.metadata.LoggingUtils.configureCommandLine;
import static com.android.tools.metadata.TestData.getTestFile;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.common.testing.TestLogHandler;
import com.google.crypto.tink.BinaryKeysetWriter;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.hybrid.HybridConfig;
import java.io.File;
import java.io.FileOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import picocli.CommandLine;

@RunWith(JUnit4.class)
public final class AppMetadataEncoderMainTest {
  private static final String ENCODE_COMMAND = "encode";
  private static final String VERBOSE_FLAG = "--verbose";
  private static final String TYPE_FLAG = "--type=some_type";
  private static final String METADATA1_FLAG = "--metadata=key=value";
  private static final String METADATA2_FLAG = "--metadata=key2=value2";

  private final CommandLine commandLine =
      configureCommandLine(new CommandLine(AppMetadataEncoderMain.class));

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private final AabMetadataDecoder aabMetadataDecoder = new AabMetadataDecoder();
  private TestLogHandler testLogHandler;
  private File encryptionKeyFile;

  @Before
  public void setUp() throws Exception {
    // Add log handler to assert the logged messages.
    testLogHandler = new TestLogHandler();
    Logger.getLogger("").addHandler(testLogHandler);

    // Reset log level between tests
    Logger.getLogger(checkNotNull(LoggingUtils.class.getPackage()).getName()).setLevel(Level.INFO);

    // Creating encryption key using tink.
    encryptionKeyFile = new File(tempFolder.getRoot(), "public_key.bin");
    HybridConfig.register();
    KeysetHandle.generateNew(KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_128_GCM"))
        .getPublicKeysetHandle()
        .writeNoSecret(
            BinaryKeysetWriter.withOutputStream(new FileOutputStream(encryptionKeyFile)));
  }

  @After
  public void clearTestLogHandler() {
    testLogHandler.clear();
    Logger.getLogger("").removeHandler(testLogHandler);
  }

  @Test
  public void encodeApkWithRequiredFlagsSet_logsNoErrors() throws Exception {
    String outputFilePath = getOutputFilePath("apk");

    int exitCode =
        commandLine.execute(
            ENCODE_COMMAND,
            getTestFile("v1-only-two-signers.apk", tempFolder).getAbsolutePath(),
            METADATA1_FLAG,
            METADATA2_FLAG,
            TYPE_FLAG,
            "--encryption=com.test=" + encryptionKeyFile.getPath(),
            "--encryption=com.test2=" + encryptionKeyFile.getPath(),
            "--output=" + outputFilePath);

    assertThat(getLogMessages())
        .containsExactly(
            "INFO: Validating request.",
            "INFO: Encoding metadata into APK.",
            String.format("INFO: Metadata successfully encoded at: %s.", outputFilePath));
    assertThat(exitCode).isEqualTo(0);
  }

  @Test
  public void encodeAabWithRequiredFlagsSet_logsNoErrors() throws Exception {
    String outputFilePath = getOutputFilePath("aab");

    int exitCode =
        commandLine.execute(
            ENCODE_COMMAND,
            getTestFile("app-with-test-metadata-type.aab", tempFolder).getAbsolutePath(),
            METADATA1_FLAG,
            METADATA2_FLAG,
            TYPE_FLAG,
            "--output=" + outputFilePath);

    assertThat(getLogMessages())
        .containsExactly(
            "INFO: Validating request.",
            "INFO: Encoding metadata into AAB.",
            String.format("INFO: Metadata successfully encoded at: %s.", outputFilePath));
    assertThat(exitCode).isEqualTo(0);
  }

  @Test
  public void encodeAabWithRequiredOptions_addsMetadataToAab() throws Exception {
    File inputFile = getTestFile("app-with-test-metadata-type.aab", tempFolder);

    commandLine.execute(
        ENCODE_COMMAND, inputFile.getAbsolutePath(), METADATA1_FLAG, METADATA2_FLAG, TYPE_FLAG);

    File outputFile = new File(inputFile.getParentFile(), "app-with-test-metadata-type-out.aab");
    assertThat(getLogMessages())
        .containsExactly(
            "INFO: Validating request.",
            "INFO: Encoding metadata into AAB.",
            String.format("INFO: Metadata successfully encoded at: %s.", outputFile.getPath()));
    Metadata outputMetadata = aabMetadataDecoder.decode(outputFile, "some_type").get();
    assertThat(outputMetadata)
        .isEqualTo(
            Metadata.newBuilder()
                .setAppMetadataEncoderVersion(AppMetadataEncoderConstants.CURRENT_VERSION)
                .addMetadataEntries(MetadataEntry.newBuilder().setKey("key2").setValue("value2"))
                .addMetadataEntries(MetadataEntry.newBuilder().setKey("key").setValue("value"))
                .build());
  }

  @Test
  public void encodeAabWithoutOutputFlag_logsNoErrors() throws Exception {
    File inputFile = getTestFile("app-with-test-metadata-type.aab", tempFolder);

    commandLine.execute(
        ENCODE_COMMAND, inputFile.getAbsolutePath(), METADATA1_FLAG, METADATA2_FLAG, TYPE_FLAG);

    assertThat(getLogMessages())
        .containsExactly(
            "INFO: Validating request.",
            "INFO: Encoding metadata into AAB.",
            String.format(
                "INFO: Metadata successfully encoded at: %s/app-with-test-metadata-type-out.aab.",
                checkNotNull(inputFile.getParentFile()).getPath()));
  }

  @Test
  public void encodeAabWithInvalidOutputType_logsError() throws Exception {
    File inputFile = getTestFile("app-with-test-metadata-type.aab", tempFolder);

    commandLine.execute(
        ENCODE_COMMAND,
        inputFile.getAbsolutePath(),
        METADATA1_FLAG,
        METADATA2_FLAG,
        TYPE_FLAG,
        "--output=" + getOutputFilePath("txt"));

    assertThat(getLogMessages())
        .containsExactly(
            "INFO: Validating request.",
            "SEVERE: Output file 'output.txt' must be either an APK or AAB.");
  }

  @Test
  public void encodeAabWithNonMatchingOutputType_logsError() throws Exception {
    File inputFile = getTestFile("app-with-test-metadata-type.aab", tempFolder);

    commandLine.execute(
        ENCODE_COMMAND,
        inputFile.getAbsolutePath(),
        METADATA1_FLAG,
        METADATA2_FLAG,
        TYPE_FLAG,
        "--output=" + getOutputFilePath("apk"));

    assertThat(getLogMessages())
        .containsExactly(
            "INFO: Validating request.",
            "SEVERE: Input and output files must have the same file extension (.apk or .aab).");
  }

  @Test
  public void encodeAabWithEqualInputAndOutputPath_logsError() throws Exception {
    File inputFile = getTestFile("app-with-test-metadata-type.aab", tempFolder);

    commandLine.execute(
        ENCODE_COMMAND,
        inputFile.getAbsolutePath(),
        METADATA1_FLAG,
        METADATA2_FLAG,
        TYPE_FLAG,
        "--output=" + inputFile.getAbsolutePath());

    assertThat(getLogMessages())
        .containsExactly(
            "INFO: Validating request.", "SEVERE: Input path cannot be the same as output path.");
  }

  @Test
  public void encodeAabWithExistingOutputFile_logsError() throws Exception {
    File inputFile = getTestFile("app-with-test-metadata-type.aab", tempFolder);
    String outputFilePath = getOutputFilePath("aab");
    Files.copy(inputFile, new File(outputFilePath));

    commandLine.execute(
        ENCODE_COMMAND,
        inputFile.getAbsolutePath(),
        METADATA1_FLAG,
        METADATA2_FLAG,
        TYPE_FLAG,
        "--output=" + outputFilePath);

    assertThat(getLogMessages())
        .containsExactly(
            "INFO: Validating request.",
            String.format("SEVERE: Output file at '%s' already exists.", outputFilePath));
  }

  @Test
  public void encodeAabWithExistingOutputFileInVerboseMode_logsError() throws Exception {
    File inputFile = getTestFile("app-with-test-metadata-type.aab", tempFolder);
    String outputFilePath = getOutputFilePath("aab");
    Files.copy(inputFile, new File(outputFilePath));

    commandLine.execute(
        ENCODE_COMMAND,
        VERBOSE_FLAG,
        inputFile.getAbsolutePath(),
        METADATA1_FLAG,
        METADATA2_FLAG,
        TYPE_FLAG,
        "--output=" + outputFilePath);

    assertThat(getLogMessages())
        .containsExactly(
            "INFO: Validating request.",
            String.format("SEVERE: Output file at '%s' already exists.", outputFilePath),
            "FINE: ");
    assertThat(getLastLogRecordException()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void encodeAabWithWithExistingMetadataType_doesNotProduceOutputFile() throws Exception {
    File inputFile = getTestFile("app-with-test-metadata-type.aab", tempFolder);
    String outputFilePath = getOutputFilePath("aab");

    commandLine.execute(
        ENCODE_COMMAND,
        inputFile.getAbsolutePath(),
        METADATA1_FLAG,
        METADATA2_FLAG,
        "--type=test",
        "--output=" + outputFilePath);

    assertThat(getLogMessages())
        .containsExactly(
            "INFO: Validating request.",
            "INFO: Encoding metadata into AAB.",
            "SEVERE: Metadata of type 'test' already exists.");
    assertThat(new File(outputFilePath).exists()).isFalse();
  }

  @Test
  public void encodeInvalidFileType_logsError() throws Exception {
    commandLine.execute(
        ENCODE_COMMAND,
        getTestFile("blank.txt", tempFolder).getAbsolutePath(),
        METADATA1_FLAG,
        METADATA2_FLAG,
        TYPE_FLAG);

    assertThat(getLogMessages())
        .containsExactly("SEVERE: Input file must be either an APK or AAB.");
  }

  @Test
  public void encodeApkWithDuplicateMetadataKey_logsError() throws Exception {
    commandLine.execute(
        ENCODE_COMMAND,
        getTestFile("v1-only-two-signers.apk", tempFolder).getAbsolutePath(),
        "--metadata=key=value",
        "--metadata=key=value2",
        TYPE_FLAG,
        "--output=" + getOutputFilePath("apk"));

    assertThat(getLogMessages())
        .containsExactly("SEVERE: Duplicate key 'key' in metadata is found.");
  }

  @Test
  public void encodeApkWithoutMetadataFlag_logsError() throws Exception {
    commandLine.execute(
        ENCODE_COMMAND,
        getTestFile("v1-only-two-signers.apk", tempFolder).getAbsolutePath(),
        TYPE_FLAG,
        "--output=" + getOutputFilePath("apk"));

    assertThat(getLogMessages())
        .containsExactly("SEVERE: Missing required option '--metadata=<String=String>'");
  }

  @Test
  public void encodeApkWithoutTypeFlag_logsError() throws Exception {
    commandLine.execute(
        ENCODE_COMMAND,
        getTestFile("v1-only-two-signers.apk", tempFolder).getAbsolutePath(),
        METADATA1_FLAG,
        "--output=" + getOutputFilePath("apk"));

    assertThat(getLogMessages()).containsExactly("SEVERE: Missing required option '--type=<type>'");
  }

  @Test
  public void encodeApkWithDuplicateEncryptionKeyOwner_logsError() throws Exception {
    commandLine.execute(
        ENCODE_COMMAND,
        getTestFile("v1-only-two-signers.apk", tempFolder).getAbsolutePath(),
        METADATA1_FLAG,
        TYPE_FLAG,
        "--encryption=com.test=" + encryptionKeyFile.getAbsolutePath(),
        "--encryption=com.test=" + getTestFile("blank.txt", tempFolder).getAbsolutePath());

    assertThat(getLogMessages())
        .containsExactly(
            "SEVERE: Duplicate encryption key owner 'com.test' in encryptions is found.");
  }

  @Test
  public void encodeApkWithNonExistingEncryptionKey_logsError() throws Exception {
    String keyPath = tempFolder.newFolder().getAbsolutePath() + "/non-existing-key.bin";
    commandLine.execute(
        ENCODE_COMMAND,
        getTestFile("v1-only-two-signers.apk", tempFolder).getAbsolutePath(),
        METADATA1_FLAG,
        TYPE_FLAG,
        "--encryption=com.test=" + keyPath);

    assertThat(getLogMessages())
        .containsExactly(
            String.format("SEVERE: Failed to read the encryption key from file: %s.", keyPath));
  }

  @Test
  public void encodeApkWithExistingOutputFileInVerboseMode_logsException() throws Exception {
    File inputFile = getTestFile("v1-only-two-signers.apk", tempFolder);
    String outputFilePath = getOutputFilePath("apk");
    Files.copy(inputFile, new File(outputFilePath));

    commandLine.execute(
        ENCODE_COMMAND,
        VERBOSE_FLAG,
        inputFile.getAbsolutePath(),
        METADATA1_FLAG,
        TYPE_FLAG,
        "--encryption=com.test=" + encryptionKeyFile.getPath(),
        "--output=" + outputFilePath);

    assertThat(getLogMessages())
        .containsExactly(
            "INFO: Validating request.",
            String.format("SEVERE: Output file at '%s' already exists.", outputFilePath),
            "FINE: ");
    assertThat(getLastLogRecordException()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void encodeApkWithNonExistingEncryptionKeyWithMultipleSeparators_logsError()
      throws Exception {
    String keyPath = "/path/to/e=mc2/key.bin";
    commandLine.execute(
        ENCODE_COMMAND,
        getTestFile("v1-only-two-signers.apk", tempFolder).getAbsolutePath(),
        METADATA1_FLAG,
        TYPE_FLAG,
        "--encryption=com.test=" + keyPath);

    assertThat(getLogMessages())
        .containsExactly(
            String.format("SEVERE: Failed to read the encryption key from file: %s.", keyPath));
  }

  private String getOutputFilePath(String fileExtension) throws Exception {
    return tempFolder.getRoot().getPath() + "/output." + fileExtension;
  }

  private ImmutableList<String> getLogMessages() {
    return testLogHandler.getStoredLogRecords().stream()
        .map(logRecord -> logRecord.getLevel().getName() + ": " + logRecord.getMessage())
        .collect(toImmutableList());
  }

  private Throwable getLastLogRecordException() {
    return checkNotNull(Iterables.getLast(testLogHandler.getStoredLogRecords()).getThrown());
  }
}
