package com.auberer.compilerdesignlectureproject.ast;

import com.auberer.compilerdesignlectureproject.lexer.TokenType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ASTSwitchCaseStmtNode extends ASTNode {
  @Override
  public <T> T accept(ASTVisitor<T> visitor) {
    return visitor.visitSwitchCaseStmt(this);
  }

  public static Set<TokenType> getSelectionSet() {
    Set<TokenType> selectionSet = new HashSet<>();
    selectionSet.add(TokenType.TOK_SWITCH);
    return selectionSet;
  }

  public ASTTernaryExprNode getCondition() {
    return getChild(ASTTernaryExprNode.class, 0);
  }

  public List<ASTCaseBlockNode> getCaseBlocks() {
    return getChildren(ASTCaseBlockNode.class);
  }

  public ASTDefaultBlockNode getDefaultBlock() {
    return getChild(ASTDefaultBlockNode.class, 0);
  }
}
