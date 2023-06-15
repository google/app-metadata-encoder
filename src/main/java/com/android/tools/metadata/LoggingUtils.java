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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.flogger.GoogleLogger;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import picocli.CommandLine;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.IParameterExceptionHandler;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParseResult;

/** Utility functions for configuring logging. */
public final class LoggingUtils {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  /**
   * Configures the logging exception handlers of the command line object and the system's logging
   * format.
   *
   * @param cmd CommandLine object instantiated with the target class.
   */
  public static CommandLine configureCommandLine(CommandLine cmd) {
    Properties properties = System.getProperties();
    // Remove timestamp line from log.
    // Log in the format of "<log_level>: <log_message> <backtrace (Only in verbose mode)>".
    properties.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s %6$s%n");

    LoggingExceptionHandler loggingExceptionHandler = new LoggingExceptionHandler();
    return cmd.setParameterExceptionHandler(loggingExceptionHandler)
        .setExecutionExceptionHandler(loggingExceptionHandler);
  }

  /** Extends a Picocli command class with verbosity functionality. */
  public static final class LoggingVerbosityMixin {
    @Option(
        names = {"-v", "--verbose"},
        description =
            "Enable verbose output which includes execution steps, failure exception stack trace,"
                + " etc.")
    boolean isVerbose;

    /** Configures the level of logger and console handler depending on {@code isVerbose}. */
    public void configureLogger() {
      Logger root = Logger.getLogger(checkNotNull(LoggingUtils.class.getPackage()).getName());
      if (isVerbose) {
        root.setLevel(Level.FINE);
      } else {
        root.setLevel(Level.INFO);
      }
    }
  }

  private static class LoggingExceptionHandler
      implements IParameterExceptionHandler, IExecutionExceptionHandler {
    @Override
    public int handleParseException(ParameterException e, String[] args) {
      CommandLine cmd = e.getCommandLine();
      return logException(e, cmd);
    }

    @Override
    public int handleExecutionException(Exception e, CommandLine cmd, ParseResult parseResult) {
      return logException(e, cmd);
    }

    private static int logException(Exception e, CommandLine cmd) {
      logger.atSevere().log("%s", e.getMessage());
      logger.atFine().withCause(e).log();
      cmd.getOut()
          .println(
              "To learn more about each option of the command line tool, run the command with"
                  + " --help or -h.");

      return cmd.getExitCodeExceptionMapper() != null
          ? cmd.getExitCodeExceptionMapper().getExitCode(e)
          : cmd.getCommandSpec().exitCodeOnExecutionException();
    }
  }

  private LoggingUtils() {}
}
