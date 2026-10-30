class Solution {
  public boolean balanced(String text) {
    java.util.ArrayDeque<Character> stack = new java.util.ArrayDeque<>();
    for (int i = 0; i < text.length(); i++) {
      char current = text.charAt(i);
      if (current == '(' || current == '[' || current == '{') stack.push(current);
      else if (current == ')' || current == ']' || current == '}') {
        if (stack.isEmpty()) return false;
        char open = stack.pop();
        if ((current == ')' && open != '(') || (current == ']' && open != '[') || (current == '}' && open != '{')) return false;
      }
    }
    return stack.isEmpty();
  }
}
