package com.b1n_ry.yigd.gametest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Method;

/** Replaces removed BeforeBatch/AfterBatch hooks with native test-environment setup and teardown. */
public record IsolatedTestEnvironment(String suite, String environment)
        implements TestEnvironmentDefinition<Object> {
    public static final MapCodec<IsolatedTestEnvironment> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("suite").forGetter(IsolatedTestEnvironment::suite),
            Codec.STRING.fieldOf("environment").forGetter(IsolatedTestEnvironment::environment)
    ).apply(instance, IsolatedTestEnvironment::new));

    @Override
    public Object setup(ServerLevel level) {
        if (!(level.getServer() instanceof GameTestServer)) {
            throw new IllegalStateException("YiGD test environments cannot change a regular server or player world");
        }
        Class<?> clazz = YigdGameTestsMod.suite(suite);
        try {
            Method setup = optionalHook(clazz, "setup", ServerLevel.class, String.class);
            if (setup != null) return setup.invoke(null, level, environment);
            Method beforeBatch = optionalHook(clazz, "beforeBatch", ServerLevel.class);
            if (beforeBatch != null) beforeBatch.invoke(null, level);
            return null;
        } catch (ReflectiveOperationException exception) {
            NativeTestPlayer.closeAll(level.getServer());
            throw YigdGameTestsMod.propagate(exception);
        }
    }

    @Override
    public void teardown(ServerLevel level, Object savedData) {
        try {
            Class<?> clazz = YigdGameTestsMod.suite(suite);
            Method teardown = optionalHook(clazz, "teardown", ServerLevel.class, Object.class);
            if (teardown != null) teardown.invoke(null, level, savedData);
            else {
                Method afterBatch = optionalHook(clazz, "afterBatch", ServerLevel.class);
                if (afterBatch != null) afterBatch.invoke(null, level);
            }
        } catch (ReflectiveOperationException exception) {
            throw YigdGameTestsMod.propagate(exception);
        } finally {
            // Test failures and native timeout exits also run this cleanup.
            NativeTestPlayer.closeAll(level.getServer());
        }
    }

    @Override
    public MapCodec<IsolatedTestEnvironment> codec() {
        return CODEC;
    }

    private static Method optionalHook(Class<?> suite, String name, Class<?>... arguments) {
        try {
            return suite.getMethod(name, arguments);
        } catch (NoSuchMethodException absent) {
            return null;
        }
    }
}
