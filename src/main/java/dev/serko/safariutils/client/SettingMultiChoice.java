package dev.serko.safariutils.client;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Integer bit-mask picker whose entries may be grouped for display. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SettingMultiChoice {
	String[] values();
	String[] groups() default {};
	int[] groupStarts() default {};
}
