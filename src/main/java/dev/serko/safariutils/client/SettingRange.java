package dev.serko.safariutils.client;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Defines the bounds and step size of a numeric slider. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SettingRange {
	float minValue();
	float maxValue();
	float minStep();
}
