# Dynamic programming

## Simple version

Some problems repeat the same smaller question. Dynamic programming saves that answer and reuses it, instead of solving the smaller question again.

## How to picture it

Climbing stairs, taking 1 or 2 steps. The ways to reach step n are the ways to reach n-1, plus the ways to reach n-2. Write those two numbers down. The next step is their sum. You never need the whole history, only the last two answers.

## Interview version

Name the smaller problem, the choice that gets you there, and the base case. Here the base cases are 0 steps and 1 step: each has one way. After that, each answer is the sum of the previous two. One loop does the work, and two variables hold the state, so extra space stays constant.

## Common traps

- Recomputing the same step inside a recursive call.
- Treating 0 steps as zero ways. Doing nothing is one way.
- Keeping an array of every step when only the last two values matter.

### Tiny glossary

- **Subproblem:** a smaller input of the same question.
- **Base case:** an input small enough to answer immediately.
- **Recurrence:** the rule that builds the next answer from earlier ones.
- **State:** the saved values the next step needs.

Read this only as much as you need, then try the exercise.
