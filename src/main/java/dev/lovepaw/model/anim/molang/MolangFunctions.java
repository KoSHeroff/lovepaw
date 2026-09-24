package dev.lovepaw.model.anim.molang;

import java.util.List;
import java.util.Locale;

final class MolangFunctions {
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);
    private static final float RAD_TO_DEG = (float) (180.0 / Math.PI);

    private MolangFunctions() {
    }

    static MolangValue create(String path, List<MolangValue> args) {
        String name = path.toLowerCase(Locale.ROOT);
        if (name.startsWith("math.")) {
            name = name.substring("math.".length());
        }

        return switch (name) {
            case "abs" -> unary(args, Math::abs);
            case "acos" -> unary(args, value -> (float) Math.acos(value) * RAD_TO_DEG);
            case "asin" -> unary(args, value -> (float) Math.asin(value) * RAD_TO_DEG);
            case "atan" -> unary(args, value -> (float) Math.atan(value) * RAD_TO_DEG);
            case "atan2" -> binary(args, (y, x) -> (float) Math.atan2(y, x) * RAD_TO_DEG);
            case "ceil" -> unary(args, value -> (float) Math.ceil(value));
            case "clamp" -> ternary(args, (value, min, max) -> Math.max(min, Math.min(max, value)));
            case "cos" -> unary(args, value -> (float) Math.cos(value * DEG_TO_RAD));
            case "exp" -> unary(args, value -> (float) Math.exp(value));
            case "floor" -> unary(args, value -> (float) Math.floor(value));
            case "hermite_blend" -> unary(args, value -> 3 * value * value - 2 * value * value * value);
            case "lerp" -> ternary(args, (start, end, delta) -> start + (end - start) * clamp01(delta));
            case "lerprotate" -> ternary(args, MolangFunctions::lerpRotate);
            case "ln" -> unary(args, value -> value <= 0 ? 0f : (float) Math.log(value));
            case "max" -> binary(args, Math::max);
            case "min" -> binary(args, Math::min);
            case "mod" -> binary(args, (value, divisor) -> divisor == 0 ? 0f : value % divisor);
            case "pow" -> binary(args, (base, exponent) -> (float) Math.pow(base, exponent));
            case "round" -> unary(args, value -> (float) Math.round(value));
            case "sin" -> unary(args, value -> (float) Math.sin(value * DEG_TO_RAD));
            case "sqrt" -> unary(args, value -> value <= 0 ? 0f : (float) Math.sqrt(value));
            case "trunc" -> unary(args, value -> (float) (long) value);
            case "random" -> random(args, false);
            case "random_integer", "die_roll_integer" -> random(args, true);
            default -> MolangValue.ZERO;
        };
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float lerpRotate(float start, float end, float delta) {
        float difference = (end - start) % 360f;
        if (difference > 180f) {
            difference -= 360f;
        } else if (difference < -180f) {
            difference += 360f;
        }
        return start + difference * clamp01(delta);
    }

    private static MolangValue random(List<MolangValue> args, boolean integer) {
        MolangValue low = argument(args, 0, 0f);
        MolangValue high = argument(args, 1, 1f);
        return context -> {
            float min = low.get(context);
            float max = high.get(context);
            if (max < min) {
                float swap = min;
                min = max;
                max = swap;
            }
            if (integer) {
                int range = (int) (max - min) + 1;
                return min + (range <= 0 ? 0 : context.random().nextInt(range));
            }
            return min + context.random().nextFloat() * (max - min);
        };
    }

    private static MolangValue argument(List<MolangValue> args, int index, float fallback) {
        return index < args.size() ? args.get(index) : MolangValue.constant(fallback);
    }

    private static MolangValue unary(List<MolangValue> args, Unary function) {
        MolangValue value = argument(args, 0, 0f);
        return context -> function.apply(value.get(context));
    }

    private static MolangValue binary(List<MolangValue> args, Binary function) {
        MolangValue first = argument(args, 0, 0f);
        MolangValue second = argument(args, 1, 0f);
        return context -> function.apply(first.get(context), second.get(context));
    }

    private static MolangValue ternary(List<MolangValue> args, Ternary function) {
        MolangValue first = argument(args, 0, 0f);
        MolangValue second = argument(args, 1, 0f);
        MolangValue third = argument(args, 2, 0f);
        return context -> function.apply(first.get(context), second.get(context), third.get(context));
    }

    @FunctionalInterface
    private interface Unary {
        float apply(float value);
    }

    @FunctionalInterface
    private interface Binary {
        float apply(float first, float second);
    }

    @FunctionalInterface
    private interface Ternary {
        float apply(float first, float second, float third);
    }
}
