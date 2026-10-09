package com.b1n_ry.yigd.gametest;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** This mod is present only in the separate gameTest source set. */
@Mod(YigdGameTestsMod.MOD_ID)
public final class YigdGameTestsMod {
    public static final String MOD_ID = "yigd_game_tests";
    private static final String PACKAGE = "com.b1n_ry.yigd.gametest.";

    public YigdGameTestsMod(IEventBus eventBus) {
        var environments = DeferredRegister.<MapCodec<? extends TestEnvironmentDefinition<?>>>create(
                Registries.TEST_ENVIRONMENT_DEFINITION_TYPE, MOD_ID);
        environments.register("isolated", () -> IsolatedTestEnvironment.CODEC);
        environments.register(eventBus);

        List<Class<?>> suites = new ArrayList<>();
        suites.add(suite("InventoryRegressionTests"));
        suites.add(suite("GraveGameplayGameTests"));
        suites.add(suite("DeathPipelineGameTests"));
        suites.add(suite("NetworkingGameTests"));
        // Load optional-API classes only when the actual optional mod is installed.
        if (ModList.get().isLoaded("curios")) suites.add(suite("CuriosInventoryRegressionTests"));
        if (ModList.get().isLoaded("travelersbackpack")) suites.add(suite("TravelersInventoryRegressionTests"));
        List<TestMethod> tests = suites.stream().flatMap(clazz -> Arrays.stream(clazz.getDeclaredMethods()))
                .filter(method -> method.isAnnotationPresent(GameTest.class))
                .map(TestMethod::new).sorted(Comparator.comparing(test -> test.id().toString())).toList();
        if (tests.isEmpty()) throw new IllegalStateException("No YiGD native GameTests were registered");
        if (tests.stream().map(TestMethod::id).distinct().count() != tests.size()) {
            throw new IllegalStateException("Duplicate YiGD native GameTest identifiers");
        }

        var functions = DeferredRegister.<Consumer<GameTestHelper>>create(Registries.TEST_FUNCTION, MOD_ID);
        for (TestMethod test : tests) functions.register(test.id().getPath(), () -> test::run);
        functions.register(eventBus);
        eventBus.addListener((RegisterGameTestsEvent event) -> {
            for (TestMethod test : tests) {
                GameTest metadata = test.method().getAnnotation(GameTest.class);
                // A distinct native environment holder creates a separate sequential batch for each test.
                // Global configuration and end-of-tick callbacks therefore cannot leak into a sibling test.
                var environment = event.registerEnvironment(
                        Identifier.fromNamespaceAndPath(MOD_ID, test.id().getPath() + ".environment"),
                        new IsolatedTestEnvironment(test.method().getDeclaringClass().getName(), metadata.environment()));
                event.registerTest(test.id(), new FunctionGameTestInstance(
                        ResourceKey.create(Registries.TEST_FUNCTION, test.id()),
                        new TestData<>(environment,
                                Identifier.fromNamespaceAndPath(metadata.templateNamespace(), metadata.template()),
                                metadata.timeoutTicks(), metadata.setupTicks(), metadata.required())));
            }
        });

        if (Boolean.getBoolean("yigd.smokeServer")) YigdSmokeServer.install();
        if (Boolean.getBoolean("yigd.smokeClient")) installClientSmoke();
    }

    static Class<?> suite(String name) {
        String className = name.contains(".") ? name : PACKAGE + name;
        if (!className.startsWith(PACKAGE)) throw new IllegalArgumentException("Unexpected test suite " + className);
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Required YiGD test suite is missing: " + className, exception);
        }
    }

    private static void installClientSmoke() {
        try {
            Class.forName(PACKAGE + "YigdClientSmoke").getMethod("install").invoke(null);
        } catch (ReflectiveOperationException exception) {
            throw propagate(exception);
        }
    }

    static RuntimeException propagate(Throwable exception) {
        Throwable cause = exception instanceof InvocationTargetException invocation ? invocation.getCause() : exception;
        if (cause instanceof Error error) throw error;
        if (cause instanceof RuntimeException runtime) return runtime;
        return new IllegalStateException("YiGD runtime test invocation failed", cause);
    }

    private record TestMethod(Method method) {
        TestMethod {
            if (!Modifier.isPublic(method.getModifiers()) || !Modifier.isStatic(method.getModifiers())
                    || method.getReturnType() != void.class
                    || !Arrays.equals(method.getParameterTypes(), new Class<?>[]{GameTestHelper.class})) {
                throw new IllegalArgumentException("GameTest must be public static void(GameTestHelper): " + method);
            }
        }

        Identifier id() {
            return Identifier.fromNamespaceAndPath(MOD_ID,
                    (method.getDeclaringClass().getSimpleName() + "." + method.getName()).toLowerCase(Locale.ROOT));
        }

        void run(GameTestHelper helper) {
            if (!(helper.getLevel().getServer() instanceof GameTestServer)) {
                throw new IllegalStateException("YiGD regression fixtures may run only on the isolated native GameTest server");
            }
            try {
                method.invoke(null, helper);
            } catch (InvocationTargetException exception) {
                if (exception.getCause() instanceof AssertionError assertion) {
                    // The native runner catches GameTestException, whereas a raw AssertionError would terminate its server.
                    helper.fail(net.minecraft.network.chat.Component.literal("Fixture assertion: " + assertion.getMessage()));
                }
                throw propagate(exception);
            } catch (ReflectiveOperationException exception) {
                throw propagate(exception);
            }
        }
    }
}
