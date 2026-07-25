package com.nirithy.lxclua

/**
 * Lua 词法 Token 类型枚举
 * 与 C 层 llex.h RESERVED 枚举和 lparser.c 实际使用保持完全一致
 * 死代码 token（如 TK_ANDANDEQ, TK_OROREQ）已移除，lparser 未使用
 */
enum class LuaTokenTypes {
    // === 特殊标记 ===
    SHEBANG_CONTENT,
    NEW_LINE,
    WHITE_SPACE,
    BAD_CHARACTER,

    // === 字面量 ===
    NAME,
    NUMBER,
    STRING,
    LONG_STRING,
    RAW_STRING,        // _raw"..." / _raw'...' / _raw[[...]]
    INTERPSTRING,      // $"..." / f"..." 插值字符串
    REGEX,             // /pattern/flags 正则字面量

    // === 单字符运算符 ===
    PLUS,              // +
    MINUS,             // -
    MULT,              // *
    DIV,               // /
    MOD,               // %
    EXP,               // ^
    GETN,              // #
    BIT_AND,           // &
    BIT_OR,            // |
    BIT_TILDE,         // ~
    LT,                // <
    GT,                // >
    ASSIGN,            // =
    DOT,               // .
    QUESTION,          // ?

    // === 分隔符 ===
    LPAREN,            // (
    RPAREN,            // )
    LBRACK,            // [
    RBRACK,            // ]
    LCURLY,            // {
    RCURLY,            // }
    COMMA,             // ,
    SEMI,              // ;
    COLON,             // :
    AT,                // @
    DOLLAR,            // $

    // === 多字符运算符 ===
    CONCAT,            // ..
    ELLIPSIS,          // ...
    DOUBLE_DIV,        // //
    EQ,                // ==
    NE,                // ~= 或 !=
    GE,                // >=
    LE,                // <=
    SPACESHIP,         // <=>
    BIT_LTLT,          // <<
    BIT_RTRT,          // >>
    DOUBLE_COLON,      // ::
    LEF,               // ->
    MEAN,              // =>
    WALRUS,            // :=
    PIPE,              // |>
    REVPIPE,           // <|
    SAFEPIPE,          // |?>
    MERGE,             // <> 表合并操作符
    DOLLDOLL,          // $$

    // === 复合赋值运算符 ===
    ADDEQ,             // +=
    SUBEQ,             // -=
    MULEQ,             // *=
    DIVEQ,             // /=
    IDIVEQ,            // //=
    MODEQ,             // %=
    BANDEQ,            // &=
    BOREQ,             // |=
    BXOREQ,            // ~= (位异或赋值，与 NE 共用 ~=)
    POWEQ,             // ^=
    SHREQ,             // >>=
    SHLEQ,             // <<=
    CONCATEQ,          // ..=
    PLUSPLUS,          // ++

    // === 可选链和空值合并 ===
    OPTCHAIN,          // ?.
    NULLCOAL,          // ??
    NULLCOALEQ,        // ??=

    // === 关键字（与 C 层 luaX_tokens 顺序一致）===
    AND,               // and
    ASM,               // asm
    ASTPARSER,         // astparser
    ASYNC,             // async
    AWAIT,             // await
    BOOL,              // bool
    BREAK,             // break
    CASE,              // case
    CATCH,             // catch
    CHAR,              // char
    COMMAND,           // command
    CONCEPT,           // concept
    CONST,             // const
    CONTINUE,          // continue
    DEFAULT,           // default
    DEFER,             // defer
    DELETE,            // delete
    DO,                // do
    DOUBLE,            // double
    ELSE,              // else
    ELSEIF,            // elseif
    END,               // end
    ENUM,              // enum
    EXPORT,            // export
    FALSE,             // false
    FINALLY,           // finally
    FLOAT,             // float
    FOR,               // for
    FUNCTION,          // function
    GLOBAL,            // global
    GUARD,             // guard
    GOTO,              // goto
    IF,                // if
    IN,                // in
    TYPE_INT,          // int
    IS,                // is
    INSTANCEOF,        // instanceof
    KEYWORD,           // keyword
    LAMBDA,            // lambda
    LOCAL,             // local
    LONG,              // long
    NAMESPACE,         // namespace
    NIL,               // nil
    NOT,               // not
    OPERATOR_KW,       // operator
    OR,                // or
    REPEAT,            // repeat
    REQUIRES,          // requires
    RETURN,            // return
    STRUCT,            // struct
    SUPERSTRUCT,       // superstruct
    SWITCH,            // switch
    TAKE,              // take
    THEN,              // then
    TRUE,              // true
    TRY,               // try
    UNTIL,             // until
    USING,             // using
    VOID,              // void
    WHEN,              // when
    WHILE,             // while
    WITH,              // with
    LET,               // let

    // === OOP 扩展关键字（编辑器保留高亮） ===
    ABSTRACT,          // abstract
    CLASS,             // class
    EXTENDS,           // extends
    FINAL,             // final
    IMPLEMENTS,        // implements
    INTERFACE,         // interface
    NEW,               // new
    SUPER,             // super
    PRIVATE,           // private
    PROTECTED,         // protected
    PUBLIC,            // public
    STATIC,            // static
    AS,                // as
    MATCH,             // match

    // === 注释 ===
    SHORT_COMMENT,     // --...
    BLOCK_COMMENT,     // --[[...]]
    DOC_COMMENT,       // ---...
    REGION,            // --region
    ENDREGION,         // --endregion
    SHEBANG,           // #!
    LABEL,             // ::label::
}