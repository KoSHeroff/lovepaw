package dev.lovepaw.model.anim.molang;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MolangParserTest {
    private static final float EPSILON = 1.0E-5f;

    private static float eval(String expression, MolangContext context) {
        return MolangParser.compile(expression).get(context);
    }

    @Test
    void readsPlainNumbers() {
        assertEquals(2.5f, eval("2.5", new MolangContext()), EPSILON);
        assertEquals(-3f, eval("-3", new MolangContext()), EPSILON);
    }

    @Test
    void appliesOperatorPrecedence() {
        MolangContext context = new MolangContext();
        assertEquals(7f, eval("1 + 2 * 3", context), EPSILON);
        assertEquals(9f, eval("(1 + 2) * 3", context), EPSILON);
        assertEquals(1f, eval("10 / 10", context), EPSILON);
    }

    @Test
    void trigonometryWorksInDegrees() {
        MolangContext context = new MolangContext();
        assertEquals(1f, eval("math.sin(90)", context), EPSILON);
        assertEquals(0f, eval("math.cos(90)", context), EPSILON);
        assertEquals(90f, eval("math.asin(1)", context), EPSILON);
    }

    @Test
    void readsQueriesAndVariables() {
        MolangContext context = new MolangContext();
        context.setQuery("anim_time", 3f);
        context.setVariable("speed", 2f);

        assertEquals(6f, eval("query.anim_time * 2", context), EPSILON);
        assertEquals(6f, eval("q.anim_time * 2", context), EPSILON);
        assertEquals(2f, eval("variable.speed", context), EPSILON);
        assertEquals(2f, eval("v.speed", context), EPSILON);
    }

    @Test
    void unknownNamesAreZeroRatherThanAnError() {
        MolangContext context = new MolangContext();
        assertEquals(0f, eval("query.something_we_do_not_have", context), EPSILON);
        assertEquals(0f, eval("math.no_such_function(4)", context), EPSILON);
    }

    @Test
    void handlesTernaries() {
        MolangContext context = new MolangContext();
        assertEquals(20f, eval("1 > 2 ? 10 : 20", context), EPSILON);
        assertEquals(10f, eval("2 >= 2 ? 10 : 20", context), EPSILON);
    }

    @Test
    void runsStatementListsWithAssignments() {
        MolangContext context = new MolangContext();
        assertEquals(10f, eval("v.x = 5; v.x * 2", context), EPSILON);
        assertEquals(5f, context.variable("x"), EPSILON);
        assertEquals(7f, eval("v.y = 7; return v.y; 99", context), EPSILON);
    }

    @Test
    void divisionByZeroYieldsZeroInsteadOfInfinity() {
        assertEquals(0f, eval("5 / 0", new MolangContext()), EPSILON);
    }

    @Test
    void clampAndLerpBehaveLikeMolang() {
        MolangContext context = new MolangContext();
        assertEquals(5f, eval("math.clamp(9, 1, 5)", context), EPSILON);
        assertEquals(1f, eval("math.clamp(-2, 1, 5)", context), EPSILON);
        assertEquals(2.5f, eval("math.lerp(0, 5, 0.5)", context), EPSILON);
    }
}
