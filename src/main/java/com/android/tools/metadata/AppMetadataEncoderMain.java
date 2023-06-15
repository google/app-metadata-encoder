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

import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Main entry for App Metadata Encoder. */
@Command(
    mixinStandardHelpOptions = true,
    version = AppMetadataEncoderConstants.CURRENT_VERSION,
    subcommands = {AppMetadataEncodeCommand.class, AppMetadataNoticesCommand.class})
public final class AppMetadataEncoderMain implements Callable<Integer> {
  @Override
  public Integer call() throws Exception {
    return 0;
  }

  /** Parses the flags and routes to the appropriate command handler. */
  public static void main(String... args) {
    int exitCode =
        configureCommandLine(new CommandLine(AppMetadataEncoderMain.class)).execute(args);
    System.exit(exitCode);
  }
}
