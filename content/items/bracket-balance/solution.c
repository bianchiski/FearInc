/* Return 1 when brackets are balanced and 0 when they are not. */
int brackets_balanced(const char *text) {
  char stack[256]; /* stack stores unmatched opening brackets. */
  int top = 0; /* top is both the count and the next free index. */
  for (const char *p = text; *p != '\0'; p++) { /* Walk every character until the terminator. */
    if (*p == '(' || *p == '[' || *p == '{') { /* This character opens a bracket group. */
      stack[top] = *p; /* Push the opening bracket. */
      top++; /* Reserve the next stack slot. */
    } else if (*p == ')' || *p == ']' || *p == '}') { /* This character closes a group. */
      if (top == 0) { /* A closing bracket has no matching opener. */
        return 0; /* The string is unbalanced. */
      }
      top--; /* Pop the most recent unmatched opening bracket. */
      char open = stack[top]; /* open is the bracket that must match this closer. */
      if ((*p == ')' && open != '(') || /* Parentheses must match each other. */
          (*p == ']' && open != '[') || /* Square brackets must match each other. */
          (*p == '}' && open != '{')) { /* Curly braces must match each other. */
        return 0; /* The closer does not match the latest opener. */
      }
    }
  }
  return top == 0; /* Every pushed opener must have been popped. */
}
