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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.io.Files.getFileExtension;

import com.android.tools.metadata.LoggingUtils.LoggingVerbosityMixin;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** Command to encode metadata into an APK or AAB. */
@Command(
    name = "encode",
    description = "Encodes metadata into an APK or ABB.",
    mixinStandardHelpOptions = true)
public final class AppMetadataEncodeCommand implements Callable<Integer> {
  @Spec CommandSpec spec;

  @Mixin LoggingVerbosityMixin verbosityMixin;

  private final Map<String, String> metadataMap = new HashMap<>();

  private final Map<String, Path> encryptionMap = new HashMap<>();

  private File input;

  
  @Parameters(
      index = "0",
      arity = "1",
      description = "Path to the APK or AAB file to encode metadata into.")
  private void setInput(File input) {
    String fileName = input.getName();

    if (!fileName.endsWith(".apk") && !fileName.endsWith(".aab")) {
      throw new ParameterException(spec.commandLine(), "Input file must be either an APK or AAB.");
    }

    this.input = input;
  }

  
  @Option(
      names = "--metadata",
      required = true,
      description = {
        "Key-value pairs with metadata to encode.",
        "The repeated flag where each metadata entry should be provided in the format"
            + " `--metadata=<key>=<value>`.",
        "For example `--metadata=app.version=0.1.3 --metadata=app.subversion=42`"
      })
  private void setMetadata(Map<String, String> metadata) {
    for (String key : metadata.keySet()) {
      String newValue = metadata.get(key);

      // Check for duplicate keys.
      String existing = metadataMap.get(key);
      if (existing != null && !existing.equals(newValue)) {
        throw new ParameterException(
            spec.commandLine(), String.format("Duplicate key '%s' in metadata is found.", key));
      }

      metadataMap.put(key, newValue);
    }
  }

  
  @Option(
      names = "--encryption",
      description = {
        "[Only for APKs] Repeated flag specifying the encryption key owner and the path to the"
            + " encryption key (in binary format) to encrypt the metadata.",
        "The pairs of `encryption_key_owner` and `encryption_key_path` must be provided in the"
            + " format `--encryption=<encryption_key_owner>=<encryption_key_path>` where"
            + " `encryption_key_owner` is a string in the java package name format (it is expected"
            + " to be a reverse of a domain this party owns).",
        "For example `--encryption=com.sample.store=/path/to/key.bin`"
      })
  private void setEncryption(Map<String, Path> encryptions) {
    for (String keyOwner : encryptions.keySet()) {
      Path newKeyPath = encryptions.get(keyOwner);

      // Check for duplicate encryption_key_owner.
      Path existingKeyPath = encryptionMap.get(keyOwner);
      if (existingKeyPath != null && !existingKeyPath.equals(newKeyPath)) {
        throw new ParameterException(
            spec.commandLine(),
            String.format(
                "Duplicate encryption key owner '%s' in encryptions is found.", keyOwner));
      }

      encryptionMap.put(keyOwner, newKeyPath);
    }
  }

  @Option(
      names = "--type",
      required = true,
      description = {
        "The type of the metadata (e.g. drm).",
        "A string identifier for the type of metadata that is being encoded."
      })
  private String type;

  @Option(
      names = "--output",
      description = {
        "Path to the output file. (Optional)",
        "If not provided, then the input APK or AAB name will be augmented with `-out` before the"
            + " file extension. E.g. for `app.apk` the tool will generate `app-out.apk`."
      })
  private Path output;

  @Override
  public Integer call() throws Exception {
    verbosityMixin.configureLogger();

    Metadata metadata = buildMetadataProto();
    ImmutableList<EncodeAppMetadataRequest.Encryption> encryptions = buildEncryptions();
    EncodeAppMetadataRequest request =
        EncodeAppMetadataRequest.builder()
            .setInputFile(input)
            .setMetadata(metadata)
            .setType(type)
            .setEncryptions(encryptions)
            .build();

    AppMetadataEncoder appMetadataEncoder = new AppMetadataEncoder();
    File outputFile =
        Optional.ofNullable(output).orElse(defaultOutputPath(request.getInputFile())).toFile();
    appMetadataEncoder.validateRequest(
        request.getInputFile(), request.getType(), request.getMetadata(), request.getEncryptions());
    validateOutputFile(outputFile, request.getInputFile());

    boolean finishedSuccessfully = false;
    try {
      // Copy input file to output path.
      Files.copy(request.getInputFile(), outputFile);
      request = request.toBuilder().setInputFile(outputFile).build();
      appMetadataEncoder.encodeInPlaceWithoutValidations(request);

      finishedSuccessfully = true;
    } finally {
      // Deleting the file if the encoding failed.
      if (!finishedSuccessfully && outputFile.exists()) {
        outputFile.delete();
      }
    }

    return 0;
  }

  // Augment input file path with '-out' before file extension.
  private Path defaultOutputPath(File inputFile) {
    String inputFileName = inputFile.getName();
    String outputFileName =
        String.format(
            "%s-out.%s",
            Files.getNameWithoutExtension(inputFileName), Files.getFileExtension(inputFileName));

    return Paths.get(checkNotNull(inputFile.getParent()), outputFileName);
  }

  private Metadata buildMetadataProto() {
    return Metadata.newBuilder()
        .setAppMetadataEncoderVersion(AppMetadataEncoderConstants.CURRENT_VERSION)
        .addAllMetadataEntries(
            metadataMap.entrySet().stream()
                .map(
                    kv ->
                        MetadataEntry.newBuilder()
                            .setKey(kv.getKey())
                            .setValue(kv.getValue())
                            .build())
                .collect(toImmutableList()))
        .build();
  }

  private ImmutableList<EncodeAppMetadataRequest.Encryption> buildEncryptions() {
    return encryptionMap.entrySet().stream()
        .map(
            kv -> {
              try {
                return EncodeAppMetadataRequest.Encryption.builder()
                    .setEncryptionKeyOwner(kv.getKey())
                    .setEncryptionKey(ByteBuffer.wrap(Files.toByteArray(kv.getValue().toFile())))
                    .build();
              } catch (IOException e) {
                throw new ParameterException(
                    spec.commandLine(),
                    String.format(
                        "Failed to read the encryption key from file: %s.", kv.getValue()),
                    e);
              }
            })
        .collect(toImmutableList());
  }

  private void validateOutputFile(File outputFile, File inputFile) {
    String outputFileName = outputFile.getName();

    checkArgument(
        !inputFile.getAbsolutePath().equals(outputFile.getAbsolutePath()),
        "Input path cannot be the same as output path.");
    checkArgument(
        outputFileName.endsWith(".apk") || outputFileName.endsWith(".aab"),
        "Output file '%s' must be either an APK or AAB.",
        outputFileName);
    checkArgument(
        getFileExtension(inputFile.getName()).equals(getFileExtension(outputFileName)),
        "Input and output files must have the same file extension (.apk or .aab).");
    checkArgument(
        !outputFile.exists(), "Output file at '%s' already exists.", outputFile.getAbsolutePath());
  }
}
