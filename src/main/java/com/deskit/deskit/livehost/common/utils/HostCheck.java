package com.deskit.deskit.livehost.common.utils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD) // 메소드 위에 붙일 수 있음
@Retention(RetentionPolicy.RUNTIME) // 실행 중에도 동작함
public @interface HostCheck {
}
