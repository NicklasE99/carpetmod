/*
 * Copyright 2012-2018 Udo Klimaschewski
 *
 * http://UdoJava.com/
 * http://about.me/udo.klimaschewski
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */
package carpetscript;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.stream.Collectors;


/**
 * <h1>EvalEx - Java Expression Evaluator</h1>
 *
 * <h2>Introduction</h2> EvalEx is a handy expression evaluator for Java, that
 * allows to evaluate simple mathematical and boolean expressions. <br>
 * For more information, see:
 * <a href="https://github.com/uklimaschewski/EvalEx">EvalEx GitHub
 * repository</a>
 * <ul>
 * <li>The software is licensed under the MIT Open Source license (see <a href=
 * "https://raw.githubusercontent.com/uklimaschewski/EvalEx/master/LICENSE">LICENSE
 * file</a>).</li>
 * <li>The *power of* operator (^) implementation was copied from <a href=
 * "http://stackoverflow.com/questions/3579779/how-to-do-a-fractional-power-on-bigdecimal-in-java">Stack
 * Overflow</a>. Thanks to Gene Marin.</li>
 * <li>The SQRT() function implementation was taken from the book <a href=
 * "http://www.amazon.de/Java-Number-Cruncher-Programmers-Numerical/dp/0130460419">The
 * Java Programmers Guide To numerical Computing</a> (Ronald Mak, 2002).</li>
 * </ul>
 *
 * @authors Thanks to all who contributed to this project: <a href=
 * "https://github.com/uklimaschewski/EvalEx/graphs/contributors">Contributors</a>
 * @see <a href="https://github.com/uklimaschewski/EvalEx">GitHub repository</a>
 */
public class Expression
{

    /**
     * Unary operators precedence: + and - as prefix
     */
    public static final int OPERATOR_PRECEDENCE_UNARY = 60;

    /**
     * Equality operators precedence: =, ==, !=. <>
     */
    public static final int OPERATOR_PRECEDENCE_EQUALITY = 7;

    /**
     * Comparative operators precedence: <,>,<=,>=
     */
    public static final int OPERATOR_PRECEDENCE_COMPARISON = 10;

    /**
     * Or operator precedence: ||
     */
    public static final int OPERATOR_PRECEDENCE_OR = 3;

    /**
     * And operator precedence: &&
     */
    public static final int OPERATOR_PRECEDENCE_AND = 4;

    /**
     * Power operator precedence: ^
     */
    public static final int OPERATOR_PRECEDENCE_POWER = 40;

    /**
     * Multiplicative operators precedence: *,/,%
     */
    public static final int OPERATOR_PRECEDENCE_MULTIPLICATIVE = 30;

    /**
     * Additive operators precedence: + and -
     */
    public static final int OPERATOR_PRECEDENCE_ADDITIVE = 20;

    /**
     * next command operator: ;
     */
    public static final int OPERATOR_PRECEDENCE_NEXTOP = 1;

    /**
     * Assignment operator precedence: =
     */
    public static final int OPERATOR_PRECEDENCE_ASSIGN = 2;

    /**
     * Definition of PI as a constant, can be used in expressions as variable.
     */
    public static final Value PI = new NumericValue(
            "3.1415926535897932384626433832795028841971693993751058209749445923078164062862089986280348253421170679");


    /**
     * Definition of e: "Euler's number" as a constant, can be used in
     * expressions as variable.
     */
    public static final Value e = new NumericValue(
            "2.71828182845904523536028747135266249775724709369995957496696762772407663");

    /**
     * The {@link MathContext} to use for calculations.
     */
    private MathContext mc;


    /**
     * The current infix expression, with optional variable substitutions.
     */
    private String expression;

    /**
     * The cached RPN (Reverse Polish Notation) of the expression.
     */
    private List<Token> rpn = null;

    /**
     * All defined operators with name and implementation.
     */
    private Map<String, ILazyOperator> operators = new HashMap<>();

    /**
     * All defined functions with name and implementation.
     */
    private Map<String, ILazyFunction> functions = new HashMap<>();

    /**
     * All defined variables with name and value.
     */
    private Map<String, LazyValue> variables = new HashMap<>();

    /**
     * What character to use for decimal separators.
     */
    private static final char decimalSeparator = '.';

    /**
     * What character to use for minus sign (negative values).
     */
    private static final char minusSign = '-';

    private Consumer<String> logOutput = null;

    /**
     * LazyNumber interface created for lazily evaluated functions
     */
    @FunctionalInterface
    public interface LazyValue
    {
        Value eval();
    }

    /**
     * The Value representation of the left parenthesis, used for parsing
     * varying numbers of function parameters.
     */
    private static final LazyValue PARAMS_START = () -> null;

    /**
     * The expression evaluators exception class.
     */
    public static class ExpressionException extends RuntimeException
    {
        public ExpressionException(String message)
        {
            super(message);
        }
    }

    public static LazyValue FALSE = () -> Value.FALSE;
    public static LazyValue TRUE = () -> Value.TRUE;
    public static LazyValue EMPTY = () -> Value.EMPTY;
    public static LazyValue ZERO = () -> Value.ZERO;

    enum TokenType
    {
        VARIABLE, FUNCTION, LITERAL, OPERATOR, UNARY_OPERATOR, OPEN_PAREN, COMMA, CLOSE_PAREN, HEX_LITERAL, STRINGPARAM
    }

    class Token
    {
        public String surface = "";
        public TokenType type;
        public int pos;

        public void append(char c)
        {
            surface += c;
        }

        public void append(String s)
        {
            surface += s;
        }

        public char charAt(int pos)
        {
            return surface.charAt(pos);
        }

        public int length()
        {
            return surface.length();
        }

        @Override
        public String toString()
        {
            return surface;
        }
    }

    /**
     * Expression tokenizer that allows to iterate over a {@link String}
     * expression token by token. Blank characters will be skipped.
     */
    private class Tokenizer implements Iterator<Token>
    {

        /**
         * Actual position in expression string.
         */
        private int pos = 0;

        /**
         * The original input expression.
         */
        private String input;
        /**
         * The previous token or <code>null</code> if none.
         */
        private Token previousToken;

        /**
         * Creates a new tokenizer for an expression.
         *
         * @param input The expression string.
         */
        public Tokenizer(String input)
        {
            this.input = input.trim();
        }

        @Override
        public boolean hasNext()
        {
            return (pos < input.length());
        }

        /**
         * Peek at the next character, without advancing the iterator.
         *
         * @return The next character or character 0, if at end of string.
         */
        private char peekNextChar()
        {
            if (pos < (input.length() - 1))
            {
                return input.charAt(pos + 1);
            }
            else
            {
                return 0;
            }
        }

        private boolean isHexDigit(char ch)
        {
            return ch == 'x' || ch == 'X' || (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f')
                    || (ch >= 'A' && ch <= 'F');
        }

        @Override
        public Token next()
        {
            Token token = new Token();

            if (pos >= input.length())
            {
                return previousToken = null;
            }
            char ch = input.charAt(pos);
            while (Character.isWhitespace(ch) && pos < input.length())
            {
                ch = input.charAt(++pos);
            }
            token.pos = pos;

            boolean isHex = false;

            if (Character.isDigit(ch) || (ch == decimalSeparator && Character.isDigit(peekNextChar())))
            {
                if (ch == '0' && (peekNextChar() == 'x' || peekNextChar() == 'X'))
                    isHex = true;
                while ((isHex
                        && isHexDigit(
                        ch))
                        || (Character.isDigit(ch) || ch == decimalSeparator || ch == 'e' || ch == 'E'
                        || (ch == minusSign && token.length() > 0
                        && ('e' == token.charAt(token.length() - 1)
                        || 'E' == token.charAt(token.length() - 1)))
                        || (ch == '+' && token.length() > 0
                        && ('e' == token.charAt(token.length() - 1)
                        || 'E' == token.charAt(token.length() - 1))))
                        && (pos < input.length()))
                {
                    token.append(input.charAt(pos++));
                    ch = pos == input.length() ? 0 : input.charAt(pos);
                }
                token.type = isHex ? TokenType.HEX_LITERAL : TokenType.LITERAL;
            }
            else if (ch == '\'')
            {
                pos++;
                if (previousToken == null || previousToken.type != TokenType.STRINGPARAM)
                {
                    ch = input.charAt(pos);
                    while (ch != '\'')
                    {
                        token.append(input.charAt(pos++));
                        ch = pos == input.length() ? 0 : input.charAt(pos);
                    }
                    pos++;
                    token.type = TokenType.STRINGPARAM;
                }
                else
                {
                    return next();
                }
            }
            else if (Character.isLetter(ch) || "_".indexOf(ch) >= 0)
            {
                while ((Character.isLetter(ch) || Character.isDigit(ch) || "_".indexOf(ch) >= 0
                        || token.length() == 0 && "_".indexOf(ch) >= 0) && (pos < input.length()))
                {
                    token.append(input.charAt(pos++));
                    ch = pos == input.length() ? 0 : input.charAt(pos);
                }
                // Remove optional white spaces after function or variable name
                if (Character.isWhitespace(ch))
                {
                    while (Character.isWhitespace(ch) && pos < input.length())
                    {
                        ch = input.charAt(pos++);
                    }
                    pos--;
                }
                token.type = ch == '(' ? TokenType.FUNCTION : TokenType.VARIABLE;
            }
            else if (ch == '(' || ch == ')' || ch == ',')
            {
                if (ch == '(')
                {
                    token.type = TokenType.OPEN_PAREN;
                }
                else if (ch == ')')
                {
                    token.type = TokenType.CLOSE_PAREN;
                }
                else
                {
                    token.type = TokenType.COMMA;
                }
                token.append(ch);
                pos++;
            }
            else
            {
                String greedyMatch = "";
                int initialPos = pos;
                ch = input.charAt(pos);
                int validOperatorSeenUntil = -1;
                while (!Character.isLetter(ch) && !Character.isDigit(ch) && "_".indexOf(ch) < 0
                        && !Character.isWhitespace(ch) && ch != '(' && ch != ')' && ch != ','
                        && (pos < input.length()))
                {
                    greedyMatch += ch;
                    pos++;
                    if (operators.containsKey(greedyMatch))
                    {
                        validOperatorSeenUntil = pos;
                    }
                    ch = pos == input.length() ? 0 : input.charAt(pos);
                }
                if (validOperatorSeenUntil != -1)
                {
                    token.append(input.substring(initialPos, validOperatorSeenUntil));
                    pos = validOperatorSeenUntil;
                }
                else
                {
                    token.append(greedyMatch);
                }

                if (previousToken == null || previousToken.type == TokenType.OPERATOR
                        || previousToken.type == TokenType.OPEN_PAREN || previousToken.type == TokenType.COMMA)
                {
                    token.surface += "u";
                    token.type = TokenType.UNARY_OPERATOR;
                }
                else
                {
                    token.type = TokenType.OPERATOR;
                }
            }
            return previousToken = token;
        }

        @Override
        public void remove()
        {
            throw new ExpressionException("remove() not supported");
        }

    }

    public Value assertNotNull(Value v1)
    {
        if (v1 == null)
            throw new ExpressionException("Operand may not be null");
        return v1;
    }

    public BigDecimal getNumericalValue(Value v1)
    {
        if (!(v1 instanceof NumericValue))
            throw new ExpressionException("Operand has to be of a numeric type");
        return ((NumericValue) v1).getNumber();
    }

    public void assertNotNull(Value v1, Value v2)
    {
        if (v1 == null)
            throw new ExpressionException("First operand may not be null");
        if (v2 == null)
            throw new ExpressionException("Second operand may not be null");
    }


    public LazyValue assertNotNull(LazyValue lv)
    {
        if (lv == null)
            throw new ExpressionException("Operand may not be null");
        return lv;
    }

    public void addLazyBinaryOperator(String surface, int precedence, boolean leftAssoc,
                            java.util.function.BiFunction<LazyValue, LazyValue, LazyValue> lazyfun)
    {
        operators.put(surface, new AbstractLazyOperator(surface, precedence, leftAssoc)
        {
            @Override
            public LazyValue eval(LazyValue v1, LazyValue v2) //TODO add nonnull check here, and strip null checks from
            {
                return lazyfun.apply(v1, v2);
            }
        });
    }


    public void addUnaryOperator(String surface, boolean leftAssoc, java.util.function.Function<Value, Value> fun)
    {
        operators.put(surface+"u", new AbstractUnaryOperator(surface, OPERATOR_PRECEDENCE_UNARY, leftAssoc)
        {
            @Override
            public Value evalUnary(Value v1)
            {
                return fun.apply(assertNotNull(v1));
            }
        });
    }

    public void addBinaryOperator(String surface, int precedence, boolean leftAssoc, java.util.function.BiFunction<Value, Value, Value> fun)
    {
        operators.put(surface, new AbstractOperator(surface, precedence, leftAssoc)
        {
            @Override
            public Value eval(Value v1, Value v2) //TODO add nonnull check here, and strip null checks from number checks
            {
                assertNotNull(v1, v2);
                return fun.apply(v1, v2);
            }
        });
    }


    public void addUnaryFunction(String name, java.util.function.Function<Value, Value> fun)
    {
        name = name.toLowerCase(Locale.ROOT);
        functions.put(name,  new AbstractFunction(name, 1)
        {
            @Override
            public Value eval(List<Value> parameters)
            {
                return fun.apply(assertNotNull(parameters.get(0)));
            }
        });
    }

    public void addBinaryFunction(String name, java.util.function.BiFunction<Value, Value, Value> fun)
    {
        name = name.toLowerCase(Locale.ROOT);
        functions.put(name, new AbstractFunction(name, 2)
        {
            @Override
            public Value eval(List<Value> parameters)
            {
                Value v1 = parameters.get(0);
                Value v2 = parameters.get(1);
                assertNotNull(v1, v2);
                return fun.apply(v1, v2);
            }
        });
    }

    public void addNAryFunction(String name, int numArgs, java.util.function.Function<List<Value>, Value> fun)
    {
        name = name.toLowerCase(Locale.ROOT);
        functions.put(name, new AbstractFunction(name, numArgs)
        {
            @Override
            public Value eval(List<Value> parameters)
            {
                for (Value v: parameters)
                    assertNotNull(v);
                return fun.apply(parameters);
            }
        });
    }

    public void addFunction(String name, java.util.function.Function<List<Value>, Value> fun)
    {
        addNAryFunction(name, -1, fun);
    }

    public void addMathematicalUnaryFunction(String name, java.util.function.Function<Double, Double> fun)
    {
        addUnaryFunction(name, (v) -> new NumericValue(new BigDecimal(fun.apply(getNumericalValue(v).doubleValue()),mc)));
    }

    public void addMathematicalBinaryFunction(String name, java.util.function.BiFunction<Double, Double, Double> fun)
    {
        addBinaryFunction(name, (w,v) ->
                new NumericValue(new BigDecimal(fun.apply(getNumericalValue(w).doubleValue(), getNumericalValue(v).doubleValue()), mc)));
    }


    public void addLazyFunction(String name, int num_params, java.util.function.Function<List<LazyValue>, LazyValue> fun)//ILazyFunction function)
    {
        name = name.toLowerCase(Locale.ROOT);
        functions.put(name, new AbstractLazyFunction(name, num_params)
        {
            @Override
            public LazyValue lazyEval(List<LazyValue> lazyParams)
            {
                return fun.apply(lazyParams);
            }
        });
    }

    /**
     * Creates a new expression instance from an expression string with a given
     * default match context of {@link MathContext#DECIMAL32}.
     *
     * @param expression The expression. E.g. <code>"2.4*sin(3)/(2-4)"</code> or
     *                   <code>"sin(y)>0 & max(z, 3)>3"</code>
     */
    public Expression(String expression)
    {
        this(expression, MathContext.DECIMAL32);
    }

    /**
     * Creates a new expression instance from an expression string with a given
     * default match context.
     *
     * @param expression         The expression. E.g. <code>"2.4*sin(3)/(2-4)"</code> or
     *                           <code>"sin(y)>0 & max(z, 3)>3"</code>
     * @param defaultMathContext The {@link MathContext} to use by default.
     */
    public Expression(String expression, MathContext defaultMathContext)
    {
        this.mc = defaultMathContext;
        this.expression = expression;
        variables.put("e", () -> e);
        variables.put("PI", () -> PI);
        variables.put("NULL", null);
        variables.put("TRUE", () -> Value.TRUE);
        variables.put("FALSE", () -> Value.FALSE);

        addBinaryOperator(";",OPERATOR_PRECEDENCE_NEXTOP, true, (v1, v2) ->
        {
            if (logOutput != null)
                logOutput.accept(v1.getString());
            return v2;
        });

        addBinaryOperator("+", OPERATOR_PRECEDENCE_ADDITIVE, true, Value::add);

        addBinaryOperator("-", OPERATOR_PRECEDENCE_ADDITIVE, true, Value::subtract);

        addBinaryOperator("*", OPERATOR_PRECEDENCE_MULTIPLICATIVE, true, Value::multiply);

        addBinaryOperator("/", OPERATOR_PRECEDENCE_MULTIPLICATIVE, true, Value::divide);

        addBinaryOperator("%", OPERATOR_PRECEDENCE_MULTIPLICATIVE, true, (v1, v2) ->
                new NumericValue(getNumericalValue(v1).remainder(getNumericalValue(v2), mc)));

        addBinaryOperator("^", OPERATOR_PRECEDENCE_POWER, false, (v1, v2) ->
        {
            BigDecimal d1 = getNumericalValue(v1);
            BigDecimal d2 = getNumericalValue(v2);
            /*-
             * Thanks to Gene Marin:
             * http://stackoverflow.com/questions/3579779/how-to-do-a-fractional-power-on-bigdecimal-in-java
             */
            int signOf2 = d2.signum();
            double dn1 = d1.doubleValue();
            d2 = d2.multiply(new BigDecimal(signOf2)); // n2 is now positive
            BigDecimal remainderOf2 = d2.remainder(BigDecimal.ONE);
            BigDecimal n2IntPart = d2.subtract(remainderOf2);
            BigDecimal intPow = d1.pow(n2IntPart.intValueExact(), mc);
            BigDecimal doublePow = new BigDecimal(Math.pow(dn1, remainderOf2.doubleValue()));
            BigDecimal result = intPow.multiply(doublePow, mc);
            if (signOf2 == -1)
            {
                result = BigDecimal.ONE.divide(result, mc.getPrecision(), RoundingMode.HALF_UP);
            }
            return new NumericValue(result);
        });


        addLazyBinaryOperator("&&", OPERATOR_PRECEDENCE_AND, false, (lv1, lv2) ->
        {
            boolean b1 = assertNotNull(lv1).eval().getBoolean();
            if (!b1) return FALSE;
            boolean b2 = assertNotNull(lv2).eval().getBoolean();
            return b2 ? TRUE : FALSE;
        });

        addLazyBinaryOperator("||", OPERATOR_PRECEDENCE_OR, false, (lv1, lv2) ->
        {
            boolean b1 = assertNotNull(lv1).eval().getBoolean();
            if (b1) return TRUE;
            boolean b2 = assertNotNull(lv2).eval().getBoolean();
            return b2 ? TRUE : FALSE;
        });

        addBinaryOperator(">", OPERATOR_PRECEDENCE_COMPARISON, false, (v1, v2) ->
                v1.compareTo(v2) > 0 ? Value.TRUE : Value.FALSE);

        addBinaryOperator(">=", OPERATOR_PRECEDENCE_COMPARISON, false, (v1, v2) ->
                v1.compareTo(v2) >= 0 ? Value.TRUE : Value.FALSE);

        addBinaryOperator("<", OPERATOR_PRECEDENCE_COMPARISON, false, (v1, v2) ->
                v1.compareTo(v2) < 0 ? Value.TRUE : Value.FALSE);

        addBinaryOperator("<=", OPERATOR_PRECEDENCE_COMPARISON, false, (v1, v2) ->
                v1.compareTo(v2) <= 0 ? Value.TRUE : Value.FALSE);

        addBinaryOperator("==", OPERATOR_PRECEDENCE_EQUALITY, false, (v1, v2) ->
                v1.compareTo(v2) == 0 ? Value.TRUE : Value.FALSE);

        addBinaryOperator("!=", OPERATOR_PRECEDENCE_EQUALITY, false, (v1, v2) ->
                v1.compareTo(v2) == 0 ? Value.TRUE : Value.FALSE);

        addBinaryOperator("=", OPERATOR_PRECEDENCE_ASSIGN, false, (v1, v2) ->
        {
            if (!v1.isBound())
                throw new ExpressionException("LHS of assignment needs to be a variable");
            String varname = v1.getVariable();
            Value boundedLHS = v2.boundTo(varname);
            LazyValue lazyLHS = () -> boundedLHS;
            variables.put(varname, lazyLHS);
            return boundedLHS;
        });

        addBinaryOperator("<>", OPERATOR_PRECEDENCE_EQUALITY, false, (v1, v2) ->
        {
            if (!v1.isBound() || !v2.isBound())
                throw new ExpressionException("Both sides of swapping assignment need to be variables");
            String lvalvar = v1.getVariable();
            String rvalvar = v2.getVariable();
            Value lval = v2.boundTo(lvalvar);
            Value rval = v1.boundTo(rvalvar);
            variables.put(lvalvar, () -> lval);
            variables.put(rvalvar, () -> rval);
            return lval;
        });

        addUnaryOperator("-",  false, (v) -> new NumericValue(getNumericalValue(v).multiply(new BigDecimal(-1))));

        addUnaryOperator("+", false, (v) ->
        {
            getNumericalValue(v);
            return v;
        });

        addUnaryFunction("fact", (v) ->
        {
            BigDecimal d1 = getNumericalValue(v);
            int number = d1.intValue();
            BigDecimal factorial = BigDecimal.ONE;
            for (int i = 1; i <= number; i++)
            {
                factorial = factorial.multiply(new BigDecimal(i));
            }
            return new NumericValue(factorial);
        });

        addUnaryFunction("not", (v) ->
        {
            boolean b = v.getBoolean();
            return b ? Value.FALSE : Value.TRUE;
        });

        addLazyFunction("if", 3, (lv) ->
        {
            Value result = lv.get(0).eval();
            assertNotNull(result);
            return result.getBoolean() ? lv.get(1) : lv.get(2);
        });

        addMathematicalUnaryFunction("rand", (d) -> d*Math.random());
        addMathematicalUnaryFunction("sin",    (d) -> Math.sin(Math.toRadians(d)));
        addMathematicalUnaryFunction("cos",    (d) -> Math.cos(Math.toRadians(d)));
        addMathematicalUnaryFunction("tan",    (d) -> Math.tan(Math.toRadians(d)));
        addMathematicalUnaryFunction("asin",   (d) -> Math.toDegrees(Math.asin(d)));
        addMathematicalUnaryFunction("acos",   (d) -> Math.toDegrees(Math.acos(d)));
        addMathematicalUnaryFunction("atan",   (d) -> Math.toDegrees(Math.atan(d)));
        addMathematicalBinaryFunction("atan2", (d, d2) -> Math.toDegrees(Math.atan2(d, d2)) );
        addMathematicalUnaryFunction("sinh",   Math::sinh );
        addMathematicalUnaryFunction("cosh",   Math::cosh  );
        addMathematicalUnaryFunction("tanh",   Math::tanh );
        addMathematicalUnaryFunction("sec",    (d) ->  1.0 / Math.cos(Math.toRadians(d)) ); // Formula: sec(x) = 1 / cos(x)
        addMathematicalUnaryFunction("csc",    (d) ->  1.0 / Math.sin(Math.toRadians(d)) ); // Formula: csc(x) = 1 / sin(x)
        addMathematicalUnaryFunction("sech",   (d) ->  1.0 / Math.cosh(d) );                // Formula: sech(x) = 1 / cosh(x)
        addMathematicalUnaryFunction("csch",   (d) -> 1.0 / Math.sinh(d)  );                // Formula: csch(x) = 1 / sinh(x)
        addMathematicalUnaryFunction("cot",    (d) -> 1.0 / Math.tan(Math.toRadians(d))  ); // Formula: cot(x) = cos(x) / sin(x) = 1 / tan(x)
        addMathematicalUnaryFunction("acot",   (d) ->  Math.toDegrees(Math.atan(1.0 / d)) );// Formula: acot(x) = atan(1/x)
        addMathematicalUnaryFunction("coth",   (d) ->  1.0 / Math.tanh(d) );                // Formula: coth(x) = 1 / tanh(x)
        addMathematicalUnaryFunction("asinh",  (d) ->  Math.log(d + (Math.sqrt(Math.pow(d, 2) + 1))));  // Formula: asinh(x) = ln(x + sqrt(x^2 + 1))
        addMathematicalUnaryFunction("acosh",  (d) ->  Math.log(d + (Math.sqrt(Math.pow(d, 2) - 1))));  // Formula: acosh(x) = ln(x + sqrt(x^2 - 1))
        addMathematicalUnaryFunction("atanh",  (d) ->                                       // Formula: atanh(x) = 0.5*ln((1 + x)/(1 - x))
        {
            if (Math.abs(d) > 1 || Math.abs(d) == 1)
                throw new ExpressionException("Number must be |x| < 1");
            return 0.5 * Math.log((1 + d) / (1 - d));
        });
        addMathematicalUnaryFunction("rad",  Math::toRadians);
        addMathematicalUnaryFunction("deg", Math::toDegrees);
        addMathematicalUnaryFunction("log", Math::log);
        addMathematicalUnaryFunction("log10", Math::log10);
        addMathematicalUnaryFunction("log1p", Math::log1p);
        addMathematicalUnaryFunction("sqrt", Math::sqrt);


        addFunction("min", (lv) ->
        {
            if (lv.size() == 0)
                throw new ExpressionException("MAX requires at least one parameter");
            Value max = null;
            for (Value parameter : lv)
            {
                if (max == null || parameter.compareTo(max) > 0) max = parameter;
            }
            return max;
        });

        addFunction("min", (lv) ->
        {
            if (lv.size() == 0)
                throw new ExpressionException("MIN requires at least one parameter");
            Value min = null;
            for (Value parameter : lv)
            {
                if (min == null || parameter.compareTo(min) < 0) min = parameter;
            }
            return min;
        });

        addUnaryFunction("abs", (v) -> new NumericValue(getNumericalValue(v).abs(mc)));

        addBinaryFunction("round", (v1, v2) ->
        {
            BigDecimal toRound = getNumericalValue(v1);
            int precision = getNumericalValue(v2).intValue();
            return new NumericValue(toRound.setScale(precision, mc.getRoundingMode()));
        });

        addUnaryFunction("floor", (v) -> new NumericValue(getNumericalValue(v).setScale(0, RoundingMode.FLOOR)));

        addUnaryFunction("ceil", (v) -> new NumericValue(getNumericalValue(v).setScale(0, RoundingMode.CEILING)));

        addUnaryFunction("relu", (v) -> v.compareTo(Value.ZERO) < 0 ? Value.ZERO : v);

        addUnaryFunction("print", (v) ->
        {
                System.out.println(v.getString());
                return v; // pass through for variables
        });

        addFunction("list", ListValue::new);

        // loop(expr, limit) => last_value
        // expr receives bounded variable '_' indicating iteration

        addLazyFunction("loop", 2, (lv) ->
        {
            long limit = getNumericalValue(lv.get(1).eval()).longValue();
            Value lastOne = Value.ZERO;
            LazyValue expr = lv.get(0);
            for (long i=0; i < limit; i++)
            {
                long dummy = i;
                variables.put("_", () -> (new NumericValue(dummy)).boundTo("_"));
                lastOne = expr.eval();
            }
            Value trulyLastOne = lastOne;
            variables.put("_", () -> Value.ZERO.boundTo("_"));
            return () -> trulyLastOne;
        });

        // map(expr, list) => list
        // receives bounded variable '_' with the expression
        addLazyFunction("map", 2, (lv) ->
        {
            LazyValue expr = lv.get(0);
            Value rval= lv.get(1).eval();
            if (!(rval instanceof ListValue))
                throw new ExpressionException("Second argument of map function should be a list");
            ListValue list = (ListValue) rval;
            LazyValue ret = () -> new ListValue(list.getItems().stream().map((v)->{
                variables.put("_", () -> v.boundTo("_"));
                return expr.eval();
            }).collect(Collectors.toList()));
            variables.put("_", () -> Value.ZERO.boundTo("_"));
            return ret;
        });

        // similar to map, but returns total number of successes
        addLazyFunction("for", 2, (lv) -> // for(expr, list) => success_count
        {
            LazyValue expr = lv.get(0);
            Value rval= lv.get(1).eval();
            if (!(rval instanceof ListValue))
                throw new ExpressionException("Second argument of for function should be a list");
            ListValue list = (ListValue) rval;
            long successCount = 0;
            for (Value v : list.getItems())
            {
                variables.put("_", () -> v.boundTo("_"));
                Value res = expr.eval();
                if (res.getBoolean())
                    successCount++;
            }
            long promiseWontChange = successCount;
            variables.put("_", () -> Value.ZERO.boundTo("_"));
            return () -> new NumericValue(promiseWontChange);
        });

        //condition and expression will get a bound 'i'
        //returns last successful expression or false
        addLazyFunction("while", 3, (lv) -> // while(cond, limit, expr) => ??
        {
            long limit = getNumericalValue(lv.get(1).eval()).longValue();
            LazyValue condition = lv.get(0);
            LazyValue expr = lv.get(2);
            long i = 0;
            Value lastOne = Value.ZERO;
            variables.put("_",() -> new NumericValue(0).boundTo("_"));
            while (i<limit && condition.eval().getBoolean() )
            {
                lastOne = expr.eval();
                i++;
                long notGonnaChangeIPromize = i;
                variables.put("_",() -> new NumericValue(notGonnaChangeIPromize).boundTo("_"));
            }
            Value lastValueNoKidding = lastOne;
            return () -> lastValueNoKidding;
        });

        // reduce(expr, list, acc) => value
        // reduces values in the list with expression that gets accumulator
        // each iteration expr receives acc - accumulator, and '_' - current list value
        // returned value is substituted to the accumulator
        addLazyFunction("reduce", 3, (lv) ->
        {
            LazyValue expr = lv.get(0);
            Value acc = lv.get(2).eval();
            Value rval= lv.get(1).eval();
            if (!(rval instanceof ListValue))
                throw new ExpressionException("Second argument of for function should be a list");
            List<Value> elements= ((ListValue) rval).getItems();
            if (elements.isEmpty())
            {
                Value seriouslyWontChange = acc;
                return () -> seriouslyWontChange;
            }
            for (Value v: elements)
            {
                Value kidYouNotWontChange = acc;
                variables.put("acc", () -> kidYouNotWontChange.boundTo("acc"));
                variables.put("_", () -> v.boundTo("_"));
                acc = expr.eval();

            }
            Value hopeItsEnoughPromise = acc;
            variables.put("acc", () -> Value.ZERO.boundTo("acc"));
            variables.put("_", () -> Value.ZERO.boundTo("_"));
            return () -> hopeItsEnoughPromise;
        });

        // case(cond1, expr1, cond2, expr2, ...) => value
        addLazyFunction("case", -1, (lv) ->
        {
            if ((lv.size() % 2 == 0) || lv.size() < 3 )
                throw new ExpressionException("case statement needs to have at least one condition and case, and a default value");
            Value returnValue = Value.ZERO;
            for (int i=0; i<lv.size()-1; i+=2)
            {
                if (lv.get(i).eval().getBoolean())
                {
                    Value ret = lv.get(i+1).eval();
                    return () -> ret;
                }
            }
            return () -> lv.get(lv.size() - 1).eval();
        });

    }



    /**
     * Is the string a number?
     *
     * @param st The string.
     * @return <code>true</code>, if the input string is a number.
     */
    private boolean isNumber(String st)
    {
        if (st.charAt(0) == minusSign && st.length() == 1)
            return false;
        if (st.charAt(0) == '+' && st.length() == 1)
            return false;
        if (st.charAt(0) == decimalSeparator && (st.length() == 1 || !Character.isDigit(st.charAt(1))))
            return false;
        if (st.charAt(0) == 'e' || st.charAt(0) == 'E')
            return false;
        for (char ch : st.toCharArray())
        {
            if (!Character.isDigit(ch) && ch != minusSign && ch != decimalSeparator && ch != 'e' && ch != 'E'
                    && ch != '+')
                return false;
        }
        return true;
    }

    /**
     * Implementation of the <i>Shunting Yard</i> algorithm to transform an
     * infix expression to a RPN expression.
     *
     * @param expression The input expression in infx.
     * @return A RPN representation of the expression, with each token as a list
     * member.
     */
    private List<Token> shuntingYard(String expression)
    {
        List<Token> outputQueue = new ArrayList<>();
        Stack<Token> stack = new Stack<>();

        Tokenizer tokenizer = new Tokenizer(expression);

        Token lastFunction = null;
        Token previousToken = null;
        while (tokenizer.hasNext())
        {
            Token token = tokenizer.next();
            switch (token.type)
            {
                case STRINGPARAM:
                    //stack.push(token);
                    //break;
                case LITERAL:
                case HEX_LITERAL:
                    if (previousToken != null && (
                            previousToken.type == TokenType.LITERAL ||
                                    previousToken.type == TokenType.HEX_LITERAL ||
                                    previousToken.type == TokenType.STRINGPARAM))
                    {
                        throw new ExpressionException("Missing operator at character position " + token.pos);
                    }
                    outputQueue.add(token);
                    break;
                case VARIABLE:
                    outputQueue.add(token);
                    break;
                case FUNCTION:
                    stack.push(token);
                    lastFunction = token;
                    break;
                case COMMA:
                    if (previousToken != null && previousToken.type == TokenType.OPERATOR)
                    {
                        throw new ExpressionException("Missing parameter(s) for operator " + previousToken
                                + " at character position " + previousToken.pos);
                    }
                    while (!stack.isEmpty() && stack.peek().type != TokenType.OPEN_PAREN)
                    {
                        outputQueue.add(stack.pop());
                    }
                    if (stack.isEmpty())
                    {
                        if (lastFunction == null)
                        {
                            throw new ExpressionException("Unexpected comma at character position " + token.pos);
                        }
                        else
                        {
                            throw new ExpressionException(
                                    "Parse error for function '" + lastFunction + "' at character position " + token.pos);
                        }
                    }
                    break;
                case OPERATOR:
                {
                    if (previousToken != null
                            && (previousToken.type == TokenType.COMMA || previousToken.type == TokenType.OPEN_PAREN))
                    {
                        throw new ExpressionException(
                                "Missing parameter(s) for operator " + token + " at character position " + token.pos);
                    }
                    ILazyOperator o1 = operators.get(token.surface);
                    if (o1 == null)
                    {
                        throw new ExpressionException("Unknown operator '" + token + "' at position " + (token.pos + 1));
                    }

                    shuntOperators(outputQueue, stack, o1);
                    stack.push(token);
                    break;
                }
                case UNARY_OPERATOR:
                {
                    if (previousToken != null && previousToken.type != TokenType.OPERATOR
                            && previousToken.type != TokenType.COMMA && previousToken.type != TokenType.OPEN_PAREN)
                    {
                        throw new ExpressionException(
                                "Invalid position for unary operator " + token + " at character position " + token.pos);
                    }
                    ILazyOperator o1 = operators.get(token.surface);
                    if (o1 == null)
                    {
                        throw new ExpressionException(
                                "Unknown unary operator '" + token.surface.substring(0, token.surface.length() - 1)
                                        + "' at position " + (token.pos + 1));
                    }

                    shuntOperators(outputQueue, stack, o1);
                    stack.push(token);
                    break;
                }
                case OPEN_PAREN:
                    if (previousToken != null)
                    {
                        if (previousToken.type == TokenType.LITERAL || previousToken.type == TokenType.CLOSE_PAREN
                                || previousToken.type == TokenType.VARIABLE
                                || previousToken.type == TokenType.HEX_LITERAL)
                        {
                            // Implicit multiplication, e.g. 23(a+b) or (a+b)(a-b)
                            Token multiplication = new Token();
                            multiplication.append("*");
                            multiplication.type = TokenType.OPERATOR;
                            stack.push(multiplication);
                        }
                        // if the ( is preceded by a valid function, then it
                        // denotes the start of a parameter list
                        if (previousToken.type == TokenType.FUNCTION)
                        {
                            outputQueue.add(token);
                        }
                    }
                    stack.push(token);
                    break;
                case CLOSE_PAREN:
                    if (previousToken != null && previousToken.type == TokenType.OPERATOR)
                    {
                        throw new ExpressionException("Missing parameter(s) for operator " + previousToken
                                + " at character position " + previousToken.pos);
                    }
                    while (!stack.isEmpty() && stack.peek().type != TokenType.OPEN_PAREN)
                    {
                        outputQueue.add(stack.pop());
                    }
                    if (stack.isEmpty())
                    {
                        throw new ExpressionException("Mismatched parentheses");
                    }
                    stack.pop();
                    if (!stack.isEmpty() && stack.peek().type == TokenType.FUNCTION)
                    {
                        outputQueue.add(stack.pop());
                    }
            }
            previousToken = token;
        }

        while (!stack.isEmpty())
        {
            Token element = stack.pop();
            if (element.type == TokenType.OPEN_PAREN || element.type == TokenType.CLOSE_PAREN)
            {
                throw new ExpressionException("Mismatched parentheses");
            }
            outputQueue.add(element);
        }
        return outputQueue;
    }

    private void shuntOperators(List<Token> outputQueue, Stack<Token> stack, ILazyOperator o1)
    {
        Expression.Token nextToken = stack.isEmpty() ? null : stack.peek();
        while (nextToken != null
                && (nextToken.type == Expression.TokenType.OPERATOR
                || nextToken.type == Expression.TokenType.UNARY_OPERATOR)
                && ((o1.isLeftAssoc() && o1.getPrecedence() <= operators.get(nextToken.surface).getPrecedence())
                || (o1.getPrecedence() < operators.get(nextToken.surface).getPrecedence())))
        {
            outputQueue.add(stack.pop());
            nextToken = stack.isEmpty() ? null : stack.peek();
        }
    }


    private LazyValue ast = null;

    /**
     * Evaluates the expression.
     *
     * @return The result of the expression.
     */
    public Value eval()
    {
        if (ast != null) return ast.eval();

        Stack<LazyValue> stack = new Stack<>();
        for (final Token token : getRPN())
        {
            switch (token.type)
            {
                case UNARY_OPERATOR:
                {
                    final LazyValue value = stack.pop();
                    LazyValue result = new LazyValue()
                    {
                        public Value eval()
                        {
                            return operators.get(token.surface).eval(value, null).eval();
                        }

                    };
                    stack.push(result);
                    break;
                }
                case OPERATOR:
                    final LazyValue v1 = stack.pop();
                    final LazyValue v2 = stack.pop();
                    LazyValue result = new LazyValue()
                    {
                        public Value eval()
                        {
                            return operators.get(token.surface).eval(v2, v1).eval();
                        }

                    };
                    stack.push(result);
                    break;
                case VARIABLE:
                    if (functions.containsKey(token.surface.toUpperCase(Locale.ROOT)))
                    {
                        throw new ExpressionException("Variable would mask function: " + token);
                    }

                    stack.push(new LazyValue()
                    {
                        public Value eval()
                        {
                            if (!variables.containsKey(token.surface)) // new variable
                            {
                                variables.put(token.surface, () -> Value.ZERO.boundTo(token.surface));
                            }
                            LazyValue lazyVariable = variables.get(token.surface);
                            Value value = lazyVariable.eval();
                            return value;
                        }

                    });
                    break;
                case FUNCTION:
                    ILazyFunction f = functions.get(token.surface.toLowerCase(Locale.ROOT));
                    ArrayList<LazyValue> p = new ArrayList<>(!f.numParamsVaries() ? f.getNumParams() : 0);
                    // pop parameters off the stack until we hit the start of
                    // this function's parameter list
                    while (!stack.isEmpty() && stack.peek() != PARAMS_START)
                    {
                        p.add(0, stack.pop());
                    }

                    if (stack.peek() == PARAMS_START)
                    {
                        stack.pop();
                    }

                    stack.push(new LazyValue()
                    {
                        public Value eval()
                        {
                            return f.lazyEval(p).eval();
                        }

                    });
                    break;
                case OPEN_PAREN:
                    stack.push(PARAMS_START);
                    break;
                case LITERAL:
                    stack.push(new LazyValue()
                    {
                        public Value eval()
                        {
                            if (token.surface.equalsIgnoreCase("NULL"))
                            {
                                return null;
                            }
                            return new NumericValue(new BigDecimal(token.surface, mc));
                        }

                    });
                    break;
                case STRINGPARAM:
                    stack.push(new LazyValue()
                    {
                        public Value eval()
                        {
                            return new StringValue(token.surface); // was null
                        }

                    });
                    break;
                case HEX_LITERAL:
                    stack.push(new LazyValue()
                    {
                        public Value eval()
                        {
                            return new NumericValue(new BigDecimal(new BigInteger(token.surface.substring(2), 16), mc));
                        }

                    });
                    break;
                default:
                    throw new ExpressionException(
                            "Unexpected token '" + token.surface + "' at character position " + token.pos);
            }
        }
        ast = stack.pop();
        return ast.eval();
        //return stack.pop().eval();
    }



    /**
     * Sets a variable value.
     *
     * @param variable The variable name.
     * @param value    The variable value.
     * @return The expression, allows to chain methods.
     */
    public Expression setVariable(String variable, Value value)
    {
        return setVariable(variable, () -> value.boundTo(variable));
    }

    /**
     * Sets a variable value.
     *
     * @param variable The variable name.
     * @param value    The variable value.
     * @return The expression, allows to chain methods.
     */
    public Expression setVariable(String variable, LazyValue value)
    {
        variables.put(variable, value);
        return this;
    }

    /**
     * Sets a variable value.
     *
     * @param variable The variable to set.
     * @param value    The variable value.
     * @return The expression, allows to chain methods.
     */
    public Expression setVariable(String variable, String value)
    {
        if (isNumber(value))
            variables.put(variable, () -> new NumericValue(new BigDecimal(value, mc)).boundTo(variable));
        else if (value.equalsIgnoreCase("null"))
        {
            variables.put(variable, null);
        }
        else
        {
            variables.put(variable, () -> new StringValue(value).boundTo(variable));
        }
        return this;
    }

    /**
     * Sets a variable value.
     *
     * @param variable The variable to set.
     * @param value    The variable value.
     * @return The expression, allows to chain methods.
     */
    public Expression with(String variable, BigDecimal value)
    {
        return setVariable(variable, new NumericValue(value)); // variable will be set by setVariable
    }

    /**
     * Sets a variable value.
     *
     * @param variable The variable to set.
     * @param value    The variable value.
     * @return The expression, allows to chain methods.
     */
    public Expression with(String variable, String value)
    {
        return setVariable(variable, value);
    }

    /**
     * Cached access to the RPN notation of this expression, ensures only one
     * calculation of the RPN per expression instance. If no cached instance
     * exists, a new one will be created and put to the cache.
     *
     * @return The cached RPN instance.
     */
    private List<Token> getRPN()
    {
        if (rpn == null)
        {
            rpn = shuntingYard(this.expression);
            validate(rpn);
        }
        return rpn;
    }

    /**
     * Check that the expression has enough numbers and variables to fit the
     * requirements of the operators and functions, also check for only 1 result
     * stored at the end of the evaluation.
     */
    private void validate(List<Token> rpn)
    {
        /*-
         * Thanks to Norman Ramsey:
         * http://http://stackoverflow.com/questions/789847/postfix-notation-validation
         */
        // each push on to this stack is a new function scope, with the value of
        // each
        // layer on the stack being the count of the number of parameters in
        // that scope
        Stack<Integer> stack = new Stack<Integer>();

        // push the 'global' scope
        stack.push(0);

        for (final Token token : rpn)
        {
            switch (token.type)
            {
                case UNARY_OPERATOR:
                    if (stack.peek() < 1)
                    {
                        throw new ExpressionException("Missing parameter(s) for operator " + token);
                    }
                    break;
                case OPERATOR:
                    if (stack.peek() < 2)
                    {
                        if (token.surface.equalsIgnoreCase(";"))
                        {
                            throw new ExpressionException("Unnecessary semicolon at position " + (token.pos + 1));
                        }
                        throw new ExpressionException("Missing parameter(s) for operator " + token);
                    }
                    // pop the operator's 2 parameters and add the result
                    stack.set(stack.size() - 1, stack.peek() - 2 + 1);
                    break;
                case FUNCTION:
                    ILazyFunction f = functions.get(token.surface.toLowerCase(Locale.ROOT));
                    if (f == null)
                    {
                        throw new ExpressionException("Unknown function '" + token + "' at position " + (token.pos + 1));
                    }

                    int numParams = stack.pop();
                    if (!f.numParamsVaries() && numParams != f.getNumParams())
                    {
                        throw new ExpressionException(
                                "IFunction " + token + " expected " + f.getNumParams() + " parameters, got " + numParams);
                    }
                    if (stack.size() <= 0)
                    {
                        throw new ExpressionException("Too many function calls, maximum scope exceeded");
                    }
                    // push the result of the function
                    stack.set(stack.size() - 1, stack.peek() + 1);
                    break;
                case OPEN_PAREN:
                    stack.push(0);
                    break;
                default:
                    stack.set(stack.size() - 1, stack.peek() + 1);
            }
        }

        if (stack.size() > 1)
        {
            throw new ExpressionException("Too many unhandled function parameter lists");
        }
        else if (stack.peek() > 1)
        {
            throw new ExpressionException("Too many numbers or variables");
        }
        else if (stack.peek() < 1)
        {
            throw new ExpressionException("Empty expression");
        }
    }

    public void setLogOutput(Consumer<String> to)
    {
        logOutput = to;
    }
}
