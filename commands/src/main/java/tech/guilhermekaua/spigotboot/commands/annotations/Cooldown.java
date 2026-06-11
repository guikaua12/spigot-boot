package tech.guilhermekaua.spigotboot.commands.annotations;

import tech.guilhermekaua.spigotboot.commands.cooldown.CommandCooldownInterceptor;
import tech.guilhermekaua.spigotboot.commands.cooldown.CommandCooldownPolicy;
import tech.guilhermekaua.spigotboot.commands.cooldown.FixedCommandCooldownPolicy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@CommandInterceptedBy(CommandCooldownInterceptor.class)
public @interface Cooldown {
    String time() default "";

    Class<? extends CommandCooldownPolicy> policy() default FixedCommandCooldownPolicy.class;
}
