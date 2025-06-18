# Spigot Boot

Spigot Boot is a powerful library inspired by Spring Boot, designed specifically for Spigot plugins. It provides:

- Robust Dependency Injection for managing your plugin's components
- An IOC container that manages dependencies and method intercepts (beans)
- A data module featuring OrmLite with automatic Transaction Management
- A Placeholder module that integrates with [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) to
  automatically handles placeholders
- An improved API for Plugin Messaging Channel, making BungeeCord and Spigot communication easier

With Spigot Boot, you can build modular, maintainable, and scalable Spigot plugins with ease.

## Getting Started

To get started, you need to add the dependency to your project.
If you are using **Maven**, add the following dependency to your `pom.xml`:

```xml

<dependency>
    <groupId>tech.guilhermekaua.spigot-boot</groupId>
    <artifactId>spigot-boot-core</artifactId>
    <version>2.0.0</version>
    <scope>provided</scope>
</dependency>
```

If you are using **Gradle**, add the following dependency to your `build.gradle`:

```groovy
dependencies {
    compileOnly 'tech.guilhermekaua.spigot-boot:spigot-boot-core:2.0.0'
}
```

## Dependency Injection

Spigot Boot provides a powerful Dependency Injection system that allows you to manage your plugin's components easily.
You can define beans and inject them into your classes using annotations.

```java
import tech.guilhermekaua.spigot.boot.core.annotations.Bean;
import tech.guilhermekaua.spigot.boot.core.annotations.Inject;
import tech.guilhermekaua.spigot.boot.core.annotations.OnEnable;
import tech.guilhermekaua.spigot.boot.core.annotations.OnDisable;
import tech.guilhermekaua.spigot.boot.core.annotations.Plugin;
import tech.guilhermekaua.spigot.boot.core.SpigotBootPlugin;

@Plugin(name = "MyPlugin", version = "1.0.0")

##
BungeeCord Plugin
messaging channel

ApxPlugin has
a convenient
API to
send messages
between BungeeCord
and Spigot
servers:

        ```java

public class Main extends ApxPlugin {
    @Override
    protected void onPluginEnable() {
        BungeeChannel bungeeChannel = new BungeeChannel(this);

        // Send a message to BungeeCord to get the server name of a player by its name
        bungeeChannel.sendMessage(null, new GetPlayerServerAction(player.getName())).thenAccept(serverName -> {
            player.sendMessage("You are on server: " + serverName);
        }).exceptionally(throwable -> {
            player.sendMessage("An error occurred while trying to fetch your server.");
            throwable.printStackTrace();
            return null;
        });

        // you saw? no need to rack your brains filling your code with boilerplate
        // there are two types of Actions, normal ones, and responsible ones which return some value 
        // (such in the example above, we are using `GetPlayerServerAction` which extends `ResponseableMessageAction`)
        // you can find all message actions here: https://github.com/guikaua12/ApxPlugin/tree/master/core/src/main/java/me/approximations/apxPlugin/messaging/bungee/actions
    }
}
```

## Credits:

- NBTEditor: https://github.com/BananaPuncher714/NBTEditor

TODO:

- [ ] Add support for custom subchannels and custom messages responses.
