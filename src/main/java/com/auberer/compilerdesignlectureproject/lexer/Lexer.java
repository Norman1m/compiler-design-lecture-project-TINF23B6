package com.auberer.compilerdesignlectureproject.lexer;

import com.auberer.compilerdesignlectureproject.lexer.statemachine.StateMachine;
import com.auberer.compilerdesignlectureproject.reader.CodeLoc;
import com.auberer.compilerdesignlectureproject.reader.Reader;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.misc.Pair;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lexer class for tokenizing the input stream.
 * Input: Character stream
 * Output: Token stream
 */
@Slf4j
public class Lexer implements ILexer {
  private final Reader reader;
  private final List<StateMachine> stateMachines = new ArrayList<>();
  private final Queue<Pair<Character, CodeLoc>> inputBuffer = new LinkedList<>();
  private Token currentToken;
  private final boolean dumpTokens;

  public Lexer(Reader reader, boolean dumpTokens) {
    this.reader = reader;
    this.dumpTokens = dumpTokens;

    stateMachines.add(new KeywordStateMachine("int", TokenType.TOK_TYPE_INT));
    stateMachines.add(new KeywordStateMachine("double", TokenType.TOK_TYPE_DOUBLE));
    stateMachines.add(new KeywordStateMachine("string", TokenType.TOK_TYPE_STRING));
    stateMachines.add(new KeywordStateMachine("bool", TokenType.TOK_TYPE_BOOL));
    stateMachines.add(new KeywordStateMachine("if", TokenType.TOK_IF));
    stateMachines.add(new KeywordStateMachine("else", TokenType.TOK_ELSE));
    stateMachines.add(new KeywordStateMachine("switch", TokenType.TOK_SWITCH));
    stateMachines.add(new KeywordStateMachine("case", TokenType.TOK_CASE));
    stateMachines.add(new KeywordStateMachine("default", TokenType.TOK_DEFAULT));
    stateMachines.add(new KeywordStateMachine("while", TokenType.TOK_WHILE));
    stateMachines.add(new KeywordStateMachine("do", TokenType.TOK_DO));
    stateMachines.add(new KeywordStateMachine("for", TokenType.TOK_FOR));
    stateMachines.add(new KeywordStateMachine("ret", TokenType.TOK_RET));
    stateMachines.add(new KeywordStateMachine("call", TokenType.TOK_CALL));
    stateMachines.add(new KeywordStateMachine("print", TokenType.TOK_PRINT));
    stateMachines.add(new KeywordStateMachine("true", TokenType.TOK_TRUE));
    stateMachines.add(new KeywordStateMachine("false", TokenType.TOK_FALSE));

    stateMachines.add(new PunctuationStateMachine("{", TokenType.TOK_LBRACE));
    stateMachines.add(new PunctuationStateMachine("}", TokenType.TOK_RBRACE));
    stateMachines.add(new PunctuationStateMachine("(", TokenType.TOK_LPAREN));
    stateMachines.add(new PunctuationStateMachine(")", TokenType.TOK_RPAREN));
    stateMachines.add(new PunctuationStateMachine("[", TokenType.TOK_LBRACKET));
    stateMachines.add(new PunctuationStateMachine("]", TokenType.TOK_RBRACKET));

    stateMachines.add(new PunctuationStateMachine("=", TokenType.TOK_ASSIGN));
    stateMachines.add(new PunctuationStateMachine("==", TokenType.TOK_EQUAL));
    stateMachines.add(new PunctuationStateMachine("!", TokenType.TOK_NOT));
    stateMachines.add(new PunctuationStateMachine("!=", TokenType.TOK_NOT_EQUAL));
    stateMachines.add(new PunctuationStateMachine("+", TokenType.TOK_PLUS));
    stateMachines.add(new PunctuationStateMachine("-", TokenType.TOK_MINUS));
    stateMachines.add(new PunctuationStateMachine("*", TokenType.TOK_MUL));
    stateMachines.add(new PunctuationStateMachine("/", TokenType.TOK_DIV));
    stateMachines.add(new PunctuationStateMachine(":", TokenType.TOK_COLON));
    stateMachines.add(new PunctuationStateMachine("?", TokenType.TOK_QUESTION_MARK));
    stateMachines.add(new PunctuationStateMachine(";", TokenType.TOK_SEMICOLON));

    stateMachines.add(new DoubleLiteralStateMachine());
    stateMachines.add(new IntegerLiteralStateMachine());
    stateMachines.add(new StringLiteralStateMachine());
    stateMachines.add(new IdentifierStateMachine());

    // Initialize all state machines
    for (StateMachine stateMachine : stateMachines)
      stateMachine.init();

    // Read the first token
    advance();
  }

  @Override
  public Token getToken() {
    return currentToken;
  }

  private char peekChar() {
    if (!inputBuffer.isEmpty())
      return inputBuffer.peek().a;
    return reader.getChar();
  }

  private Pair<Character, CodeLoc> getCurrentCharAndCodeLoc() {
    // If there are characters in the input buffer, return the next one
    // This is required to backtrack to the position, where a previously matching state machine accepted
    // e.g. in case of the keyword machines "for" and "foreach", with the input "forea", the "for" machine
    // would accept first, but the "foreach" machine would continue matching. Later, the "foreach" machine
    // would fail. Then we want to produce the token "for" and continue with the "ea" part of the input.
    if (!inputBuffer.isEmpty())
      return inputBuffer.poll();
    char currentChar = reader.getChar();
    CodeLoc currentCodeLoc = reader.getCodeLoc().clone();
    reader.advance();
    return new Pair<>(currentChar, currentCodeLoc);
  }

  @Override
  public void advance() {
    // Reset all state machines to start from the respective initial state
    for (StateMachine stateMachine : stateMachines)
      stateMachine.reset();

    // Skip whitespaces and comments
    skipWhitespaces();
    skipComments();
    skipWhitespaces(); // This is required to eliminate whitespaces after comments

    CodeLoc tokenCodeLoc = null;

    // Run all state machines in parallel on the given char input stream
    List<StateMachine> runningMachines = new ArrayList<>(stateMachines);
    Map<StateMachine, Integer> acceptingMachines = new LinkedHashMap<>();
    Queue<Pair<Character, CodeLoc>> newInputBuffer = new LinkedList<>();
    while (!(reader.isEOF() && inputBuffer.isEmpty()) && !runningMachines.isEmpty()) {
      Pair<Character, CodeLoc> curCharAndCodeLoc = getCurrentCharAndCodeLoc();
      newInputBuffer.add(curCharAndCodeLoc);
      if (tokenCodeLoc == null)
        tokenCodeLoc = curCharAndCodeLoc.b;

      for (StateMachine stateMachine : new CopyOnWriteArrayList<>(runningMachines)) {
        // Try to process the input. If the processing throws an exception, the machine is in an invalid state
        // and should be removed from the list of running machines.
        try {
          stateMachine.processInput(curCharAndCodeLoc.a);
        } catch (IllegalStateException e) {
          String currentInput = stateMachine.getAcceptedInput() + curCharAndCodeLoc.a;
          log.debug("State machine does not match input {}: {}", currentInput, e.getMessage());
          runningMachines.remove(stateMachine);
          continue;
        }

        // If the machine is in an accepting state, add it to the list of accepting machines
        if (stateMachine.isInAcceptState()) {
          acceptingMachines.remove(stateMachine);
          acceptingMachines.put(stateMachine, stateMachine.getAcceptedInput().length());
          // Clear the input buffer to make sure we backtrack to this point in the input in case
          // no other running machine accepts later.
          newInputBuffer.clear();
        }
      }
    }

    // Add the remaining characters to the input buffer
    inputBuffer.addAll(newInputBuffer);

    // If EOF is reached, finalize the token
    if (acceptingMachines.isEmpty()) {
      currentToken = new Token(TokenType.TOK_INVALID, "", tokenCodeLoc);
      return;
    }

    // Check which of the running machines has the highest priority and set the current token accordingly
    Map.Entry<StateMachine, Integer> winningEntry = null;
    for (Map.Entry<StateMachine, Integer> entry : acceptingMachines.entrySet())
      if (winningEntry == null || entry.getValue().compareTo(winningEntry.getValue()) > 0)
        winningEntry = entry;
    StateMachine winningMachine = winningEntry.getKey();
    currentToken = new Token(winningMachine.getTokenType(), winningMachine.getAcceptedInput(), tokenCodeLoc);
    if (dumpTokens)
      System.out.println(currentToken);
  }

  @Override
  public void expect(TokenType expectedType) throws RuntimeException {
    if (currentToken.getType() != expectedType)
      throw new RuntimeException("Unexpected token: " + currentToken.getType() + " at " + currentToken.getCodeLoc() + ". Expected: " + expectedType);
    advance();
  }

  @Override
  public void expectOneOf(Set<TokenType> expectedTypes) throws RuntimeException {
    if (!expectedTypes.contains(currentToken.getType()))
      throw new RuntimeException("Unexpected token: " + currentToken.getType() + " at " + currentToken.getCodeLoc() + ". Expected one of: " + expectedTypes);
    advance();
  }

  @Override
  public boolean isEOF() {
    return reader.isEOF();
  }

  @Override
  public CodeLoc getCodeLoc() {
    return reader.getCodeLoc();
  }

  private void skipComments() {
    if (peekChar() == '#') {
      do {
        getCurrentCharAndCodeLoc();
      } while ((peekChar() != '\n'));
      getCurrentCharAndCodeLoc();
    }
  }

  private void skipWhitespaces() {
    while (!(reader.isEOF() && inputBuffer.isEmpty()) && Character.isWhitespace(peekChar()))
      getCurrentCharAndCodeLoc();
  }
}