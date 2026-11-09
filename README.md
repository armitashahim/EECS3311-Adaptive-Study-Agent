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

## Run the tests

```bash
mvn clean test
```

The tests use a scripted stand-in for the AI, so they need no key or network.

## Example usage (CLI)

The folder `samples/` has two made-up PDFs: `sample-syllabus.pdf` and `sample-notes-sorting.pdf`.

```
> course-add "Data Structures"
> period-add availability monday 09:00 17:00
> syllabus-import 1 samples/sample-syllabus.pdf
> draft-set all priority 2
> draft-set all minutes 90
> drafts
> draft-set 4 deadline 2026-12-01 17:00      (the task that had no date; check its number with 'drafts')
> syllabus-confirm
> plan-generate
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