package ca.yorku.eecs3311.cli;

import java.nio.file.Path;
import java.util.List;

/**
 * Command-line options for the standalone CLI ({@code java -jar Agent.jar ...}).
 * Parsing is kept separate from {@link StudyCLI} so the rules can be tested on their own.
 */
public final class CliOptions {
    public static final String DEFAULT_DATA_FILE = "data/repository.json";
    public static final String DEFAULT_ENV_FILE = ".env";

    public static final String USAGE = """
            Usage: java -jar Agent.jar [options]

            Runs the Adaptive Study Agent from the terminal. Without --input it starts an
            interactive session; with --input it runs the commands in the file and exits.

            Options:
              -i, --input FILE     run the commands in FILE (one command per line; lines starting
                                   with # are comments). Use "-" to read commands from standard input.
              -d, --data FILE      data file to use (default: data/repository.json)
                  --env FILE       configuration file with the LLM settings (default: .env)
                  --api-key KEY    LLM API key (overrides LLM_API_KEY from the .env file)
                  --llm-api URL    LLM API base URL (overrides LLM_BASE_URL; default https://api.deepseek.com)
                  --model NAME     LLM model name (overrides LLM_MODEL; default deepseek-flash)
                  --no-llm         run without the AI assistant (AI commands report that it is off)
                  --echo           print each command before its output (on by default with --input)
              -h, --help           show this help and exit

            Exit status: 0 if every command succeeded, 1 if any command failed,
            2 if the options were invalid.

            Example:
              java -jar Agent.jar --input samples/task.txt --data data/demo.json
            """;

    private final Path inputFile;
    private final boolean readStandardInput;
    private final Path dataFile;
    private final Path envFile;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final boolean noLlm;
    private final boolean echo;
    private final boolean help;

    private CliOptions(Path inputFile, boolean readStandardInput, Path dataFile, Path envFile,
                       String apiKey, String baseUrl, String model, boolean noLlm,
                       boolean echo, boolean help) {
        this.inputFile = inputFile;
        this.readStandardInput = readStandardInput;
        this.dataFile = dataFile;
        this.envFile = envFile;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.noLlm = noLlm;
        this.echo = echo;
        this.help = help;
    }

    /**
     * Parses the program arguments. A single argument that is not an option is treated as
     * the data file, so the older form {@code StudyCLI data/demo.json} keeps working.
     *
     * @throws IllegalArgumentException if an option is unknown, repeated or missing its value
     */
    public static CliOptions parse(String[] args) {
        Path inputFile = null;
        boolean readStandardInput = false;
        Path dataFile = null;
        Path envFile = null;
        String apiKey = null;
        String baseUrl = null;
        String model = null;
        boolean noLlm = false;
        boolean echo = false;
        boolean help = false;

        List<String> list = List.of(args == null ? new String[0] : args);
        for (int i = 0; i < list.size(); i++) {
            String arg = list.get(i);
            switch (arg) {
                case "-i", "--input" -> {
                    String value = value(list, i++, arg);
                    if (inputFile != null || readStandardInput) {
                        throw new IllegalArgumentException("--input was given more than once.");
                    }
                    if (value.equals("-")) {
                        readStandardInput = true;
                    } else {
                        inputFile = Path.of(value);
                    }
                }
                case "-d", "--data" -> {
                    if (dataFile != null) {
                        throw new IllegalArgumentException("--data was given more than once.");
                    }
                    dataFile = Path.of(value(list, i++, arg));
                }
                case "--env" -> envFile = Path.of(value(list, i++, arg));
                case "--api-key" -> apiKey = value(list, i++, arg);
                case "--llm-api", "--base-url" -> baseUrl = value(list, i++, arg);
                case "--model" -> model = value(list, i++, arg);
                case "--no-llm" -> noLlm = true;
                case "--echo" -> echo = true;
                case "-h", "--help" -> help = true;
                default -> {
                    if (arg.startsWith("-")) {
                        throw new IllegalArgumentException("Unknown option '" + arg + "'.");
                    }
                    if (dataFile != null) {
                        throw new IllegalArgumentException("Unexpected argument '" + arg + "'.");
                    }
                    dataFile = Path.of(arg);
                }
            }
        }
        if (noLlm && (apiKey != null || baseUrl != null || model != null)) {
            throw new IllegalArgumentException(
                    "--no-llm cannot be combined with --api-key, --llm-api or --model.");
        }
        return new CliOptions(inputFile, readStandardInput,
                dataFile == null ? Path.of(DEFAULT_DATA_FILE) : dataFile,
                envFile == null ? Path.of(DEFAULT_ENV_FILE) : envFile,
                apiKey, baseUrl, model, noLlm,
                echo || inputFile != null || readStandardInput, help);
    }

    private static String value(List<String> args, int index, String option) {
        if (index + 1 >= args.size() || args.get(index + 1).isBlank()) {
            throw new IllegalArgumentException("Option " + option + " needs a value.");
        }
        return args.get(index + 1);
    }

    /** The command file, or null when commands come from the keyboard or standard input. */
    public Path inputFile() {
        return inputFile;
    }

    /** True when the commands are a script (a file or "-"), not an interactive session. */
    public boolean isScripted() {
        return inputFile != null || readStandardInput;
    }

    public Path dataFile() {
        return dataFile;
    }

    public Path envFile() {
        return envFile;
    }

    public String apiKey() {
        return apiKey;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String model() {
        return model;
    }

    public boolean noLlm() {
        return noLlm;
    }

    public boolean echo() {
        return echo;
    }

    public boolean help() {
        return help;
    }
}
