package dev.serko.safariutils.client;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Int or long bit-mask picker whose entries may be grouped for display. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SettingMultiChoice {
	String[] values() default {};
	String[] groups() default {};
	int[] groupStarts() default {};
	/** Populate choices from the canonical 37-critter biome/rarity order. */
	boolean critters() default false;
	/** Present the four configured biome groups as headed columns when space allows. */
	boolean biomeColumns() default false;
}
