package com.b1n_ry.yigd.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Development-only test metadata translated into native 26.1.2 test instances. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GameTest {
    String templateNamespace() default YigdGameTestsMod.MOD_ID;
    String template() default "empty";
    String environment() default "regression";
    int timeoutTicks() default 100;
    int setupTicks() default 0;
    boolean required() default true;
}
