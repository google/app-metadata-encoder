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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.flogger.GoogleLogger;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

/** Command to display third-party notices. */
@Command(
    name = "notices",
    description = "Displays third-party notices.",
    mixinStandardHelpOptions = true)
public final class AppMetadataNoticesCommand implements Callable<Integer> {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();
  private static final String NOTICES_RESOURCE_NAME = "THIRD_PARTY_NOTICES";

  @Override
  public Integer call() {
    URL noticesURL = Resources.getResource(AppMetadataNoticesCommand.class, NOTICES_RESOURCE_NAME);

    try {
      String notices = Resources.toString(noticesURL, UTF_8);
      logger.atInfo().log("%s", notices);
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Failed to retrieve third party notices.");
    }

    return 0;
  }
}
