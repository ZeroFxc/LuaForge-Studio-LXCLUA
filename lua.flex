package com.nirithy.lxclua;

import static com.nirithy.lxclua.LuaTokenTypes.*;
import java.io.IOException;
import java.io.Reader;

%%

%class LuaLexer
%public
%unicode
%line
%column
%char
%type LuaTokenTypes
%function advance

%state xSHEBANG
%state xDOUBLE_QUOTED_STRING
%state xSINGLE_QUOTED_STRING
%state xBLOCK_STRING
%state xBLOCK_COMMENT
%state xREGEX

%{
  /**
   * CharSequence 适配器，用于从 CharSequence 读取字符
   */
  static class CharSeqReader extends Reader
  {
    int offset = 0;
    CharSequence src;

    CharSeqReader(CharSequence src)
    {
      this.src = src;
    }

    @Override
    public void close() throws IOException
    {
      src = null;
      offset = 0;
    }

    @Override
    public int read(char[] chars, int i, int i1) throws IOException
    {
      int len = Math.min(src.length() - offset, i1);
      for (int n = 0; n < len; n++)
      {
        try{
          char c = src.charAt(offset++);
          chars[i++] = c;
        }catch(Exception e){
          // ignore
        }
      }
      if (len <= 0)
        return -1;
      return len;
    }
  }

  /**
   * 从 CharSequence 创建词法分析器
   */
  public LuaLexer(CharSequence src) {
    this(new CharSeqReader(src));
  }

  /**
   * 获取当前行号
   */
  public int yyline(){
    return yyline;
  }
  
  /**
   * 获取当前列号
   */
  public int yycolumn(){
    return yycolumn;
  }
  
  /**
   * 获取当前字符位置
   */
  public int yychar(){
    return (int)yychar;
  }
  
  // 块字符串/注释的等号数量
  private int nBrackets = 0;
  // 块内容缓冲区
  private StringBuilder blockBuffer = new StringBuilder();
  // 块开始标记
  private String blockStart = "";
  // 用于存储完整的块内容（开始标记 + 内容 + 结束标记）
  private String fullBlockContent = "";
  
  /**
   * 检查指定偏移处是否为指定字符
   */
  private boolean checkAhead(char c, int offset) {
    int pos = this.zzMarkedPos + offset;
    return pos < this.zzBuffer.length && this.zzBuffer[pos] == c;
  }

  /**
   * 检查并解析块字符串/注释的开始标记 [=*[
   * 设置 nBrackets 为等号数量
   * @return 是否是有效的块开始标记
   */
  private boolean checkBlock() {
    nBrackets = 0;
    if (checkAhead('[', 0)) {
      int n = 0;
      while (checkAhead('=', n + 1)) n++;
      if (checkAhead('[', n + 1)) {
        nBrackets = n;
        return true;
      }
    }
    return false;
  }

  /**
   * 从当前匹配文本中解析块开始标记的等号数量
   */
  private void parseBlockBrackets() {
    String text = yytext();
    nBrackets = 0;
    for (int i = 1; i < text.length() - 1; i++) {
      if (text.charAt(i) == '=') nBrackets++;
    }
  }

  /**
   * 检查块字符串/注释是否已闭合
   * @return 闭合标记后多余的字符数，-1 表示未闭合
   */
  private int checkBlockEnd() {
    String cs = blockBuffer.toString();
    StringBuilder closer = new StringBuilder("]");
    for (int i = 0; i < nBrackets; i++) closer.append('=');
    closer.append(']');
    int index = cs.indexOf(closer.toString());
    if (index >= 0) {
      return cs.length() - index - nBrackets - 2;
    }
    return -1;
  }

  /**
   * 获取完整的块内容（长字符串或块注释）
   * @return 完整的块内容
   */
  public String getBlockContent() {
    return fullBlockContent;
  }

%}

/* 基本定义 */
LineTerminator = \r|\n|\r\n
WhiteSpace = [ \t\f]+
Identifier = [a-zA-Z_][a-zA-Z0-9_]*

/* 数字 - 完整支持 Lua 5.4 + 扩展 */
Digit = [0-9]
HexDigit = [0-9a-fA-F]
BinDigit = [01]
/* 十进制数：整数、浮点数、科学计数法，支持数字分隔符 _ */
DecInteger = {Digit}("_"?{Digit})*
DecNumber = {DecInteger}(\.{Digit}*)?([eE][+-]?{DecInteger})?
/* 十六进制数：整数、浮点数、二进制指数，支持数字分隔符 _ */
HexInteger = {HexDigit}("_"?{HexDigit})*
HexNumber = 0[xX]{HexInteger}(\.{HexDigit}*)?([pP][+-]?{DecInteger})?
/* 二进制数（扩展）*/
BinNumber = 0[bB]{BinDigit}("_"?{BinDigit})*
Number = {DecNumber} | {HexNumber} | {BinNumber}

%%

<YYINITIAL> {
  /* Shebang - 必须在文件开头 */
  "#!"                      { yybegin(xSHEBANG); return SHEBANG; }

  /* 空白和换行 */
  {LineTerminator}          { return NEW_LINE; }
  {WhiteSpace}              { return WHITE_SPACE; }

  /* 关键字 - 与 C 层 llex.c luaX_tokens 完全一致 */
  "and"                     { return AND; }
  "asm"                     { return ASM; }
  "async"                   { return ASYNC; }
  "await"                   { return AWAIT; }
  "bool"                    { return BOOL; }
  "break"                   { return BREAK; }
  "case"                    { return CASE; }
  "catch"                   { return CATCH; }
  "char"                    { return CHAR; }
  "command"                 { return COMMAND; }
  "concept"                 { return CONCEPT; }
  "const"                   { return CONST; }
  "continue"                { return CONTINUE; }
  "default"                 { return DEFAULT; }
  "defer"                   { return DEFER; }
  "delete"                  { return DELETE; }
  "do"                      { return DO; }
  "double"                  { return DOUBLE; }
  "else"                    { return ELSE; }
  "elseif"                  { return ELSEIF; }
  "end"                     { return END; }
  "enum"                    { return ENUM; }
  "export"                  { return EXPORT; }
  "false"                   { return FALSE; }
  "finally"                 { return FINALLY; }
  "float"                   { return FLOAT; }
  "for"                     { return FOR; }
  "function"                { return FUNCTION; }
  "global"                  { return GLOBAL; }
  "guard"                   { return GUARD; }
  "goto"                    { return GOTO; }
  "if"                      { return IF; }
  "in"                      { return IN; }
  "int"                     { return TYPE_INT; }
  "is"                      { return IS; }
  "instanceof"              { return INSTANCEOF; }
  "keyword"                 { return KEYWORD; }
  "lambda"                  { return LAMBDA; }
  "let"                     { return LET; }
  "local"                   { return LOCAL; }
  "long"                    { return LONG; }
  "namespace"               { return NAMESPACE; }
  "nil"                     { return NIL; }
  "not"                     { return NOT; }
  "operator"                { return OPERATOR_KW; }
  "or"                      { return OR; }
  "repeat"                  { return REPEAT; }
  "requires"                { return REQUIRES; }
  "return"                  { return RETURN; }
  "struct"                  { return STRUCT; }
  "superstruct"             { return SUPERSTRUCT; }
  "switch"                  { return SWITCH; }
  "take"                    { return TAKE; }
  "match"                   { return MATCH; }
  "then"                    { return THEN; }
  "true"                    { return TRUE; }
  "try"                     { return TRY; }
  "until"                   { return UNTIL; }
  "using"                   { return USING; }
  "void"                    { return VOID; }
  "when"                    { return WHEN; }
  "with"                    { return WITH; }
  "while"                   { return WHILE; }

  /* OOP 面向对象关键字（编辑器保留高亮） */
  "abstract"                { return ABSTRACT; }
  "class"                   { return CLASS; }
  "extends"                 { return EXTENDS; }
  "final"                   { return FINAL; }
  "implements"              { return IMPLEMENTS; }
  "interface"               { return INTERFACE; }
  "new"                     { return NEW; }
  "super"                   { return SUPER; }
  "private"                 { return PRIVATE; }
  "protected"               { return PROTECTED; }
  "public"                  { return PUBLIC; }
  "static"                  { return STATIC; }
  "as"                      { return AS; }

  /* region/endregion 注释 */
  "--region"[^\r\n]*        { return REGION; }
  "--endregion"[^\r\n]*     { return ENDREGION; }

  /* 标识符 */
  {Identifier}              { return NAME; }

  /* 数字 */
  {Number}                  { return NUMBER; }

  /* === 插值字符串前缀 $"..." / $'...' — 必须在普通字符串前匹配 === */
  \$\"([^\"\\]|\\[^\r\n]|\\[\r\n])*\"   { return INTERPSTRING; }
  \$'([^\'\\]|\\[^\r\n]|\\[\r\n])*\'   { return INTERPSTRING; }
  /* f"..." / f'...' 插值字符串 */
  f\"([^\"\\]|\\[^\r\n]|\\[\r\n])*\"   { return INTERPSTRING; }
  f\'([^\'\\]|\\[^\r\n]|\\[\r\n])*\'   { return INTERPSTRING; }

  /* 原生字符串 _raw"..." _raw'...' - 不处理转义 */
  _raw\"[^\"]*\"            { return RAW_STRING; }
  _raw\'[^\']*\'            { return RAW_STRING; }
  
  /* 原生长字符串 _raw[[...]] _raw[=[...]=] 等 */
  _raw"["=*"["              { 
                              blockStart = yytext();
                              nBrackets = 0;
                              for (int i = 2; i < blockStart.length() - 1; i++) {
                                if (blockStart.charAt(i) == '=') nBrackets++;
                              }
                              blockBuffer.setLength(0);
                              yybegin(xBLOCK_STRING);
                            }

  /* 长字符串 [[...]], [=[...]=], [==[...]==], 等 */
  "["=*"["                  { 
                              blockStart = yytext();
                              parseBlockBrackets();
                              blockBuffer.setLength(0);
                              yybegin(xBLOCK_STRING);
                            }

  /* 普通字符串 - 完整匹配（包含转义） */
  \"([^\"\\]|\\[^\r\n]|\\[\r\n])*\"   { return STRING; }
  \'([^\'\\]|\\[^\r\n]|\\[\r\n])*\'   { return STRING; }
  
  /* 未闭合字符串 - 进入状态继续匹配 */
  \"                        { yybegin(xDOUBLE_QUOTED_STRING); }
  \'                        { yybegin(xSINGLE_QUOTED_STRING); }

  /* 文档注释 ---... */
  "---"[^\r\n]*             { return DOC_COMMENT; }
  
  /* 块注释 --[[...]], --[=[...]=], 等 */
  "--["=*"["                { 
                              blockStart = yytext();
                              nBrackets = 0;
                              for (int i = 3; i < blockStart.length() - 1; i++) {
                                if (blockStart.charAt(i) == '=') nBrackets++;
                              }
                              blockBuffer.setLength(0);
                              yybegin(xBLOCK_COMMENT);
                            }
  
  /* 单行注释 --... */
  "--"[^\r\n]*              { return SHORT_COMMENT; }

  /* === 复合赋值运算符 - 长的优先匹配 === */
  "//="                     { return IDIVEQ; }
  ">>="                     { return SHREQ; }
  "<<="                     { return SHLEQ; }
  "..="                     { return CONCATEQ; }
  "??="                     { return NULLCOALEQ; }
  "+="                      { return ADDEQ; }
  "-="                      { return SUBEQ; }
  "*="                      { return MULEQ; }
  "/="                      { return DIVEQ; }
  "%="                      { return MODEQ; }
  "&="                      { return BANDEQ; }
  "|="                      { return BOREQ; }
  "^="                      { return POWEQ; }
  "++"                      { return PLUSPLUS; }

  /* === 可选链和空值合并 - 长的优先 === */
  "?."                      { return OPTCHAIN; }
  "??"                      { return NULLCOAL; }
  "?"                       { return QUESTION; }

  /* === 多字符运算符 - 长的优先匹配 === */
  "..."                     { return ELLIPSIS; }
  ".."                      { return CONCAT; }
  "//"                      { return DOUBLE_DIV; }
  "=="                      { return EQ; }
  "!="                      { return NE; }
  "~="                      { return NE; }
  ">="                      { return GE; }
  "<=>"                     { return SPACESHIP; }
  "<="                      { return LE; }
  "<>"                      { return MERGE; }
  "<<"                      { return BIT_LTLT; }
  ">>"                      { return BIT_RTRT; }
  "::"                      { return DOUBLE_COLON; }
  "->"                      { return LEF; }
  "=>"                      { return MEAN; }
  ":="                      { return WALRUS; }
  "|?>"                     { return SAFEPIPE; }
  "|>"                      { return PIPE; }
  "<|"                      { return REVPIPE; }
  "&&"                      { return AND; }
  "$$"                      { return DOLLDOLL; }

  /* 单字符运算符 */
  "+"                       { return PLUS; }
  "-"                       { return MINUS; }
  "*"                       { return MULT; }
  "/"                       { return DIV; }
  "%"                       { return MOD; }
  "^"                       { return EXP; }
  "#"                       { return GETN; }
  "&"                       { return BIT_AND; }
  "|"                       { return BIT_OR; }
  "~"                       { return BIT_TILDE; }
  "!"                       { return NOT; }
  "<"                       { return LT; }
  ">"                       { return GT; }
  "="                       { return ASSIGN; }

  /* 分隔符 */
  "("                       { return LPAREN; }
  ")"                       { return RPAREN; }
  "{"                       { return LCURLY; }
  "}"                       { return RCURLY; }
  "["                       { return LBRACK; }
  "]"                       { return RBRACK; }
  ","                       { return COMMA; }
  ";"                       { return SEMI; }
  ":"                       { return COLON; }
  "."                       { return DOT; }
  "@"                       { return AT; }
  "$"                       { return DOLLAR; }

  /* 正则字面量 /pattern/flags — 仅当 / 紧跟非空白非运算符时尝试 */
  "/"[^/\ \t\f\r\n0-9\(\)\[\]\{\}\,\;\:\+\-\*\%\^\#\&\|\~\<\>\=\.\@\$\?\!][^/\r\n]*"/"[a-zA-Z]*  { return REGEX; }

  /* 未知字符 */
  .                         { return BAD_CHARACTER; }
}

/* Shebang 状态 - 读取到行尾 */
<xSHEBANG> {
  [^\r\n]+                  { }
  {LineTerminator}          { yybegin(YYINITIAL); return SHEBANG_CONTENT; }
  <<EOF>>                   { yybegin(YYINITIAL); return SHEBANG_CONTENT; }
}

/* 双引号字符串状态 */
<xDOUBLE_QUOTED_STRING> {
  ([^\"\\]|\\[^\r\n]|\\[\r\n])*\"  { yybegin(YYINITIAL); return STRING; }
  [^\"\\\r\n]+              { }
  \\.                       { }
  {LineTerminator}          { yybegin(YYINITIAL); return BAD_CHARACTER; }
  <<EOF>>                   { yybegin(YYINITIAL); return BAD_CHARACTER; }
}

/* 单引号字符串状态 */
<xSINGLE_QUOTED_STRING> {
  ([^\'\\]|\\[^\r\n]|\\[\r\n])*\'  { yybegin(YYINITIAL); return STRING; }
  [^\'\\\r\n]+              { }
  \\.                       { }
  {LineTerminator}          { yybegin(YYINITIAL); return BAD_CHARACTER; }
  <<EOF>>                   { yybegin(YYINITIAL); return BAD_CHARACTER; }
}

/* 块字符串状态 [[...]], [=[...]=], 等 */
<xBLOCK_STRING> {
  "]"=*"]"                  { 
                              String text = yytext();
                              int eqs = text.length() - 2;
                              blockBuffer.append(text);
                              if (eqs == nBrackets) {
                                fullBlockContent = blockStart + blockBuffer.toString();
                                yybegin(YYINITIAL);
                                return LONG_STRING;
                              }
                            }
  [^\]]+                    { blockBuffer.append(yytext()); }
  "]"                       { blockBuffer.append(yytext()); }
  <<EOF>>                   { 
                              fullBlockContent = blockStart + blockBuffer.toString();
                              yybegin(YYINITIAL); 
                              return BAD_CHARACTER; 
                            }
}

/* 块注释状态 --[[...]], --[=[...]=], 等 */
<xBLOCK_COMMENT> {
  "]"=*"]"                  { 
                              String text = yytext();
                              int eqs = text.length() - 2;
                              blockBuffer.append(text);
                              if (eqs == nBrackets) {
                                fullBlockContent = blockStart + blockBuffer.toString();
                                yybegin(YYINITIAL);
                                return BLOCK_COMMENT;
                              }
                            }
  [^\]]+                    { blockBuffer.append(yytext()); }
  "]"                       { blockBuffer.append(yytext()); }
  <<EOF>>                   { 
                              fullBlockContent = blockStart + blockBuffer.toString();
                              yybegin(YYINITIAL); 
                              return BLOCK_COMMENT; 
                            }
}

/* 文件结束 */
<<EOF>>                     { return null; }