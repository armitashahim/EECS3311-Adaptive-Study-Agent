# Adaptive AI Study & Learning Agent

EECS 3311 course project (Stage 2). A desktop application that helps a student plan study time and
practise for courses. An AI model (DeepSeek, `deepseek-flash`) reads syllabi, orders tasks, writes quizzes and
proposes review tasks, while ordinary code enforces every rule (deadlines, availability, scoring, validation).
Anything the AI produces is only a proposal: it is saved when the student accepts it, and an AI failure never
changes stored data.

The application has **two interfaces over the same code**: a Swing GUI and a command-line interface (CLI).

## Features

| ID  | Feature | AI? | GUI tab | CLI commands |
|-----|---------|-----|---------|--------------|
| F01 | Courses and tasks | no | Tasks | `course-add`, `task-add`, `task-edit`, `task-remove`, `tasks` |
| F02 | Weekly availability and commitments | no | Availability | `period-add`, `period-remove`, `periods` |
| F03 | Syllabus import (AI proposes tasks) | yes | Syllabus import | `syllabus-import`, `drafts`, `draft-set`, `draft-remove`, `syllabus-confirm`, `syllabus-cancel` |
| F04 | AI-assisted weekly plan | hybrid | Weekly plan | `plan-generate`, `plan-accept`, `plan-reject`, `plan` |
| F05 | Plan revision after changes | hybrid | Weekly plan | `plan-revise` |
| F06 | Progress recording | no | Weekly plan | `record` |
| F07 | Duration suggestions from history | no | Tasks | `duration`, `duration-accept` |
| F08 | Course material import (PDF) | no | Materials | `material-import`, `materials`, `material-show`, `material-remove` |
| F09 | Practice quiz generation and scoring | hybrid | Practice quiz | `quiz-generate`, `quiz-answer`, `quiz-submit`, `quiz-history` |
| F10 | Weak-topic review recommendations | hybrid | Review | `topics`, `recommend`, `recommend-accept` |

Type `help` in the CLI for every command and its arguments.

## Prerequisites

- **JDK 17 or newer** (`java -version`)
- **Apache Maven 3.9+** (`mvn -version`; on Windows the command may be `mvn.cmd`)
- A **DeepSeek API key** for the AI features (F03, F04, F05, F09, F10). Without a key the application still starts
  and F01, F02, F06, F07 and F08 work; AI features report that the assistant is not configured.

Dependencies (JUnit 5, Gson, Apache PDFBox) are downloaded automatically by Maven.

## Setup

1. Clone the repository and open a terminal in its folder.
2. Copy the example configuration and enter your key locally:

   ```bash
   cp .env.example .env          # Windows PowerShell: Copy-Item .env.example .env
   ```

   Edit `.env` and replace `YOUR_LOCAL_API_KEY_HERE` with your key:

   ```
   LLM_API_KEY=your-key-here
   LLM_BASE_URL=https://api.deepseek.com
   LLM_MODEL=deepseek-flash
   ```

   `.env` is listed in `.gitignore` and must never be committed. `.env.example` contains placeholders only.
3. (Optional) Check that the key and model work:

   ```bash
   mvn compile exec:java -Dexec.mainClass=ca.yorku.eecs3311.ai.LLMCheck
   ```

## Run

Start the **GUI**:

```bash
mvn compile exec:java -Dexec.mainClass=ca.yorku.eecs3311.gui.StudyGUI
```

Start the **CLI**:

```bash
mvn compile exec:java -Dexec.mainClass=ca.yorku.eecs3311.cli.StudyCLI
```

In Windows PowerShell, put the `-D` option in quotes, for example
`mvn.cmd compile exec:java "-Dexec.mainClass=ca.yorku.eecs3311.gui.StudyGUI"`.

Both programs keep their data in `data/repository.json` (created on first use, git-ignored). Pass another file
as the first argument to use separate data, for example `-Dexec.args="data/demo.json"`. The GUI and CLI read the
same file, so use one at a time.

## Standalone CLI (`Agent.jar`)

The CLI can also be built as a single runnable jar that includes all its dependencies, so it runs directly
from a terminal with `java -jar`, without Maven.

### Build

```bash
mvn clean package
```

This runs the tests and creates **`target/Agent.jar`**. To build faster without the tests, add `-DskipTests`.

### Run a task file

A task file is a plain-text list of CLI commands, one per line. Blank lines and lines starting with `#` are
ignored. The agent runs every command in order, prints each one with its result, and exits.

```bash
java -jar target/Agent.jar --input samples/task.txt --data data/task-demo.json
```

[`samples/task.txt`](samples/task.txt) creates a course with two deadlines, adds weekly availability and a
commitment, then asks the **AI planning agent** to build next week's study plan (`plan-generate next`) and
saves it. It needs an API key, which is read from `.env` or passed with `--api-key`.

To try the CLI **without an API key**, run the deterministic example:

```bash
java -jar target/Agent.jar --no-llm --input samples/task-offline.txt --data data/offline-demo.json
```

To see **most features in one run** (F01–F05 and F07–F10), use the full demo. It imports
`samples/demo-syllabus.pdf` and `samples/sample-notes-sorting.pdf`, builds and revises a plan, takes a practice
quiz, and accepts a review recommendation. It needs an API key.

```bash
java -jar target/Agent.jar --input samples/demo-all.txt --data data/demo-all.json
```

The script answers the quiz before it can see the questions. If 3 or more of those answers happen to be correct,
Sorting is not a weak topic: `recommend` then reports that there are no weak topics, and `recommend-accept` fails.
F06 is not in the script because progress can only be recorded once a session has started.

| Sample file | Features | Needs API key |
|---|---|---|
| `samples/task.txt` | F01, F02, F04 | yes |
| `samples/task-offline.txt` | F01, F02, F07 | no |
| `samples/demo-all.txt` | F01–F05, F07–F10 | yes |

Each run adds its course and periods to the data file. To run a task file again from a clean state, delete
its data file first (for example `data/task-demo.json`) or pass a new `--data` file.

### Options

| Option | Meaning |
|---|---|
| `-i`, `--input FILE` | Run the commands in `FILE`, then exit. Use `-` to read commands from standard input. Without this option, the CLI starts an interactive session. |
| `-d`, `--data FILE` | Data file to use (default `data/repository.json`). |
| `--env FILE` | Configuration file with the LLM settings (default `.env`). |
| `--api-key KEY` | LLM API key. Overrides `LLM_API_KEY` from the `.env` file. |
| `--llm-api URL` | LLM API base URL (also accepted as `--base-url`). Overrides `LLM_BASE_URL`; default `https://api.deepseek.com`. |
| `--model NAME` | LLM model name. Overrides `LLM_MODEL`; default `deepseek-flash`. |
| `--no-llm` | Run without the AI assistant. AI commands (F03, F04, F05, F09, F10) report that it is off. |
| `--echo` | Print each command before its output. This is on by default with `--input`. |
| `-h`, `--help` | Show the options and exit. |

LLM settings are applied in this order, with later ones taking priority: the `.env` file, then environment
variables (`LLM_API_KEY`, `LLM_BASE_URL`, `LLM_MODEL`), then command-line options. Passing the key with
`--api-key` can leave it in your shell history, so the `.env` file is the safer choice.

**Exit status:** `0` if every command succeeded, `1` if any command failed (for example, invalid input or an AI
error), and `2` if the options were invalid or the input file was not found.

### More examples

```bash
# Interactive session with a separate data file
java -jar target/Agent.jar --data data/demo.json

# Commands piped through standard input
echo "courses" | java -jar target/Agent.jar --input - --no-llm

# Show all options
java -jar target/Agent.jar --help
```

In the CLI, `plan`, `plan-generate` and `plan-revise` accept a week as `YYYY-MM-DD`, `this` or `next`. The
keyword `next` keeps task files valid from week to week.

## Run the tests

```bash
mvn clean test
```

The tests use a scripted stand-in for the AI, so they need no key or network.

## Example usage (CLI)

The folder `samples/` has made-up PDFs: `demo-syllabus.pdf` (Winter 2027 dates), `sample-syllabus.pdf` (Fall 2026
dates, including one task with no date) and `sample-notes-sorting.pdf`.

```
> course-add "Data Structures"
> period-add availability monday 09:00 17:00
> syllabus-import 1 samples/demo-syllabus.pdf
> draft-set all priority 2
> draft-set all minutes 90
> drafts
> syllabus-confirm
> plan-generate next
> plan-accept
> material-import 1 "Sorting notes" samples/sample-notes-sorting.pdf
> quiz-generate 1 1 Sorting
> quiz-answer 1 A
> quiz-submit confirm
> topics 1
> recommend 1
```

In the GUI the same steps are done with the tabs: add a course, add availability, import the syllabus on the
**Syllabus import** tab, generate and accept a plan on **Weekly plan**, import the notes on **Materials**, take a
quiz on **Practice quiz**, then open **Review**.

Notes: priorities run 1 to 3 and **3 is the highest**. Dates are `yyyy-MM-dd HH:mm`. A date without a time means
23:59 that day. A weekly plan only uses availability that is still in the future.

## Design

- **Facade + MVC.** `StudyApplication` is the single entry point (model: `model/`, `repository/`; views:
  `cli/StudyCLI`, `gui/StudyGUI`). Both views only parse input and show results, so they always behave the same.
- **Adapter.** `ai/LLMClient` is the interface the rest of the program uses; `ai/DeepSeekClient` adapts the
  DeepSeek HTTP API to it. Tests substitute a scripted client.
- **Strategy.** `estimation/DurationEstimationStrategy` with `OriginalDurationStrategy` and
  `HistoricalDurationStrategy`, chosen by `DurationEstimator` (F07).
- **Command.** `agent/AgentTool` (`StudyDataTool`, `SchedulingTool`) are commands run by `ToolManager`
  for the planning agent (`StudyAgent`, F04/F05).
- **Hybrid AI.** The model only chooses task order, extracts data, writes quiz questions or proposes review
  tasks. `scheduling/Scheduler`, `quiz/QuizService` validation, `model/Quiz` scoring and
  `review/TopicStats` accuracy are deterministic.

Package layout (`src/main/java/ca/yorku/eecs3311/`): `agent`, `ai`, `application`, `cli`, `config`,
`estimation`, `gui`, `material`, `model`, `quiz`, `repository`, `review`, `scheduling`, `syllabus`.

## Known limitations

- Quiz questions are checked for structure (five questions, four choices, one correct answer, an explanation),
  not for factual accuracy.
- Only text-based PDFs are supported (scanned PDFs are rejected); text longer than 60,000 characters is rejected.
- Data is stored in one local JSON file; there are no user accounts.
- The AI assistant needs an internet connection and a valid API key.