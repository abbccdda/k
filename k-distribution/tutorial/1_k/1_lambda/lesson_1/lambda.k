// Copyright (c) 2012-2016 K Team. All Rights Reserved.

module LAMBDA
  syntax Val ::= Id
               | "lambda" Id "." Exp
  syntax Exp ::= Val
               | Exp Exp      [left]
               | "(" Exp ")"  [bracket]
endmodule
