package dev.lovepaw.model.anim.molang;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A small Molang implementation, enough for what Blockbench writes into
 * animation keyframes: arithmetic, comparisons, the ternary, {@code math.*}
 * functions, {@code query.*} / {@code variable.*} lookups and statement lists
 * with assignments.
 *
 * <p>Angles follow Molang, not Java: {@code math.sin} takes degrees and
 * {@code math.asin} returns degrees.
 *
 * <p>Expressions are compiled once at load time into a {@link MolangValue} tree,
 * so animating a pet never re-parses a string.
 */
public final class MolangParser {
    private final String source;
    private int position;

    private MolangParser(String source) {
        this.source = source;
        this.position = 0;
    }

    /** Compiles an expression, falling back to a constant 0 if it cannot be read. */
    public static MolangValue compile(String expression) {
        if (expression == null || expression.isBlank()) {
            return MolangValue.ZERO;
        }
        try {
            float constant = Float.parseFloat(expression.trim());
            return MolangValue.constant(constant);
        } catch (NumberFormatException ignored) {
        }
        try {
            MolangParser parser = new MolangParser(expression);
            MolangValue value = parser.parseProgram();
            parser.skipWhitespace();
            if (parser.position < parser.source.length()) {
                throw new MolangException("unexpected trailing input at " + parser.position);
            }
            return value;
        } catch (RuntimeException e) {
            throw new MolangException("cannot parse Molang expression: " + expression, e);
        }
    }

    private MolangValue parseProgram() {
        List<Statement> statements = new ArrayList<>();
        while (true) {
            skipWhitespace();
            if (position >= source.length()) {
                break;
            }
            statements.add(parseStatement());
            skipWhitespace();
            if (position < source.length() && source.charAt(position) == ';') {
                position++;
            } else {
                break;
            }
        }

        if (statements.isEmpty()) {
            return MolangValue.ZERO;
        }
        if (statements.size() == 1 && statements.get(0).assignTo() == null) {
            return statements.get(0).value();
        }

        List<Statement> compiled = List.copyOf(statements);
        return context -> {
            float last = 0f;
            for (Statement statement : compiled) {
                float value = statement.value().get(context);
                if (statement.assignTo() != null) {
                    context.setVariable(statement.assignTo(), value);
                } else {
                    last = value;
                }
                if (statement.isReturn()) {
                    return value;
                }
            }
            return last;
        };
    }

    private Statement parseStatement() {
        skipWhitespace();
        if (matchWord("return")) {
            return new Statement(null, parseExpression(), true);
        }

        int start = position;
        String name = tryReadIdentifierPath();
        if (name != null) {
            skipWhitespace();
            if (position < source.length() && source.charAt(position) == '='
                    && (position + 1 >= source.length() || source.charAt(position + 1) != '=')) {
                position++;
                String variable = variableName(name);
                if (variable == null) {
                    throw new MolangException("only variable.* and temp.* can be assigned, got " + name);
                }
                return new Statement(variable, parseExpression(), false);
            }
        }
        position = start;
        return new Statement(null, parseExpression(), false);
    }

    private static String variableName(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.startsWith("variable.")) {
            return lower.substring("variable.".length());
        }
        if (lower.startsWith("v.")) {
            return lower.substring("v.".length());
        }
        if (lower.startsWith("temp.")) {
            return lower.substring("temp.".length());
        }
        if (lower.startsWith("t.")) {
            return lower.substring("t.".length());
        }
        return null;
    }

    private MolangValue parseExpression() {
        return parseTernary();
    }

    private MolangValue parseTernary() {
        MolangValue condition = parseCoalesce();
        skipWhitespace();
        if (position < source.length() && source.charAt(position) == '?') {
            position++;
            MolangValue ifTrue = parseExpression();
            skipWhitespace();
            if (position < source.length() && source.charAt(position) == ':') {
                position++;
                MolangValue ifFalse = parseExpression();
                return context -> condition.get(context) != 0 ? ifTrue.get(context) : ifFalse.get(context);
            }
            return context -> condition.get(context) != 0 ? ifTrue.get(context) : 0f;
        }
        return condition;
    }

    private MolangValue parseCoalesce() {
        MolangValue left = parseOr();
        while (true) {
            skipWhitespace();
            if (match("??")) {
                MolangValue right = parseOr();
                MolangValue current = left;
                left = context -> {
                    float value = current.get(context);
                    return Float.isNaN(value) ? right.get(context) : value;
                };
            } else {
                return left;
            }
        }
    }

    private MolangValue parseOr() {
        MolangValue left = parseAnd();
        while (true) {
            skipWhitespace();
            if (match("||")) {
                MolangValue right = parseAnd();
                MolangValue current = left;
                left = context -> (current.get(context) != 0 || right.get(context) != 0) ? 1f : 0f;
            } else {
                return left;
            }
        }
    }

    private MolangValue parseAnd() {
        MolangValue left = parseEquality();
        while (true) {
            skipWhitespace();
            if (match("&&")) {
                MolangValue right = parseEquality();
                MolangValue current = left;
                left = context -> (current.get(context) != 0 && right.get(context) != 0) ? 1f : 0f;
            } else {
                return left;
            }
        }
    }

    private MolangValue parseEquality() {
        MolangValue left = parseComparison();
        while (true) {
            skipWhitespace();
            if (match("==")) {
                MolangValue right = parseComparison();
                MolangValue current = left;
                left = context -> current.get(context) == right.get(context) ? 1f : 0f;
            } else if (match("!=")) {
                MolangValue right = parseComparison();
                MolangValue current = left;
                left = context -> current.get(context) != right.get(context) ? 1f : 0f;
            } else {
                return left;
            }
        }
    }

    private MolangValue parseComparison() {
        MolangValue left = parseAdditive();
        while (true) {
            skipWhitespace();
            if (match("<=")) {
                MolangValue right = parseAdditive();
                MolangValue current = left;
                left = context -> current.get(context) <= right.get(context) ? 1f : 0f;
            } else if (match(">=")) {
                MolangValue right = parseAdditive();
                MolangValue current = left;
                left = context -> current.get(context) >= right.get(context) ? 1f : 0f;
            } else if (match("<")) {
                MolangValue right = parseAdditive();
                MolangValue current = left;
                left = context -> current.get(context) < right.get(context) ? 1f : 0f;
            } else if (match(">")) {
                MolangValue right = parseAdditive();
                MolangValue current = left;
                left = context -> current.get(context) > right.get(context) ? 1f : 0f;
            } else {
                return left;
            }
        }
    }

    private MolangValue parseAdditive() {
        MolangValue left = parseMultiplicative();
        while (true) {
            skipWhitespace();
            if (position < source.length() && source.charAt(position) == '+') {
                position++;
                MolangValue right = parseMultiplicative();
                MolangValue current = left;
                left = context -> current.get(context) + right.get(context);
            } else if (position < source.length() && source.charAt(position) == '-'
                    && !isPartOfArrow()) {
                position++;
                MolangValue right = parseMultiplicative();
                MolangValue current = left;
                left = context -> current.get(context) - right.get(context);
            } else {
                return left;
            }
        }
    }

    private boolean isPartOfArrow() {
        return position + 1 < source.length() && source.charAt(position + 1) == '>';
    }

    private MolangValue parseMultiplicative() {
        MolangValue left = parseUnary();
        while (true) {
            skipWhitespace();
            if (position < source.length() && source.charAt(position) == '*') {
                position++;
                MolangValue right = parseUnary();
                MolangValue current = left;
                left = context -> current.get(context) * right.get(context);
            } else if (position < source.length() && source.charAt(position) == '/') {
                position++;
                MolangValue right = parseUnary();
                MolangValue current = left;
                left = context -> {
                    float divisor = right.get(context);
                    return divisor == 0 ? 0f : current.get(context) / divisor;
                };
            } else {
                return left;
            }
        }
    }

    private MolangValue parseUnary() {
        skipWhitespace();
        if (position < source.length() && source.charAt(position) == '-') {
            position++;
            MolangValue value = parseUnary();
            return context -> -value.get(context);
        }
        if (position < source.length() && source.charAt(position) == '!') {
            position++;
            MolangValue value = parseUnary();
            return context -> value.get(context) == 0 ? 1f : 0f;
        }
        return parsePrimary();
    }

    private MolangValue parsePrimary() {
        skipWhitespace();
        if (position >= source.length()) {
            throw new MolangException("unexpected end of expression");
        }

        char c = source.charAt(position);
        if (c == '(') {
            position++;
            MolangValue value = parseExpression();
            expect(')');
            return value;
        }
        if (Character.isDigit(c) || c == '.') {
            return MolangValue.constant(readNumber());
        }

        String path = tryReadIdentifierPath();
        if (path == null) {
            throw new MolangException("unexpected character '" + c + "' at " + position);
        }

        skipWhitespace();
        if (position < source.length() && source.charAt(position) == '(') {
            position++;
            List<MolangValue> arguments = new ArrayList<>();
            skipWhitespace();
            if (position < source.length() && source.charAt(position) == ')') {
                position++;
            } else {
                while (true) {
                    arguments.add(parseExpression());
                    skipWhitespace();
                    if (position < source.length() && source.charAt(position) == ',') {
                        position++;
                        continue;
                    }
                    expect(')');
                    break;
                }
            }
            return MolangFunctions.create(path, List.copyOf(arguments));
        }

        return lookup(path);
    }

    private static MolangValue lookup(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        String variable = variableName(lower);
        if (variable != null) {
            return context -> context.variable(variable);
        }
        if (lower.startsWith("query.") || lower.startsWith("q.")) {
            String name = lower.substring(lower.indexOf('.') + 1);
            return context -> context.query(name);
        }
        if (lower.equals("math.pi")) {
            return MolangValue.constant((float) Math.PI);
        }
        if (lower.equals("true")) {
            return MolangValue.constant(1f);
        }
        if (lower.equals("false")) {
            return MolangValue.ZERO;
        }
        return MolangValue.ZERO;
    }

    private float readNumber() {
        int start = position;
        while (position < source.length()
                && (Character.isDigit(source.charAt(position)) || source.charAt(position) == '.')) {
            position++;
        }
        return Float.parseFloat(source.substring(start, position));
    }

    private String tryReadIdentifierPath() {
        skipWhitespace();
        int start = position;
        while (position < source.length()) {
            char c = source.charAt(position);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '.') {
                position++;
            } else {
                break;
            }
        }
        if (start == position) {
            return null;
        }
        String path = source.substring(start, position);
        if (Character.isDigit(path.charAt(0))) {
            position = start;
            return null;
        }
        return path;
    }

    private boolean match(String token) {
        skipWhitespace();
        if (source.startsWith(token, position)) {
            position += token.length();
            return true;
        }
        return false;
    }

    private boolean matchWord(String word) {
        skipWhitespace();
        if (!source.startsWith(word, position)) {
            return false;
        }
        int end = position + word.length();
        if (end < source.length() && (Character.isLetterOrDigit(source.charAt(end)) || source.charAt(end) == '_')) {
            return false;
        }
        position = end;
        return true;
    }

    private void expect(char c) {
        skipWhitespace();
        if (position >= source.length() || source.charAt(position) != c) {
            throw new MolangException("expected '" + c + "' at " + position);
        }
        position++;
    }

    private void skipWhitespace() {
        while (position < source.length() && Character.isWhitespace(source.charAt(position))) {
            position++;
        }
    }

    private record Statement(String assignTo, MolangValue value, boolean isReturn) {
    }
}
