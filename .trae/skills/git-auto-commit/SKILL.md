---
name: "git-auto-commit"
description: "Automatically commits code changes to Git after task completion. Invoke when user finishes a task or says 'commit', 'done', 'finish', or when checking for uncommitted changes."
---

# Git Auto Commit Skill

## Purpose

This skill automatically commits code changes to Git repository after completing a task, ensuring your work is saved and tracked properly.

## When to Invoke

Invoke this skill when:
- User completes a task and mentions "commit", "done", "finish", or similar
- User explicitly asks to commit changes
- Checking if there are uncommitted changes in the workspace
- Session ends with code modifications

## How It Works

1. **Check Status**: First checks if the workspace is a Git repository and if there are any uncommitted changes
2. **Generate Message**: Creates a meaningful commit message based on task context
3. **Commit Changes**: Executes `git add -A && git commit -m "<message>"`
4. **Push to Remote**: Pushes changes to the remote repository

## Usage Examples

### Example 1: Basic Commit
```
User: "I'm done with the feature, commit it"
Skill: Checks for changes → Creates commit → Pushes to remote
```

### Example 2: With Task Summary
```
User: "Finish the login page fix and commit"
Skill: Generates message "fix: Complete login page fix" → Commits → Pushes
```

### Example 3: Check and Commit
```
User: "Check if there are changes and commit"
Skill: Checks status → If changes exist, commits with auto-generated message
```

## Commit Message Format

The skill generates commit messages following Conventional Commits:

```
<type>: <task summary>

## Task Summary
<detailed task description>

## Changed Files
- file1.py
- file2.js
```

### Commit Types
- `feat`: New features
- `fix`: Bug fixes  
- `refactor`: Code refactoring
- `docs`: Documentation updates
- `chore`: Maintenance tasks

## Safety Features

- Always checks for Git repository before attempting commit
- Verifies changes exist before committing
- Provides clear feedback about success or failure
- Does not auto-push if commit fails

## Output Format

```
✅ Commit successful!
Hash: abc1234
Branch: main
Message: feat: Implement user authentication

Changed files:
- src/auth/login.py
- src/auth/utils.py
```
