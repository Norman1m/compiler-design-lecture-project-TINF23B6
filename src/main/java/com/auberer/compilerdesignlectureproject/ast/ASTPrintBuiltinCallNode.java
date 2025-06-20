package com.auberer.compilerdesignlectureproject.ast;

import com.auberer.compilerdesignlectureproject.lexer.TokenType;

import java.util.HashSet;
import java.util.Set;

public class ASTPrintBuiltinCallNode extends ASTNode {
  @Override
  public <T> T accept(ASTVisitor<T> visitor) {
    return visitor.visitPrintBuiltin(this);
  }

  public static Set<TokenType> getSelectionSet() {
    Set<TokenType> selectionSet = new HashSet<>();
    selectionSet.add(TokenType.TOK_PRINT);
    return selectionSet;
  }

  public ASTTernaryExprNode getExpr() {
    return getChild(ASTTernaryExprNode.class, 0);
  }
}
